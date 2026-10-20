import com.modernshopflow.catalog.CatalogApplication;
import com.modernshopflow.catalog.api.*;
import com.modernshopflow.catalog.checkout.*;
import com.modernshopflow.catalog.checkout.CheckoutController.*;
import com.modernshopflow.catalog.orders.OrderDtos.*;
import com.modernshopflow.catalog.orders.OrderService;
import com.modernshopflow.catalog.service.CatalogService;
import com.modernshopflow.catalog.receiving.ReceivingService;
import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingRequest;
import com.modernshopflow.catalog.payments.PaymentService;
import com.modernshopflow.catalog.payments.PaymentDtos.PaymentRequest;
import com.modernshopflow.catalog.delivery.DeliveryService;
import com.shopflow.backend.service.AuthService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Applies pending additive migrations, then rolls back all checkout fixtures. */
class CheckoutSmoke {
    static void check(boolean value,String message) { if(!value)throw new IllegalStateException(message);System.out.println("PASS: "+message); }
    public static void main(String[] args) {
        try(var app=SpringApplication.run(CatalogApplication.class,"--server.port=0","--debug=false","--logging.level.root=WARN",
                "--spring.main.banner-mode=off","--logging.level.org.springframework.boot.security.autoconfigure=ERROR","--app.bootstrap.owner.email=","--app.bootstrap.warehouse.email=","--app.schema.initializer.enabled=true")) {
            var jdbc=app.getBean(JdbcTemplate.class);
            check(jdbc.queryForObject("SELECT COUNT(*) FROM schema_migrations WHERE script_name='add_checkout_addresses_and_vouchers.sql'",Long.class)==1,"checkout migration is applied");
            long[] userId={0},productId={0};
            var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status->{
                status.setRollbackOnly();
                String tag=UUID.randomUUID().toString();
                var user=app.getBean(AuthService.class).registerUser("checkout-"+tag+"@example.invalid",UUID.randomUUID().toString(),"Checkout rollback test");userId[0]=user.getId();
                var auth=UsernamePasswordAuthenticationToken.authenticated(user.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
                var catalog=app.getBean(CatalogService.class);
                var category=catalog.createCategory(new CategoryRequest("Checkout rollback "+tag,"checkout-"+tag,null));
                var product=catalog.createProduct(new ProductCreateRequest(category.id(),"CHECKOUT-"+tag,"Checkout rollback product","Test",BigDecimal.valueOf(50000),BigDecimal.valueOf(100000)));
                productId[0]=product.id();long variant=product.productVariants().getFirst().id();
                app.getBean(ReceivingService.class).receive(new ReceivingRequest(variant,10,"Rollback only",null,BigDecimal.valueOf(50000)));
                var checkout=app.getBean(CheckoutController.class);var addresses=app.getBean(AddressService.class);
                var shipping=new ShippingAddress("Rollback receiver","0901234567","Rollback test street","District","City");
                var saved=addresses.save(user.getId(),null,shipping,true);
                check(saved.isDefault() && addresses.list(user.getId()).size()==1,"address saved under the buyer account");
                var lines=List.of(new OrderLineRequest(product.id(),2));
                var quote=checkout.quote(auth,new QuoteInput(lines,"moc10"));
                check(quote.subtotalAmount()==200000 && quote.discountAmount()==20000 && quote.totalAmount()==180000,"voucher quote uses current prices and is case insensitive");
                UUID key=UUID.randomUUID();
                var input=new CheckoutInput(shipping,user.getEmail(),"COD",lines,"MOC10",key,true,saved.id(),true,180000L);
                var order=checkout.place(auth,input);
                check(order.status().equals("PENDING_PAYMENT") && order.discountAmount()==20000 && order.totalAmount()==180000,"COD is created unpaid with a discount snapshot");
                check(checkout.place(auth,input).id().equals(order.id()) && addresses.list(user.getId()).size()==1,"retry creates neither another order nor another address");
                check(jdbc.queryForObject("SELECT reserved_quantity FROM inventory_items WHERE variant_id=?",Integer.class,variant)==2,"COD stock is reserved without expiring payment holds");
                var delivery=app.getBean(DeliveryService.class);
                delivery.advance(order.id(),"PREPARING");delivery.advance(order.id(),"SHIPPED");delivery.advance(order.id(),"DELIVERED");
                check(jdbc.queryForObject("SELECT status FROM payment_attempts WHERE order_id=?",String.class,order.id()).equals("SUCCESS")
                        && jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order.id()).equals("PAID"),"COD collection is recorded only at completed delivery");
                check(jdbc.queryForObject("SELECT quantity FROM inventory_items WHERE variant_id=?",Integer.class,variant)==8
                        && jdbc.queryForObject("SELECT reserved_quantity FROM inventory_items WHERE variant_id=?",Integer.class,variant)==0,"delivery decrements stock exactly once");
                addresses.save(user.getId(),saved.id(),new ShippingAddress("Updated name","0901234567","Updated street","District","Updated city"),true);
                check(app.getBean(OrderService.class).getMine(order.id(),user.getId()).shippingAddress().addressLine().equals("Rollback test street"),"address edits preserve old order snapshots");
                var bank=checkout.place(auth,new CheckoutInput(shipping,user.getEmail(),"BANK_TRANSFER",lines,"MOC10",UUID.randomUUID(),false,null,false,180000L));
                check(bank.paymentMethod().equals("BANK_TRANSFER"),"bank-transfer simulation stores the selected method");
                var failed=app.getBean(PaymentService.class).process(bank.orderRef(),new PaymentRequest("FAILED","Rollback simulation"));
                check(failed.status().equals("FAILED") && jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE order_id=?",Integer.class,bank.id())==0,"failed payment releases its voucher reservation");
                var card=checkout.place(auth,new CheckoutInput(shipping,user.getEmail(),"CARD",lines,null,UUID.randomUUID(),false,null,false,200000L));
                check(app.getBean(PaymentService.class).process(card.orderRef(),new PaymentRequest("SUCCESS",null)).status().equals("SUCCESS"),"card simulation succeeds after explicit confirmation");
                UUID legacy=UUID.randomUUID();
                jdbc.update("INSERT INTO orders(order_ref,customer_name,receiver_name,receiver_phone,address_line,city,total_amount,customer_id) VALUES(?,'Legacy','Receiver','0901234567','Test','City',100000,?)",legacy,user.getId());
                check(app.getBean(OrderService.class).getByReference(legacy).subtotalAmount()==100000,"old deployed CARD inserts remain compatible during rollout");
            });
            check(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Long.class,userId[0])==0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Long.class,productId[0])==0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id=?",Long.class,userId[0])==0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id=?",Long.class,userId[0])==0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE user_id=?",Long.class,userId[0])==0,"all checkout fixtures rolled back; live accounts/orders are untouched");
        }
    }
}
