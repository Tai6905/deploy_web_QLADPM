package com.modernshopflow.catalog.delivery;

import java.time.OffsetDateTime;
import java.util.List;

/** Read models for paid orders moving through warehouse delivery states. */
public final class DeliveryDtos {
    private DeliveryDtos() {}

    public record DeliveryItem(long productId, String productName, long variantId, int quantity) {}

    public record DeliveryHistory(
            String fromStatus, String toStatus, OffsetDateTime changedAt, Long changedBy) {}

    public record DeliveryOrder(
            long orderId,
            String orderStatus,
            String deliveryStatus,
            String receiverName,
            String city,
            long totalAmount,
            OffsetDateTime createdAt,
            List<DeliveryItem> items,
            List<DeliveryHistory> history) {}

    public record UpdateDeliveryRequest(String toStatus) {}
}