package com.modernshopflow.catalog.delivery;

import com.modernshopflow.catalog.delivery.DeliveryDtos.DeliveryHistory;
import com.modernshopflow.catalog.delivery.DeliveryDtos.DeliveryItem;
import com.modernshopflow.catalog.delivery.DeliveryDtos.DeliveryOrder;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL boundary for paid-order delivery reads and atomic inventory completion. */
@Repository
public class DeliveryRepository {
    private final JdbcTemplate jdbc;

    public DeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> listPaidOrderIds() {
        return jdbc.queryForList("SELECT id FROM orders WHERE status = 'PAID' OR (status='PENDING_PAYMENT' AND payment_method='COD') ORDER BY created_at, id", Long.class);
    }

    public LockedDelivery lockOrder(long orderId) {
        return jdbc.queryForObject(
                "SELECT id, status, delivery_status,payment_method FROM orders WHERE id = ? FOR UPDATE",
                (rs, row) -> new LockedDelivery(rs.getLong("id"), rs.getString("status"), rs.getString("delivery_status"),rs.getString("payment_method")),
                orderId);
    }

    public DeliveryOrder get(long orderId) {
        return jdbc.queryForObject(
                "SELECT id, status, delivery_status, receiver_name, city, total_amount, created_at,payment_method FROM orders WHERE id = ?",
                (rs, row) -> mapOrder(rs),
                orderId);
    }

    public void collectCash(long orderId) {
        if(jdbc.update("UPDATE payment_attempts SET status='SUCCESS',paid_at=CURRENT_TIMESTAMP WHERE order_id=? AND method='COD' AND status='PENDING'",orderId)!=1)
            throw new com.modernshopflow.catalog.domain.CatalogConflictException("Trạng thái thu tiền COD không hợp lệ.");
        jdbc.update("UPDATE orders SET status='PAID',updated_at=CURRENT_TIMESTAMP WHERE id=?",orderId);
    }

    public void updateStatus(long orderId, String status) {
        jdbc.update("UPDATE orders SET delivery_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, orderId);
    }

    public void addHistory(long orderId, String fromStatus, String toStatus) {
        jdbc.update("INSERT INTO delivery_status_history(order_id, from_status, to_status) VALUES (?, ?, ?)",
                orderId, fromStatus, toStatus);
    }

    public List<OrderVariant> lockOrderVariants(long orderId) {
        return jdbc.query(
                "SELECT variant_id, SUM(quantity)::INT AS quantity FROM order_items WHERE order_id = ? GROUP BY variant_id ORDER BY variant_id",
                (rs, row) -> new OrderVariant(rs.getLong("variant_id"), rs.getInt("quantity")),
                orderId);
    }

    public LockedStock lockStock(long variantId) {
        return jdbc.queryForObject(
                "SELECT id, quantity, reserved_quantity FROM inventory_items WHERE variant_id = ? FOR UPDATE",
                (rs, row) -> new LockedStock(rs.getLong("id"), rs.getInt("quantity"), rs.getInt("reserved_quantity")),
                variantId);
    }

    public boolean shipStock(long inventoryId, long variantId, int quantity, long orderId) {
        int updated = jdbc.update(
                "UPDATE inventory_items SET quantity = quantity - ?, reserved_quantity = reserved_quantity - ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND quantity >= ? AND reserved_quantity >= ?",
                quantity, quantity, inventoryId, quantity, quantity);
        if (updated == 1) {
            jdbc.update("INSERT INTO stock_movements(variant_id, movement_type, quantity, reference_type, reference_id, note) "
                    + "VALUES (?, 'DELIVERY_COMPLETED', ?, 'ORDER', ?, 'Delivery completed')",
                    variantId, -quantity, orderId);
        }
        return updated == 1;
    }

    private DeliveryOrder mapOrder(ResultSet rs) throws SQLException {
        long orderId = rs.getLong("id");
        BigDecimal amount = rs.getBigDecimal("total_amount");
        List<DeliveryItem> items = jdbc.query(
                "SELECT product_id, product_name, variant_id, quantity FROM order_items WHERE order_id = ? ORDER BY id",
                (item, row) -> new DeliveryItem(item.getLong("product_id"), item.getString("product_name"),
                        item.getLong("variant_id"), item.getInt("quantity")),
                orderId);
        List<DeliveryHistory> history = jdbc.query(
                "SELECT from_status, to_status, changed_at, changed_by FROM delivery_status_history "
                        + "WHERE order_id = ? ORDER BY changed_at, id",
                (event, row) -> new DeliveryHistory(event.getString("from_status"), event.getString("to_status"),
                        event.getObject("changed_at", OffsetDateTime.class),
                        (Long) event.getObject("changed_by")),
                orderId);
        return new DeliveryOrder(orderId, rs.getString("status"), rs.getString("delivery_status"),
                rs.getString("receiver_name"), rs.getString("city"), amount.longValueExact(),
                rs.getObject("created_at", OffsetDateTime.class), items, history,rs.getString("payment_method"));
    }

    public record LockedDelivery(long id, String orderStatus, String deliveryStatus,String paymentMethod) {}
    public record OrderVariant(long variantId, int quantity) {}
    public record LockedStock(long inventoryId, int quantity, int reservedQuantity) {}
}
