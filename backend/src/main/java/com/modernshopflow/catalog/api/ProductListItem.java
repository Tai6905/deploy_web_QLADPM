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
        long reviewCount,
        java.util.Map<String, String> specifications) {
    public ProductListItem(Long id, Long categoryId, String categoryName, String sku, String name, String slug,
            String description, long listPrice, long availableQuantity, String imageUrl, double averageRating, long reviewCount) {
        this(id, categoryId, categoryName, sku, name, slug, description, listPrice, availableQuantity, imageUrl, averageRating, reviewCount, java.util.Map.of());
    }
    public ProductListItem withSpecifications(java.util.Map<String, String> specs) {
        return new ProductListItem(id, categoryId, categoryName, sku, name, slug, description, listPrice, availableQuantity, imageUrl, averageRating, reviewCount, specs);
    }
    public ProductListItem(Long id, Long categoryId, String categoryName, String sku, String name, String slug,
            String description, long listPrice, long availableQuantity, String imageUrl) {
        this(id, categoryId, categoryName, sku, name, slug, description, listPrice, availableQuantity, imageUrl, 0, 0);
    }
}
