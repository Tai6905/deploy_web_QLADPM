package com.modernshopflow.catalog.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String sku,
        String name,
        String slug,
        String description,
        long costPrice,
        long listPrice,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ProductVariantResponse> productVariants,
        List<ProductImageResponse> productImages) {

    public record ProductVariantResponse(
            Long id,
            String color,
            String size,
            int quantity,
            int reservedQuantity,
            int holdingQuantity,
            int availableQuantity) {}

    public record ProductImageResponse(
            Long id, String imageUrl, int displayOrder, boolean primary) {}
}
