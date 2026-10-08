package com.modernshopflow.catalog.returns;

import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.modernshopflow.catalog.returns.ReturnDtos.CreateReturnRequest;
import com.modernshopflow.catalog.returns.ReturnDtos.ReturnResponse;
import com.modernshopflow.catalog.returns.ReturnDtos.ReturnableOrder;
import com.modernshopflow.catalog.returns.ReturnRepository.LockedOrder;
import com.modernshopflow.catalog.returns.ReturnRepository.LockedReturn;
import com.modernshopflow.catalog.returns.ReturnRepository.LockedVariantInventory;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns return eligibility, partial quantity accounting, and role-specific lifecycle transitions. */
@Service
public class ReturnService {
    private final ReturnRepository repository;

    public ReturnService(ReturnRepository repository) {
        this.repository = repository;
    }

    public List<ReturnResponse> list(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return repository.list(limit);
    }

    public List<ReturnableOrder> listReturnableOrders() {
        return repository.listReturnableOrders();
    }

    @Transactional
    public ReturnResponse create(CreateReturnRequest request) {
        LockedOrder order = lockOrder(request.orderId());
        if (!"PAID".equals(order.status()) || !"DELIVERED".equals(order.deliveryStatus())) {
            throw new CatalogConflictException("Only delivered paid orders can be returned");
        }

        var availableItems = repository.listReturnableItems(order.id());
        Set<Long> usedOrderItems = new HashSet<>();
        for (var requested : request.items()) {
            if (!usedOrderItems.add(requested.orderItemId())) {
                throw new IllegalArgumentException("Duplicate orderItemId in return items");
            }
            var purchased = availableItems.stream()
                    .filter(item -> item.orderItemId() == requested.orderItemId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Order item does not belong to this order"));
            if (requested.quantity() > purchased.returnableQuantity()) {
                throw new CatalogConflictException("Return quantity exceeds the remaining purchased quantity");
            }
        }

        long returnId = repository.insert(order.id(), normalize(request.reason()));
        request.items().forEach(item -> repository.insertItem(returnId, item.orderItemId(), item.quantity()));
        return repository.get(returnId);
    }

    @Transactional
    public ReturnResponse update(long returnId, String target, Boolean restockable, Set<String> roles) {
        LockedReturn current;
        try {
            current = repository.lockReturn(returnId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CatalogNotFoundException("Return request " + returnId + " was not found");
        }

        switch (target) {
            case "APPROVED" -> {
                requireRole(roles, "ROLE_SHOP_OWNER");
                if (!"REQUESTED".equals(current.status()) || restockable == null) {
                    throw new CatalogConflictException("REQUESTED returns need an owner decision and restockable value");
                }
                repository.updateStatus(returnId, "APPROVED", restockable);
            }
            case "REJECTED" -> {
                requireRole(roles, "ROLE_SHOP_OWNER");
                if (!"REQUESTED".equals(current.status()) || restockable != null) {
                    throw new CatalogConflictException("Only requested returns can be rejected");
                }
                repository.updateStatus(returnId, "REJECTED", null);
            }
            case "RESTOCKED" -> {
                requireAnyRole(roles, "ROLE_WAREHOUSE", "ROLE_SHOP_OWNER");
                if (!"APPROVED".equals(current.status()) || !Boolean.TRUE.equals(current.restockable())) {
                    throw new CatalogConflictException("Only approved restockable returns can be restocked");
                }
                var items = repository.listItems(returnId).stream()
                        .sorted(Comparator.comparingLong(ReturnDtos.ReturnItem::variantId)).toList();
                for (var item : items) {
                    LockedVariantInventory inventory;
                    try {
                        inventory = repository.lockInventory(item.variantId());
                    } catch (EmptyResultDataAccessException exception) {
                        throw new CatalogConflictException("Inventory is missing for returned variant " + item.variantId());
                    }
                    if ((long) inventory.quantity() + item.quantity() > Integer.MAX_VALUE) {
                        throw new CatalogConflictException("Restock exceeds supported inventory range");
                    }
                    repository.restock(inventory.inventoryId(), item.variantId(), item.quantity(), returnId);
                }
                repository.updateStatus(returnId, "RESTOCKED", true);
            }
            default -> throw new IllegalArgumentException("toStatus must be APPROVED, REJECTED, or RESTOCKED");
        }
        return repository.get(returnId);
    }

    private LockedOrder lockOrder(long orderId) {
        try {
            return repository.lockOrder(orderId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CatalogNotFoundException("Order " + orderId + " was not found");
        }
    }

    private void requireRole(Set<String> roles, String required) {
        if (!roles.contains(required)) throw new AccessDeniedException("Role is not allowed to perform this return transition");
    }

    private void requireAnyRole(Set<String> roles, String first, String second) {
        if (!roles.contains(first) && !roles.contains(second)) {
            throw new AccessDeniedException("Role is not allowed to restock returns");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}