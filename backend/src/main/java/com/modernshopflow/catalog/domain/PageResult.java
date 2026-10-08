package com.modernshopflow.catalog.domain;

import java.util.List;

public record PageResult<T>(List<T> items, long totalItems) {}
