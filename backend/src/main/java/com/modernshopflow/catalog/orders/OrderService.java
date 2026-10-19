package com.modernshopflow.catalog.orders;

import com.modernshopflow.catalog.domain.PageResult;
import com.modernshopflow.catalog.orders.OrderDtos.CreateOrderRequest;
import com.modernshopflow.catalog.orders.OrderDtos.OrderResponse;
import com.modernshopflow.catalog.orders.OrderDtos.OrderSummary;
import com.modernshopflow.catalog.orders.OrderRepository.VariantAvailability;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates atomic checkout, stock holds, payment initialization, and owner order reads. */
@Service
public class OrderService {
    private static final long MAX_ORDER_TOTAL = 9_999_999_999L;
    private static final int HOLD_MINUTES = 15;

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /** Creates all order rows and stock holds together or rolls the complete operation back. */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        return create(request, null);
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Long customerId) {
        if (!"CARD".equals(request.paymentMethod())) {
            throw new IllegalArgumentException("Only CARD payment is supported");
        }
        Set<Long> productIds = new HashSet<>();
        List<Allocation> allocations = new ArrayList<>();
        long total = 0;

        for (var line : request.items()) {
            if (!productIds.add(line.productId())) {
                throw new IllegalArgumentException("Duplicate productId in items");
            }
            if (!repository.productIsSellable(line.productId())) {
                throw new IllegalArgumentException("Product is not available: " + line.productId());
            }

            int remaining = line.quantity();
            List<VariantAvailability> variants = repository.lockAvailableVariants(line.productId());
            for (VariantAvailability variant : variants) {
                if (remaining == 0) break;
                int allocated = Math.min(remaining, variant.availableQuantity());
                if (allocated <= 0) continue;
                allocations.add(new Allocation(line.productId(), variant, allocated));
                remaining -= allocated;
                total = Math.addExact(total, Math.multiplyExact(variant.unitPrice(), allocated));
            }
            if (remaining > 0) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product " + line.productId() + "; unavailable quantity: " + remaining);
            }
        }

        if (total > MAX_ORDER_TOTAL) {
            throw new IllegalArgumentException("Order total exceeds supported maximum");
        }

        UUID orderRef = UUID.randomUUID();
        long orderId = repository.insertOrder(orderRef, request, total, customerId);
        OffsetDateTime holdExpiresAt = OffsetDateTime.now().plusMinutes(HOLD_MINUTES);
        for (Allocation allocation : allocations) {
            VariantAvailability variant = allocation.variant();
            repository.insertOrderItem(orderId, allocation.productId(), variant.variantId(),
                    variant.productName(), variant.unitPrice(), allocation.quantity());
            repository.addStockHold(variant.inventoryItemId(), orderId, allocation.quantity(), holdExpiresAt);
            repository.addMovement(variant.variantId(), "ORDER_RESERVED", allocation.quantity(), orderId,
                    "Checkout stock hold");
        }
        repository.insertPaymentAttempt(orderId, total);
        return repository.findOrder(orderId);
    }

    /** Returns recent orders for the owner workspace using zero-based API pagination. */
    public PageResult<OrderSummary> list(int page, int size) {
        return list(page, size, null);
    }

    public PageResult<OrderSummary> list(int page, int size, String search) {
        int offset = pageOffset(page, size);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return new PageResult<>(repository.listOrders(size, offset, normalizedSearch), repository.countOrders(normalizedSearch));
    }

    public OrderResponse getByReference(UUID orderRef) {
        return repository.findOrderByRef(orderRef);
    }

    public PageResult<OrderSummary> listMine(long customerId, int page, int size, String search) {
        int offset = pageOffset(page, size);
        String query = search == null || search.isBlank() ? null : search.trim();
        return new PageResult<>(repository.listOrders(size, offset, query, customerId), repository.countOrders(query, customerId));
    }
    public OrderResponse getMine(long id, long customerId) { return repository.findOwnedOrder(id, customerId); }
    public OrderResponse getMineByReference(UUID reference, long customerId) { return repository.findOwnedOrderByRef(reference, customerId); }

    private int pageOffset(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        try {
            return Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page is too large for the requested size", exception);
        }
    }

    private record Allocation(long productId, VariantAvailability variant, int quantity) {}
}
