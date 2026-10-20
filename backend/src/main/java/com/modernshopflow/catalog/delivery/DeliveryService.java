package com.modernshopflow.catalog.delivery;

import com.modernshopflow.catalog.delivery.DeliveryDtos.DeliveryOrder;
import com.modernshopflow.catalog.delivery.DeliveryRepository.LockedDelivery;
import com.modernshopflow.catalog.delivery.DeliveryRepository.LockedStock;
import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces the forward-only delivery lifecycle and decrements stock exactly once on delivery. */
@Service
public class DeliveryService {
    private final DeliveryRepository repository;

    public DeliveryService(DeliveryRepository repository) {
        this.repository = repository;
    }

    public List<DeliveryOrder> list() {
        return repository.listPaidOrderIds().stream().map(repository::get).toList();
    }

    @Transactional
    public DeliveryOrder advance(long orderId, String target) {
        LockedDelivery order;
        try {
            order = repository.lockOrder(orderId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CatalogNotFoundException("Order " + orderId + " was not found");
        }
        boolean cash="COD".equals(order.paymentMethod()) && "PENDING_PAYMENT".equals(order.orderStatus());
        if (!"PAID".equals(order.orderStatus()) && !cash) {
            throw new CatalogConflictException("Only paid orders can be delivered");
        }
        String expected = switch (order.deliveryStatus()) {
            case "NONE" -> "PREPARING";
            case "PREPARING" -> "SHIPPED";
            case "SHIPPED" -> "DELIVERED";
            default -> throw new CatalogConflictException("Delivery is already complete");
        };
        if (!expected.equals(target)) {
            throw new CatalogConflictException("Invalid delivery transition: " + order.deliveryStatus() + " -> " + target);
        }

        if ("DELIVERED".equals(target)) {
            List<DeliveryRepository.OrderVariant> variants = repository.lockOrderVariants(orderId);
            for (DeliveryRepository.OrderVariant item : variants) {
                LockedStock stock;
                try {
                    stock = repository.lockStock(item.variantId());
                } catch (EmptyResultDataAccessException exception) {
                    throw new CatalogConflictException("Inventory is missing for variant " + item.variantId());
                }
                if (stock.quantity() < item.quantity() || stock.reservedQuantity() < item.quantity()
                        || !repository.shipStock(stock.inventoryId(), item.variantId(), item.quantity(), orderId)) {
                    throw new CatalogConflictException("Inventory changed before delivery could be completed");
                }
            }
            if(cash) repository.collectCash(orderId);
        }

        repository.updateStatus(orderId, target);
        repository.addHistory(orderId, order.deliveryStatus(), target);
        return repository.get(orderId);
    }
}
