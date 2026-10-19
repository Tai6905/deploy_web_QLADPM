package com.modernshopflow.catalog.dashboard;

import java.time.OffsetDateTime;
import java.util.List;

/** Aggregated owner metrics for a bounded dashboard time range. */
public final class DashboardDtos {
    private DashboardDtos() {}

    public record DashboardSummary(
            String range,
            long revenue,
            long orderCount,
            long lowStockCount,
            OffsetDateTime from,
            OffsetDateTime to,
            long paidOrderCount,
            List<RevenueDay> dailyRevenue) {}

    public record RevenueDay(String date, long revenue) {}
}
