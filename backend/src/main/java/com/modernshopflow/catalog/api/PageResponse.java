package com.modernshopflow.catalog.api;

import com.modernshopflow.catalog.domain.PageResult;
import java.util.List;

/** Standard paginated response envelope shared by the catalog list endpoints. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    /** Converts the repository page result while calculating a safe page count. */
    public static <T> PageResponse<T> fromPageResult(
            PageResult<T> pageResult, int page, int size) {
        return new PageResponse<>(
                pageResult.items(),
                page,
                size,
                pageResult.totalItems(),
                totalPages(pageResult.totalItems(), size));
    }

    private static int totalPages(long totalItems, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
        long pages = totalItems / size + (totalItems % size == 0 ? 0 : 1);
        return Math.toIntExact(pages);
    }
}
