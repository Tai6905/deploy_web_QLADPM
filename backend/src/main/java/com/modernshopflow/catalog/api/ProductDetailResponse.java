package com.modernshopflow.catalog.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
        @JsonIgnore long costPrice,
        long listPrice,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ProductVariantResponse> productVariants,
        List<ProductImageResponse> productImages,
        String usageInstructions,
        double averageRating,
        long reviewCount,
        java.util.Map<String, String> specifications) {

    public ProductDetailResponse(Long id, Long categoryId, String categoryName, String sku, String name, String slug,
            String description, long costPrice, long listPrice, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<ProductVariantResponse> variants, List<ProductImageResponse> images, String usageInstructions, double averageRating, long reviewCount) {
        this(id, categoryId, categoryName, sku, name, slug, description, costPrice, listPrice, createdAt, updatedAt, variants, images, usageInstructions, averageRating, reviewCount, java.util.Map.of());
    }
    public ProductDetailResponse withSpecifications(java.util.Map<String, String> specs) {
        return new ProductDetailResponse(id, categoryId, categoryName, sku, name, slug, description, costPrice, listPrice, createdAt, updatedAt, productVariants, productImages, usageInstructions, averageRating, reviewCount, specs);
    }

    public ProductDetailResponse(Long id, Long categoryId, String categoryName, String sku, String name, String slug,
            String description, long costPrice, long listPrice, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<ProductVariantResponse> variants, List<ProductImageResponse> images) {
        this(id, categoryId, categoryName, sku, name, slug, description, costPrice, listPrice, createdAt, updatedAt, variants, images, null, 0, 0);
    }

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
