package com.modernshopflow.catalog.dashboard;

import java.time.OffsetDateTime;
import java.util.List;
import com.modernshopflow.catalog.dashboard.DashboardDtos.RevenueDay;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL aggregates only paid revenue and currently available variant stock. */
@Repository
public class DashboardRepository {
    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Metrics summarize(OffsetDateTime from, OffsetDateTime to) {
        Totals totals = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN status = 'PAID' THEN total_amount ELSE 0 END), 0) AS revenue,
                       COUNT(*) AS order_count, COUNT(CASE WHEN status = 'PAID' THEN 1 END) AS paid_order_count
                FROM orders WHERE created_at >= ? AND created_at < ?
                """, (rs, row) -> new Totals(rs.getBigDecimal("revenue").longValueExact(),
                        rs.getLong("order_count"), rs.getLong("paid_order_count")), from, to);
        List<RevenueDay> dailyRevenue = jdbc.query("""
                SELECT CAST(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS DATE) AS day,
                       COALESCE(SUM(total_amount), 0) AS revenue
                FROM orders WHERE status = 'PAID' AND created_at >= ? AND created_at < ?
                GROUP BY day ORDER BY day
                """, (rs, row) -> new RevenueDay(rs.getString("day"), rs.getBigDecimal("revenue").longValueExact()), from, to);
        Long lowStockCount = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT p.id)
                FROM products p
                JOIN product_variants pv ON pv.product_id = p.id AND pv.is_deleted = FALSE
                JOIN inventory_items ii ON ii.variant_id = pv.id
                WHERE p.is_deleted = FALSE
                  AND GREATEST(ii.quantity - ii.reserved_quantity - COALESCE((
                      SELECT SUM(sh.quantity) FROM stock_holds sh
                      WHERE sh.inventory_item_id = ii.id AND sh.status = 'HOLDING'
                        AND sh.expires_at > CURRENT_TIMESTAMP
                  ), 0), 0) <= p.low_stock_threshold
                """,
                Long.class);
        return new Metrics(totals.revenue(), totals.orderCount(),
                lowStockCount == null ? 0 : lowStockCount, totals.paidOrderCount(), dailyRevenue);
    }

    private record Totals(long revenue, long orderCount, long paidOrderCount) {}
    public record Metrics(long revenue, long orderCount, long lowStockCount, long paidOrderCount, List<RevenueDay> dailyRevenue) {}
}
