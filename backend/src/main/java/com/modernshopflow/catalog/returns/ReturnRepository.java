package com.modernshopflow.catalog.returns;

import com.modernshopflow.catalog.returns.ReturnDtos.ReturnItem;
import com.modernshopflow.catalog.returns.ReturnDtos.ReturnResponse;
import com.modernshopflow.catalog.returns.ReturnDtos.ReturnableItem;
import com.modernshopflow.catalog.returns.ReturnDtos.ReturnableOrder;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Locks return/order rows and persists return lines without changing inventory prematurely. */
@Repository
public class ReturnRepository {
    private final JdbcTemplate jdbc;

    public ReturnRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LockedOrder lockOrder(long orderId) {
        return jdbc.queryForObject("SELECT id, status, delivery_status FROM orders WHERE id = ? FOR UPDATE",
                (rs, row) -> new LockedOrder(rs.getLong("id"), rs.getString("status"), rs.getString("delivery_status")), orderId);
    }

    public List<ReturnableOrder> listReturnableOrders() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM orders WHERE status = 'PAID' AND delivery_status = 'DELIVERED' ORDER BY created_at DESC, id DESC", Long.class);
        return ids.stream().map(this::getReturnableOrder).toList();
    }

    public ReturnableOrder getReturnableOrder(long orderId) {
        return jdbc.queryForObject(
                "SELECT id, receiver_name, city, total_amount, created_at FROM orders WHERE id = ?",
                (rs, row) -> {
                    BigDecimal amount = rs.getBigDecimal("total_amount");
                    return new ReturnableOrder(rs.getLong("id"), rs.getString("receiver_name"), rs.getString("city"),
                            amount.longValueExact(), rs.getObject("created_at", OffsetDateTime.class), listReturnableItems(rs.getLong("id")));
                }, orderId);
    }

    public List<ReturnableItem> listReturnableItems(long orderId) {
        return jdbc.query(
                """
                SELECT oi.id AS order_item_id, oi.product_id, oi.variant_id, oi.product_name, oi.quantity,
                       COALESCE(SUM(CASE WHEN rr.status IN ('REQUESTED', 'APPROVED', 'RESTOCKED')
                                         THEN rri.quantity ELSE 0 END), 0)::INT AS returned_quantity
                FROM order_items oi
                LEFT JOIN return_request_items rri ON rri.order_item_id = oi.id
                LEFT JOIN return_requests rr ON rr.id = rri.return_id
                WHERE oi.order_id = ?
                GROUP BY oi.id
                ORDER BY oi.id
                """,
                (rs, row) -> {
                    int purchased = rs.getInt("quantity");
                    int returned = rs.getInt("returned_quantity");
                    return new ReturnableItem(rs.getLong("order_item_id"), rs.getLong("product_id"),
                            rs.getLong("variant_id"), rs.getString("product_name"), purchased,
                            returned, Math.max(purchased - returned, 0));
                }, orderId);
    }

    public long insert(long orderId, String reason) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO return_requests(order_id, status, reason) VALUES (?, 'REQUESTED', ?)", new String[] {"id"});
            statement.setLong(1, orderId);
            statement.setString(2, reason);
            return statement;
        }, holder);
        Number key = holder.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a return id");
        return key.longValue();
    }

    public void insertItem(long returnId, long orderItemId, int quantity) {
        jdbc.update("INSERT INTO return_request_items(return_id, order_item_id, quantity) VALUES (?, ?, ?)", returnId, orderItemId, quantity);
    }

    public LockedReturn lockReturn(long returnId) {
        return jdbc.queryForObject("SELECT id, order_id, status, restockable FROM return_requests WHERE id = ? FOR UPDATE",
                (rs, row) -> new LockedReturn(rs.getLong("id"), rs.getLong("order_id"), rs.getString("status"),
                        (Boolean) rs.getObject("restockable")), returnId);
    }

    public void updateStatus(long id, String status, Boolean restockable) {
        jdbc.update("UPDATE return_requests SET status = ?, restockable = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status, restockable, id);
    }

    public List<ReturnItem> listItems(long returnId) {
        return jdbc.query(
                "SELECT rri.order_item_id, oi.product_id, oi.variant_id, oi.product_name, rri.quantity "
                        + "FROM return_request_items rri JOIN order_items oi ON oi.id = rri.order_item_id "
                        + "WHERE rri.return_id = ? ORDER BY rri.id",
                (rs, row) -> new ReturnItem(rs.getLong("order_item_id"), rs.getLong("product_id"),
                        rs.getLong("variant_id"), rs.getString("product_name"), rs.getInt("quantity")), returnId);
    }

    public List<ReturnResponse> list(int limit) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM return_requests ORDER BY created_at DESC, id DESC LIMIT ?", Long.class, limit);
        return ids.stream().map(this::get).toList();
    }

    public ReturnResponse get(long id) {
        return jdbc.queryForObject(
                "SELECT id, order_id, status, reason, restockable, created_at, updated_at FROM return_requests WHERE id = ?",
                (rs, row) -> new ReturnResponse(rs.getLong("id"), rs.getLong("order_id"), rs.getString("status"),
                        rs.getString("reason"), (Boolean) rs.getObject("restockable"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class), listItems(rs.getLong("id"))), id);
    }

    public LockedVariantInventory lockInventory(long variantId) {
        jdbc.update("INSERT INTO inventory_items(variant_id) VALUES (?) ON CONFLICT (variant_id) DO NOTHING", variantId);
        return jdbc.queryForObject("SELECT id, quantity FROM inventory_items WHERE variant_id = ? FOR UPDATE",
                (rs, row) -> new LockedVariantInventory(rs.getLong("id"), rs.getInt("quantity")), variantId);
    }

    public void restock(long inventoryId, long variantId, int quantity, long returnId) {
        jdbc.update("UPDATE inventory_items SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", quantity, inventoryId);
        jdbc.update("INSERT INTO stock_movements(variant_id, movement_type, quantity, reference_type, reference_id, note) "
                        + "VALUES (?, 'RETURN_RESTOCK', ?, 'RETURN', ?, 'Approved return restocked')",
                variantId, quantity, returnId);
    }

    public record LockedOrder(long id, String status, String deliveryStatus) {}
    public record LockedReturn(long id, long orderId, String status, Boolean restockable) {}
    public record LockedVariantInventory(long inventoryId, int quantity) {}
}