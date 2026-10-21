package com.modernshopflow.catalog.orders;

import com.modernshopflow.catalog.checkout.VoucherService;
import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.modernshopflow.catalog.orders.OrderDtos.OrderResponse;
import com.modernshopflow.catalog.payments.PaymentRepository;
import java.util.List;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Same order -> payment -> inventory -> voucher lock order as payment/delivery. */
@Service
public class OrderCancellationService {
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final VoucherService vouchers;

    public OrderCancellationService(JdbcTemplate jdbc, OrderRepository orders, PaymentRepository payments, VoucherService vouchers) {
        this.jdbc=jdbc; this.orders=orders; this.payments=payments; this.vouchers=vouchers;
    }

    @Transactional
    public OrderResponse cancelMine(long id, long userId, String reason) { return cancel(id,userId,userId,"CUSTOMER",reason); }

    @Transactional
    public OrderResponse cancelForStaff(long id, long staffId, String reason) { return cancel(id,null,staffId,"STAFF",reason); }

    private OrderResponse cancel(long id, Long customerId, long actorId, String actorKind, String reason) {
        String clean=reason==null?"":reason.trim();
        if(clean.isBlank() || clean.length()>500) throw new IllegalArgumentException("Lý do hủy cần từ 1 đến 500 ký tự.");
        LockedOrder order;
        try {
            order=jdbc.queryForObject("SELECT status,delivery_status,total_amount FROM orders WHERE id=? AND (CAST(? AS BIGINT) IS NULL OR customer_id=?) FOR UPDATE",
                    (row,index)->new LockedOrder(row.getString(1),row.getString(2),row.getBigDecimal(3).longValueExact()),id,customerId,customerId);
        } catch(EmptyResultDataAccessException error) { throw new CatalogNotFoundException("Không tìm thấy đơn hàng."); }
        // A repeated click/retry cannot release stock, vouchers or refunds twice.
        if("CANCELLED".equals(order.status())) return orders.findOrder(id);
        if(!Set.of("NONE","PREPARING").contains(order.deliveryStatus()))
            throw new CatalogConflictException("Đơn đã bàn giao vận chuyển hoặc đã giao, không thể hủy. Vui lòng liên hệ cửa hàng hoặc dùng chức năng đổi trả.");
        if(!Set.of("PENDING_PAYMENT","PAID","PAYMENT_FAILED").contains(order.status()))
            throw new CatalogConflictException("Trạng thái đơn không cho phép hủy.");
        com.modernshopflow.catalog.payments.PaymentDtos.PaymentResponse payment;
        try { payment=payments.lockPayment(id); }
        catch(EmptyResultDataAccessException error) { throw new CatalogConflictException("Không tìm thấy phiên thanh toán của đơn. Vui lòng liên hệ cửa hàng."); }
        boolean paid="PAID".equals(order.status());
        if(paid != "SUCCESS".equals(payment.status())) throw new CatalogConflictException("Trạng thái thanh toán không đồng nhất, vui lòng liên hệ cửa hàng.");
        if(paid && payment.amount()!=order.totalAmount()) throw new CatalogConflictException("Số tiền thanh toán không khớp đơn hàng. Vui lòng liên hệ cửa hàng.");
        // CARD and BANK_TRANSFER in this project are payment simulations.
        if(paid && !Set.of("CARD","BANK_TRANSFER").contains(payment.method()))
            throw new CatalogConflictException("Đơn đã thu tiền COD cần được cửa hàng kiểm tra trước khi hoàn tiền.");

        List<Hold> holds=jdbc.query("""
                SELECT sh.id,sh.status,sh.quantity,ii.id AS inventory_id,ii.variant_id
                FROM stock_holds sh JOIN inventory_items ii ON ii.id=sh.inventory_item_id
                WHERE sh.order_id=? AND sh.status IN ('HOLDING','COMMITTED')
                ORDER BY ii.variant_id,sh.id FOR UPDATE OF sh,ii
                """,(row,index)->new Hold(row.getLong("id"),row.getString("status"),row.getInt("quantity"),row.getLong("inventory_id"),row.getLong("variant_id")),id);
        if(paid || ("COD".equals(payment.method()) && "PENDING_PAYMENT".equals(order.status()))) {
            var expected=new java.util.HashMap<Long,Long>();
            jdbc.query("SELECT variant_id,SUM(quantity) AS quantity FROM order_items WHERE order_id=? GROUP BY variant_id",
                    (org.springframework.jdbc.core.RowCallbackHandler) row -> expected.put(row.getLong(1),row.getLong(2)),id);
            var committed=new java.util.HashMap<Long,Long>();
            holds.stream().filter(hold->hold.status().equals("COMMITTED")).forEach(hold->committed.merge(hold.variantId(),(long)hold.quantity(),Long::sum));
            if(expected.isEmpty() || !expected.equals(committed))
                throw new CatalogConflictException("Thông tin giữ hàng của đơn không đầy đủ. Vui lòng liên hệ cửa hàng để kiểm tra.");
        }
        for(var hold:holds) {
            if("COMMITTED".equals(hold.status()) && jdbc.update("UPDATE inventory_items SET reserved_quantity=reserved_quantity-?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND reserved_quantity>=?",
                    hold.quantity(),hold.inventoryId(),hold.quantity())!=1)
                throw new CatalogConflictException("Tồn kho giữ cho đơn không đồng nhất. Chưa có thay đổi nào được lưu.");
            jdbc.update("UPDATE stock_holds SET status='RELEASED' WHERE id=?",hold.id());
            jdbc.update("INSERT INTO stock_movements(variant_id,movement_type,quantity,reference_type,reference_id,note,created_by) VALUES(?,'ORDER_CANCELLED_RELEASE',?,'ORDER',?,?,?)",
                    hold.variantId(),-hold.quantity(),id,clean,actorId);
        }
        if(paid) {
            // Preserve the original charge timestamp and amount for audit.
            jdbc.update("UPDATE payment_attempts SET status='REFUNDED' WHERE id=?",payment.id());
        } else if("PENDING".equals(payment.status())) {
            payments.updatePayment(payment.id(),"CANCELLED",clean);
        }
        vouchers.release(id);
        jdbc.update("INSERT INTO order_cancellations(order_id,reason,cancelled_by,actor_kind,previous_status,previous_delivery_status,payment_outcome,refund_amount) VALUES(?,?,?,?,?,?,?,?)",
                id,clean,actorId,actorKind,order.status(),order.deliveryStatus(),paid?"SIMULATED_REFUND":"NOT_CHARGED",paid?payment.amount():0);
        payments.updateOrderStatus(id,"CANCELLED");
        return orders.findOrder(id);
    }
    private record LockedOrder(String status,String deliveryStatus,long totalAmount) {}
    private record Hold(long id,String status,int quantity,long inventoryId,long variantId) {}
}
