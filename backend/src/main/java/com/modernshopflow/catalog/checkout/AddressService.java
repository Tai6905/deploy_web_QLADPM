package com.modernshopflow.catalog.checkout;

import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.modernshopflow.catalog.orders.OrderDtos.ShippingAddress;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    private final JdbcTemplate jdbc;
    public AddressService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Address(long id, String receiverName, String phone, String addressLine, String district, String city, boolean isDefault) {}
    public List<Address> list(long user) {
        return jdbc.query("SELECT * FROM user_addresses WHERE user_id=? ORDER BY is_default DESC,id DESC", (rs,row)->new Address(rs.getLong("id"),rs.getString("receiver_name"),rs.getString("receiver_phone"),rs.getString("address_line"),rs.getString("district"),rs.getString("city"),rs.getBoolean("is_default")),user);
    }
    @Transactional
    public Address save(long user, Long id, ShippingAddress value, boolean makeDefault) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user);
        var current=list(user);
        if(id==null) {
            var same=current.stream().filter(a->a.receiverName().equals(value.receiverName().trim()) && a.phone().equals(value.phone().trim()) && a.addressLine().equals(value.addressLine().trim()) && java.util.Objects.equals(a.district(),value.district()) && a.city().equals(value.city().trim())).findFirst();
            if(same.isPresent()) id=same.get().id();
        }
        Long requestedId=id;
        if(id!=null && current.stream().noneMatch(a->a.id()==requestedId)) throw new CatalogNotFoundException("Địa chỉ không tồn tại.");
        if(id==null && current.size()>=10) throw new IllegalArgumentException("Bạn có thể lưu tối đa 10 địa chỉ.");
        boolean defaultValue=makeDefault || current.isEmpty() || current.stream().anyMatch(a->a.id()==(requestedId==null?-1:requestedId) && a.isDefault());
        if(defaultValue) jdbc.update("UPDATE user_addresses SET is_default=FALSE WHERE user_id=?",user);
        if(id==null) {
            var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{var ps=connection.prepareStatement("INSERT INTO user_addresses(user_id,receiver_name,receiver_phone,address_line,district,city,is_default) VALUES(?,?,?,?,?,?,?)",new String[]{"id"});
                ps.setLong(1,user);ps.setString(2,value.receiverName().trim());ps.setString(3,value.phone().trim());ps.setString(4,value.addressLine().trim());ps.setString(5,value.district());ps.setString(6,value.city().trim());ps.setBoolean(7,defaultValue);return ps;},key);
            id=key.getKey().longValue();
        } else jdbc.update("UPDATE user_addresses SET receiver_name=?,receiver_phone=?,address_line=?,district=?,city=?,is_default=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",value.receiverName().trim(),value.phone().trim(),value.addressLine().trim(),value.district(),value.city().trim(),defaultValue,id,user);
        long savedId=id;
        return list(user).stream().filter(a->a.id()==savedId).findFirst().orElseThrow();
    }
    @Transactional
    public void delete(long user,long id) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user);
        if(jdbc.update("DELETE FROM user_addresses WHERE id=? AND user_id=?",id,user)==0) throw new CatalogNotFoundException("Địa chỉ không tồn tại.");
        var remaining=list(user);
        if(!remaining.isEmpty() && remaining.stream().noneMatch(Address::isDefault)) jdbc.update("UPDATE user_addresses SET is_default=TRUE WHERE id=?",remaining.getFirst().id());
    }
}
