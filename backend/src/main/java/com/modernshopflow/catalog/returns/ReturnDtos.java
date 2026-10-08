package com.modernshopflow.catalog.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Request and response records for partial customer returns. */
public final class ReturnDtos {
    private ReturnDtos() {}

    public record ReturnItemRequest(@NotNull @Positive Long orderItemId, @NotNull @Positive Integer quantity) {}
    public record CreateReturnRequest(
            @NotNull @Positive Long orderId,
            @Size(max = 500) String reason,
            @NotEmpty List<@Valid ReturnItemRequest> items) {}
    public record UpdateReturnRequest(String toStatus, Boolean restockable) {}
    public record ReturnItem(long orderItemId, long productId, long variantId, String productName, int quantity) {}
    public record ReturnResponse(
            long id, long orderId, String status, String reason, Boolean restockable,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, List<ReturnItem> items) {}
    public record ReturnableItem(
            long orderItemId, long productId, long variantId, String productName,
            int purchasedQuantity, int returnedQuantity, int returnableQuantity) {}
    public record ReturnableOrder(
            long orderId, String receiverName, String city, long totalAmount,
            OffsetDateTime createdAt, List<ReturnableItem> items) {}
}