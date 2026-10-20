package com.modernshopflow.catalog.api;

public record ProductListItem(
        Long id,
        Long categoryId,
        String categoryName,
        String sku,
        String name,
        String slug,
        String description,
        long listPrice,
        long availableQuantity,
        String imageUrl,
        double averageRating,
        long reviewCount) {
    public ProductListItem(Long id, Long categoryId, String categoryName, String sku, String name, String slug,
            String description, long listPrice, long availableQuantity, String imageUrl) {
        this(id, categoryId, categoryName, sku, name, slug, description, listPrice, availableQuantity, imageUrl, 0, 0);
    }
}
