package com.modernshopflow.catalog.dashboard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        BigDecimal revenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status = 'PAID' AND created_at >= ? AND created_at < ?",
                BigDecimal.class, from, to);
        Long orderCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ?",
                Long.class, from, to);
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
        return new Metrics(revenue == null ? 0 : revenue.longValueExact(),
                orderCount == null ? 0 : orderCount,
                lowStockCount == null ? 0 : lowStockCount);
    }

    public record Metrics(long revenue, long orderCount, long lowStockCount) {}
}