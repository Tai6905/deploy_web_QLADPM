package com.modernshopflow.catalog.dashboard;

import com.modernshopflow.catalog.dashboard.DashboardDtos.DashboardSummary;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.stream.LongStream;
import java.util.stream.Collectors;
import com.modernshopflow.catalog.dashboard.DashboardDtos.RevenueDay;
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
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        OffsetDateTime to = OffsetDateTime.now(zone);
        OffsetDateTime from = to.toLocalDate().minusDays(days - 1).atStartOfDay(zone).toOffsetDateTime();
        DashboardRepository.Metrics metrics = repository.summarize(from, to);
        var revenueByDate = metrics.dailyRevenue().stream().collect(Collectors.toMap(RevenueDay::date, RevenueDay::revenue));
        var dailyRevenue = LongStream.range(0, days)
                .mapToObj(day -> from.toLocalDate().plusDays(day).toString())
                .map(date -> new RevenueDay(date, revenueByDate.getOrDefault(date, 0L))).toList();
        return new DashboardSummary(range, metrics.revenue(), metrics.orderCount(), metrics.lowStockCount(), from, to,
                metrics.paidOrderCount(), dailyRevenue);
    }
}
