package com.modernshopflow.catalog.receiving;

import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Persists receipts and their immutable stock-movement audit records. */
@Repository
public class ReceivingRepository {
    private final JdbcTemplate jdbc;

    public ReceivingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long variantId, int quantity, String supplierName, String note, BigDecimal unitCost) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO receiving_records(variant_id, quantity, supplier_name, note, unit_cost) VALUES (?, ?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setLong(1, variantId);
            statement.setInt(2, quantity);
            statement.setString(3, supplierName);
            statement.setString(4, note);
            statement.setBigDecimal(5, unitCost);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a receiving id");
        return key.longValue();
    }

    public void updateLatestCost(long variantId, BigDecimal unitCost) {
        jdbc.update("UPDATE products SET cost_price = ?, updated_at = CURRENT_TIMESTAMP WHERE id = (SELECT product_id FROM product_variants WHERE id = ?)", unitCost, variantId);
    }

    public void insertMovement(long variantId, int quantity, long receivingId, String note) {
        jdbc.update(
                "INSERT INTO stock_movements(variant_id, movement_type, quantity, reference_type, reference_id, note) "
                        + "VALUES (?, 'STOCK_RECEIVED', ?, 'RECEIVING', ?, ?)",
                variantId, quantity, receivingId, note);
    }

    public List<ReceivingResponse> list(int limit) {
        return jdbc.query(RECEIPT_SQL + " ORDER BY rr.created_at DESC, rr.id DESC LIMIT ?",
                ReceivingRepository::mapResponse, limit);
    }

    public ReceivingResponse get(long id) {
        return jdbc.queryForObject(RECEIPT_SQL + " WHERE rr.id = ?", ReceivingRepository::mapResponse, id);
    }

    private static final String RECEIPT_SQL = """
                SELECT rr.id, p.id AS product_id, pv.id AS variant_id, p.name AS product_name,
                       rr.quantity, rr.supplier_name, rr.note, rr.created_at, rr.unit_cost,
                       ii.quantity AS on_hand, ii.reserved_quantity AS reserved,
                       COALESCE((SELECT SUM(sh.quantity) FROM stock_holds sh
                           WHERE sh.inventory_item_id = ii.id AND sh.status = 'HOLDING'
                             AND sh.expires_at > CURRENT_TIMESTAMP), 0) AS holding
                FROM receiving_records rr
                JOIN product_variants pv ON pv.id = rr.variant_id
                JOIN products p ON p.id = pv.product_id
                JOIN inventory_items ii ON ii.variant_id = pv.id
                """;

    private static ReceivingResponse mapResponse(ResultSet rs, int row) throws SQLException {
        int onHand = rs.getInt("on_hand");
        int reserved = rs.getInt("reserved");
        return new ReceivingResponse(
                rs.getLong("id"), rs.getLong("product_id"), rs.getLong("variant_id"),
                rs.getString("product_name"), rs.getInt("quantity"), rs.getString("supplier_name"),
                rs.getString("note"), rs.getObject("created_at", OffsetDateTime.class),
                onHand, reserved, Math.max(onHand - reserved - rs.getInt("holding"), 0),
                rs.getBigDecimal("unit_cost") == null ? null : rs.getBigDecimal("unit_cost").longValueExact(),
                rs.getBigDecimal("unit_cost") == null ? null : rs.getBigDecimal("unit_cost").multiply(BigDecimal.valueOf(rs.getInt("quantity"))).longValueExact());
    }
}
