package com.modernshopflow.catalog.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Variant-level stock projection and manual adjustment request records. */
public final class InventoryDtos {
    private InventoryDtos() {}

    public record InventoryItem(
            long productId,
            long variantId,
            String productName,
            String color,
            String size,
            int onHandStock,
            int reservedStock,
            int holdingStock,
            int availableStock,
            Integer lowStockThreshold,
            boolean lowStock) {}

    public record AdjustmentRequest(
            @NotNull Integer delta,
            @NotBlank @Size(max = 500) String reason) {}

    public record ThresholdRequest(@NotNull Integer lowStockThreshold) {}
}