package com.modernshopflow.catalog.api;

import java.time.OffsetDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
