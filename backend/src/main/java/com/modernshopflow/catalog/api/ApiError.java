package com.modernshopflow.catalog.api;

import java.time.OffsetDateTime;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId) {}
