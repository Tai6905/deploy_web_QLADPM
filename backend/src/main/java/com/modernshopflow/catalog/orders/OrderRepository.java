package com.modernshopflow.catalog.orders;

import com.modernshopflow.catalog.orders.OrderDtos.OrderLineResponse;
import com.modernshopflow.catalog.orders.OrderDtos.OrderResponse;
import com.modernshopflow.catalog.orders.OrderDtos.OrderSummary;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** SQL boundary for order writes, payment state, and inventory reservation rows. */
@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean productIsSellable(long productId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products p JOIN categories c ON c.id = p.category_id "
                        + "WHERE p.id = ? AND p.is_deleted = FALSE AND c.is_deleted = FALSE",
                Integer.class,
                productId);
        return count != null && count > 0;
    }

    /** Locks every variant inventory row before the service computes available stock. */
    public List<VariantAvailability> lockAvailableVariants(long productId) {
        return jdbc.query(
                """
                SELECT pv.id AS variant_id, ii.id AS inventory_item_id, p.name AS product_name,
                       p.list_price, GREATEST(ii.quantity - ii.reserved_quantity - COALESCE((
                           SELECT SUM(sh.quantity) FROM stock_holds sh
                           WHERE sh.inventory_item_id = ii.id AND sh.status = 'HOLDING'
                             AND sh.expires_at > CURRENT_TIMESTAMP
                       ), 0), 0) AS available_quantity
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id
                JOIN inventory_items ii ON ii.variant_id = pv.id
                WHERE pv.product_id = ? AND pv.is_deleted = FALSE AND p.is_deleted = FALSE
                ORDER BY pv.id
                FOR UPDATE OF ii
                """,
                (rs, rowNum) -> new VariantAvailability(
                        rs.getLong("variant_id"),
                        rs.getLong("inventory_item_id"),
                        rs.getString("product_name"),
                        wholeVnd(rs, "list_price"),
                        rs.getInt("available_quantity")),
                productId);
    }

    public long insertOrder(UUID orderRef, OrderDtos.CreateOrderRequest request, long total) {
        return insertOrder(orderRef, request, total, null);
    }

    public long insertOrder(UUID orderRef, OrderDtos.CreateOrderRequest request, long total, Long customerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO orders(order_ref, customer_name, customer_email, customer_phone,
                        receiver_name, receiver_phone, address_line, district, city,
                        payment_method, status, delivery_status, total_amount, customer_id,subtotal_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CARD', 'PENDING_PAYMENT', 'NONE', ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setObject(1, orderRef);
            statement.setString(2, request.customer().fullName().trim());
            statement.setString(3, blankToNull(request.customer().email()));
            statement.setString(4, blankToNull(request.customer().phone()));
            statement.setString(5, request.shippingAddress().receiverName().trim());
            statement.setString(6, request.shippingAddress().phone().trim());
            statement.setString(7, request.shippingAddress().addressLine().trim());
            statement.setString(8, blankToNull(request.shippingAddress().district()));
            statement.setString(9, request.shippingAddress().city().trim());
            statement.setBigDecimal(10, BigDecimal.valueOf(total));
            statement.setObject(11, customerId);
            statement.setBigDecimal(12, BigDecimal.valueOf(total));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Database did not return an order id");
        return key.longValue();
    }

    public void lockCustomer(long id) { jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,id); }
    public java.util.Optional<OrderResponse> checkout(UUID key,long user,String fingerprint) {
        var rows=jdbc.queryForList("SELECT id,customer_id,checkout_fingerprint FROM orders WHERE checkout_key=?",key);
        if(rows.isEmpty()) return java.util.Optional.empty();
        var row=rows.getFirst();
        if(!java.util.Objects.equals(((Number)row.get("customer_id")).longValue(),user) || !fingerprint.equals(row.get("checkout_fingerprint")))
            throw new com.modernshopflow.catalog.domain.CatalogConflictException("Yêu cầu này đã tạo đơn với nội dung khác. Vui lòng kiểm tra Đơn mua.");
        return java.util.Optional.of(findOrder(((Number)row.get("id")).longValue()));
    }
    public void checkoutPrice(long id,OrderDtos.CreateOrderRequest request,com.modernshopflow.catalog.checkout.VoucherService.Price price,String fingerprint) {
        jdbc.update("UPDATE orders SET payment_method=?,subtotal_amount=?,discount_amount=?,total_amount=?,voucher_code=?,checkout_key=?,checkout_fingerprint=? WHERE id=?",request.paymentMethod(),price.subtotalAmount(),price.discountAmount(),price.totalAmount(),price.voucherCode(),request.checkoutKey(),fingerprint,id);
    }

    public void insertOrderItem(long orderId, long productId, long variantId,
            String productName, long unitPrice, int quantity) {
        jdbc.update(
                "INSERT INTO order_items(order_id, product_id, variant_id, product_name, unit_price, quantity) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                orderId, productId, variantId, productName, BigDecimal.valueOf(unitPrice), quantity);
    }

    public void addStockHold(long inventoryItemId, long orderId, int quantity, OffsetDateTime expiresAt) {
        jdbc.update(
                "INSERT INTO stock_holds(inventory_item_id, order_id, quantity, status, expires_at) "
                        + "VALUES (?, ?, ?, 'HOLDING', ?)",
                inventoryItemId, orderId, quantity, expiresAt);
    }

    public void addMovement(long variantId, String type, int quantity, long orderId, String note) {
        jdbc.update(
                "INSERT INTO stock_movements(variant_id, movement_type, quantity, reference_type, reference_id, note) "
                        + "VALUES (?, ?, ?, 'ORDER', ?, ?)",
                variantId, type, quantity, orderId, note);
    }

    public void insertPaymentAttempt(long orderId, long total) {
        jdbc.update(
                "INSERT INTO payment_attempts(order_id, method, status, amount) SELECT ?, payment_method, 'PENDING', ? FROM orders WHERE id=?",
                orderId, BigDecimal.valueOf(total),orderId);
    }

    public OrderResponse findOrder(long id) {
        OrderHeader header = jdbc.queryForObject(ORDER_SQL + " WHERE o.id = ?", ORDER_HEADER, id);
        return toResponse(header);
    }

    public OrderResponse findOrderByRef(UUID orderRef) {
        OrderHeader header = jdbc.queryForObject(ORDER_SQL + " WHERE o.order_ref = ?", ORDER_HEADER, orderRef);
        return toResponse(header);
    }

    public OrderResponse findOwnedOrder(long id, long customerId) {
        return toResponse(jdbc.queryForObject(ORDER_SQL + " WHERE o.id = ? AND o.customer_id = ?", ORDER_HEADER, id, customerId));
    }

    public OrderResponse findOwnedOrderByRef(UUID reference, long customerId) {
        return toResponse(jdbc.queryForObject(ORDER_SQL + " WHERE o.order_ref = ? AND o.customer_id = ?", ORDER_HEADER, reference, customerId));
    }

    private static final String SEARCH_FILTER = """
                WHERE (CAST(? AS TEXT) IS NULL
                    OR LOWER(CONCAT(o.customer_name, ' ', o.order_ref, ' SF-', o.id)) LIKE LOWER(CONCAT('%', CAST(? AS TEXT), '%'))
                    OR EXISTS (SELECT 1 FROM order_items searched WHERE searched.order_id = o.id
                        AND LOWER(searched.product_name) LIKE LOWER(CONCAT('%', CAST(? AS TEXT), '%'))))
                """;

    public List<OrderSummary> listOrders(int size, int offset, String search) {
        return listOrders(size, offset, search, null);
    }

    public List<OrderSummary> listOrders(int size, int offset, String search, Long customerId) {
        return listOrders(size,offset,search,customerId,null);
    }

    public List<OrderSummary> listOrders(int size, int offset, String search, Long customerId, String status) {
        var result=jdbc.query(
                """
                SELECT o.id, o.order_ref, o.customer_name, o.total_amount, o.status,
                       o.delivery_status, o.created_at,o.payment_method,
                       COALESCE(pa.status, 'UNPAID') AS payment_status,
                       COALESCE(string_agg(DISTINCT oi.product_name, ', '), '') AS products,
                       COALESCE(SUM(oi.quantity), 0)::INT AS item_quantity
                FROM orders o
                LEFT JOIN payment_attempts pa ON pa.order_id = o.id
                LEFT JOIN order_items oi ON oi.order_id = o.id
                """ + SEARCH_FILTER + """
                AND (CAST(? AS BIGINT) IS NULL OR o.customer_id = ?)
                AND (CAST(? AS TEXT) IS NULL OR o.status = ?)
                GROUP BY o.id, pa.status
                ORDER BY
                """ + ("CANCELLED".equals(status) ? "(SELECT oc.cancelled_at FROM order_cancellations oc WHERE oc.order_id=o.id) DESC, o.id DESC" : "o.created_at DESC, o.id DESC") + " LIMIT ? OFFSET ?",
                (rs, rowNum) -> new OrderSummary(
                        rs.getLong("id"), rs.getObject("order_ref", UUID.class),
                        rs.getString("customer_name"), rs.getString("products"),
                        rs.getInt("item_quantity"), wholeVnd(rs, "total_amount"),
                        rs.getString("status"), rs.getString("payment_status"),
                        rs.getString("delivery_status"), rs.getObject("created_at", OffsetDateTime.class),rs.getString("payment_method")),
                search, search, search, customerId, customerId, status, status, size, offset);
        var info=findCancellations(result.stream().map(OrderSummary::id).toList());
        return result.stream().map(order -> order.withCancellation(info.get(order.id()))).toList();
    }

    public long countOrders(String search) {
        return countOrders(search, null);
    }

    public long countOrders(String search, Long customerId) {
        return countOrders(search,customerId,null);
    }

    public long countOrders(String search, Long customerId, String status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM orders o " + SEARCH_FILTER
                + " AND (CAST(? AS BIGINT) IS NULL OR o.customer_id = ?) AND (CAST(? AS TEXT) IS NULL OR o.status=?)", Long.class, search, search, search, customerId, customerId,status,status);
        return count == null ? 0 : count;
    }

    private static final String ORDER_SQL = """
            SELECT o.id, o.order_ref, o.status, o.delivery_status, o.payment_method,
                   o.total_amount, o.customer_name, o.customer_email, o.customer_phone,
                   o.receiver_name, o.receiver_phone, o.address_line, o.district, o.city, o.created_at,COALESCE(o.subtotal_amount,o.total_amount) AS subtotal_amount,o.discount_amount,o.voucher_code
            FROM orders o
            """;

        private static final RowMapper<OrderHeader> ORDER_HEADER = OrderRepository::mapOrderHeader;

        private OrderResponse toResponse(OrderHeader header) {
        return new OrderResponse(
            header.id(), header.orderRef(), header.status(), header.deliveryStatus(), header.paymentMethod(),
            header.totalAmount(), header.customer(), header.shippingAddress(), listOrderLines(header.id()),
            header.createdAt(), header.subtotalAmount(),header.discountAmount(),header.voucherCode(),findCancellations(List.of(header.id())).get(header.id()));
    }

    private java.util.Map<Long,OrderDtos.CancellationInfo> findCancellations(List<Long> ids) {
        var result=new java.util.HashMap<Long,OrderDtos.CancellationInfo>();
        if(ids.isEmpty()) return result;
        String placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        jdbc.query("SELECT order_id,reason,cancelled_at,actor_kind,payment_outcome,refund_amount FROM order_cancellations WHERE order_id IN ("+placeholders+")",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> result.put(row.getLong("order_id"),new OrderDtos.CancellationInfo(row.getString("reason"),row.getObject("cancelled_at",OffsetDateTime.class),row.getString("actor_kind"),row.getString("payment_outcome"),wholeVnd(row,"refund_amount"))),ids.toArray());
        return result;
    }

        private List<OrderLineResponse> listOrderLines(long orderId) {
        return jdbc.query(
            "SELECT product_id, product_name, variant_id, unit_price, quantity FROM order_items "
                + "WHERE order_id = ? ORDER BY id",
            (rs, rowNum) -> new OrderLineResponse(
                rs.getLong("product_id"), rs.getString("product_name"), rs.getLong("variant_id"),
                wholeVnd(rs, "unit_price"), rs.getInt("quantity"),
                wholeVnd(rs, "unit_price") * rs.getInt("quantity")),
            orderId);
        }

        private static OrderHeader mapOrderHeader(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("id");
        return new OrderHeader(
            id, rs.getObject("order_ref", UUID.class), rs.getString("status"),
            rs.getString("delivery_status"), rs.getString("payment_method"), wholeVnd(rs, "total_amount"),
            new OrderDtos.Customer(rs.getString("customer_name"), rs.getString("customer_email"), rs.getString("customer_phone")),
            new OrderDtos.ShippingAddress(rs.getString("receiver_name"), rs.getString("receiver_phone"),
                rs.getString("address_line"), rs.getString("district"), rs.getString("city")),
            rs.getObject("created_at", OffsetDateTime.class),wholeVnd(rs,"subtotal_amount"),wholeVnd(rs,"discount_amount"),rs.getString("voucher_code"));
        }

    private static long wholeVnd(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column).longValueExact();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record VariantAvailability(long variantId, long inventoryItemId, String productName,
            long unitPrice, int availableQuantity) {}

    private record OrderHeader(long id, UUID orderRef, String status, String deliveryStatus,
            String paymentMethod, long totalAmount, OrderDtos.Customer customer,
            OrderDtos.ShippingAddress shippingAddress, OffsetDateTime createdAt,long subtotalAmount,long discountAmount,String voucherCode) {}
}
