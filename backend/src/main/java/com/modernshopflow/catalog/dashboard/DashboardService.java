package com.modernshopflow.catalog.dashboard;

import com.modernshopflow.catalog.dashboard.DashboardDtos.DashboardSummary;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Validates dashboard ranges and maps aggregate database values to the public response. */
@Service
public class DashboardService {
    private static final Map<String, Long> RANGE_DAYS = Map.of("7d", 7L, "30d", 30L, "90d", 90L);
    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public DashboardSummary summary(String range) {
        Long days = RANGE_DAYS.get(range);
        if (days == null) throw new IllegalArgumentException("range must be 7d, 30d, or 90d");
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.minusDays(days);
        DashboardRepository.Metrics metrics = repository.summarize(from, to);
        return new DashboardSummary(range, metrics.revenue(), metrics.orderCount(), metrics.lowStockCount(), from, to);
    }
}