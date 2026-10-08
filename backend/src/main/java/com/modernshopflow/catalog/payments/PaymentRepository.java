package com.modernshopflow.catalog.payments;

import com.modernshopflow.catalog.payments.PaymentDtos.PaymentResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** SQL boundary for locking payment orders and committing or releasing their stock holds. */
@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LockedOrder lockOrder(UUID orderRef) {
        return jdbc.queryForObject(
                "SELECT id, status, total_amount FROM orders WHERE order_ref = ? FOR UPDATE",
                (rs, row) -> new LockedOrder(rs.getLong("id"), rs.getString("status"), rs.getBigDecimal("total_amount").longValueExact()),
                orderRef);
    }

    public PaymentResponse lockPayment(long orderId) {
        return jdbc.queryForObject(
                """
                SELECT pa.id, pa.order_id, pa.method, pa.status, pa.amount, pa.paid_at,
                       pa.failure_reason, pa.created_at, o.status AS order_status
                FROM payment_attempts pa JOIN orders o ON o.id = pa.order_id
                WHERE pa.order_id = ? FOR UPDATE OF pa
                """,
                ROW_MAPPER,
                orderId);
    }

    public List<StockHold> lockActiveHolds(long orderId) {
        return jdbc.query(
                """
                SELECT sh.id, sh.inventory_item_id, sh.quantity, sh.expires_at,
                       ii.variant_id, ii.quantity AS on_hand_quantity, ii.reserved_quantity
                FROM stock_holds sh JOIN inventory_items ii ON ii.id = sh.inventory_item_id
                WHERE sh.order_id = ? AND sh.status = 'HOLDING'
                ORDER BY ii.variant_id, sh.id
                FOR UPDATE OF sh, ii
                """,
                (rs, row) -> new StockHold(
                        rs.getLong("id"), rs.getLong("inventory_item_id"), rs.getLong("variant_id"),
                        rs.getInt("quantity"), rs.getInt("on_hand_quantity"),
                        rs.getInt("reserved_quantity"), rs.getObject("expires_at", OffsetDateTime.class)),
                orderId);
    }

    public void commitHolds(List<StockHold> holds, long orderId) {
        for (StockHold hold : holds) {
            jdbc.update("UPDATE inventory_items SET reserved_quantity = reserved_quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    hold.quantity(), hold.inventoryItemId());
            jdbc.update("UPDATE stock_holds SET status = 'COMMITTED' WHERE id = ?", hold.id());
        }
    }

    public void releaseHolds(List<StockHold> holds, long orderId, String reason) {
        for (StockHold hold : holds) {
            jdbc.update("UPDATE stock_holds SET status = 'RELEASED' WHERE id = ?", hold.id());
            insertMovement(hold.variantId(), "PAYMENT_FAILED_RELEASE", -hold.quantity(), orderId, reason);
        }
    }

    private void insertMovement(long variantId, String type, int quantity, long orderId, String note) {
        jdbc.update("INSERT INTO stock_movements(variant_id, movement_type, quantity, reference_type, reference_id, note) VALUES (?, ?, ?, 'ORDER', ?, ?)",
                variantId, type, quantity, orderId, note);
    }

    public void updatePayment(long paymentId, String status, String failureReason) {
        jdbc.update("UPDATE payment_attempts SET status = ?, failure_reason = ?, paid_at = CASE WHEN ? = 'SUCCESS' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id = ?",
                status, failureReason, status, paymentId);
    }

    public void updateOrderStatus(long orderId, String status) {
        jdbc.update("UPDATE orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, orderId);
    }

    public PaymentResponse getPayment(long paymentId) {
        return jdbc.queryForObject(
                """
                SELECT pa.id, pa.order_id, pa.method, pa.status, pa.amount, pa.paid_at,
                       pa.failure_reason, pa.created_at, o.status AS order_status
                FROM payment_attempts pa JOIN orders o ON o.id = pa.order_id WHERE pa.id = ?
                """,
                ROW_MAPPER,
                paymentId);
    }

    private static final RowMapper<PaymentResponse> ROW_MAPPER = (rs, row) -> mapPayment(rs);

    private static PaymentResponse mapPayment(ResultSet rs) throws SQLException {
        BigDecimal amount = rs.getBigDecimal("amount");
        return new PaymentResponse(rs.getLong("id"), rs.getLong("order_id"), rs.getString("method"),
                rs.getString("status"), amount.longValueExact(), rs.getObject("paid_at", OffsetDateTime.class),
                rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("order_status"));
    }

    public record LockedOrder(long id, String status, long totalAmount) {}
    public record StockHold(long id, long inventoryItemId, long variantId, int quantity,
            int onHandQuantity, int reservedQuantity, OffsetDateTime expiresAt) {}
}