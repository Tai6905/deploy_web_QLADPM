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
        String imageUrl) {}
