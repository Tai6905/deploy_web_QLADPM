package com.modernshopflow.catalog.checkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VoucherService {
    private final JdbcTemplate jdbc;
    public VoucherService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Voucher(long id,String code,String title,String type,BigDecimal value,BigDecimal maximum,long minimum,Integer maximumUses,int perUser,int used,OffsetDateTime from,OffsetDateTime to) {}
    public record Price(long subtotalAmount,long discountAmount,long totalAmount,String voucherCode,Long voucherId) {}
    public List<Voucher> available() {
        return jdbc.query("SELECT * FROM vouchers WHERE status='ACTIVE' AND valid_from<=CURRENT_TIMESTAMP AND valid_to>CURRENT_TIMESTAMP AND (max_usages_total IS NULL OR current_usage_count<max_usages_total) ORDER BY min_order_value,code",this::map);
    }
    private Voucher map(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new Voucher(rs.getLong("id"),rs.getString("code"),rs.getString("title"),rs.getString("discount_type"),rs.getBigDecimal("discount_value"),rs.getBigDecimal("max_discount_amount"),rs.getBigDecimal("min_order_value").longValueExact(),(Integer)rs.getObject("max_usages_total"),rs.getInt("max_usages_per_user"),rs.getInt("current_usage_count"),rs.getObject("valid_from",OffsetDateTime.class),rs.getObject("valid_to",OffsetDateTime.class));
    }
    public Price price(long subtotal,String code,Long user,boolean lock) {
        if(code==null || code.isBlank()) return new Price(subtotal,0,subtotal,null,null);
        if(user==null) throw new IllegalArgumentException("Đăng nhập để sử dụng voucher.");
        String normalized=code.trim().toUpperCase(Locale.ROOT);
        var matches=jdbc.query("SELECT * FROM vouchers WHERE UPPER(code)=? AND status='ACTIVE'"+(lock?" FOR UPDATE":""),this::map,normalized);
        if(matches.isEmpty()) throw new IllegalArgumentException("Mã ưu đãi không tồn tại hoặc đã tắt.");
        var voucher=matches.getFirst();
        var now=OffsetDateTime.now();
        if(now.isBefore(voucher.from()) || !now.isBefore(voucher.to())) throw new IllegalArgumentException("Voucher chưa có hiệu lực hoặc đã hết hạn.");
        if(subtotal<voucher.minimum()) throw new IllegalArgumentException("Đơn hàng chưa đạt mức tối thiểu "+voucher.minimum()+"đ của voucher.");
        if((voucher.maximumUses()!=null && voucher.used()>=voucher.maximumUses()) || jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=? AND user_id=?",Long.class,voucher.id(),user)>=voucher.perUser()) throw new IllegalArgumentException("Voucher đã hết lượt sử dụng.");
        BigDecimal discount="PERCENTAGE".equals(voucher.type())?BigDecimal.valueOf(subtotal).multiply(voucher.value()).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN):voucher.value();
        if(voucher.maximum()!=null) discount=discount.min(voucher.maximum());
        long amount=discount.min(BigDecimal.valueOf(subtotal)).setScale(0,RoundingMode.DOWN).longValueExact();
        return new Price(subtotal,amount,subtotal-amount,voucher.code(),voucher.id());
    }
    public void record(Price price,long order,Long user) {
        if(price.voucherId()==null) return;
        jdbc.update("INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES(?,?,?,?)",price.voucherId(),user,order,price.discountAmount());
        jdbc.update("UPDATE vouchers SET current_usage_count=current_usage_count+1 WHERE id=?",price.voucherId());
    }
    public void release(long order) {
        var vouchers=jdbc.queryForList("SELECT voucher_id FROM voucher_usages WHERE order_id=?",Long.class,order);
        for(long id:vouchers) {
            jdbc.queryForObject("SELECT id FROM vouchers WHERE id=? FOR UPDATE",Long.class,id);
            if(jdbc.update("DELETE FROM voucher_usages WHERE order_id=?",order)>0) jdbc.update("UPDATE vouchers SET current_usage_count=GREATEST(current_usage_count-1,0) WHERE id=?",id);
        }
    }
}
