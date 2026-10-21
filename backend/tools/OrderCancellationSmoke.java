import com.modernshopflow.catalog.CatalogApplication;
import com.modernshopflow.catalog.api.CategoryRequest;
import com.modernshopflow.catalog.api.ProductCreateRequest;
import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.orders.OrderCancellationService;
import com.modernshopflow.catalog.orders.OrderService;
import com.modernshopflow.catalog.orders.OrderDtos.*;
import com.modernshopflow.catalog.payments.PaymentService;
import com.modernshopflow.catalog.payments.PaymentDtos.PaymentRequest;
import com.modernshopflow.catalog.delivery.DeliveryService;
import com.modernshopflow.catalog.receiving.ReceivingService;
import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingRequest;
import com.modernshopflow.catalog.service.CatalogService;
import com.shopflow.backend.service.AuthService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Applies schema migration, then rolls back every business fixture. Never cancels a real order. */
class OrderCancellationSmoke {
    static void check(boolean condition,String message) {
        if(!condition) throw new IllegalStateException(message);
        System.out.println("PASS: "+message);
    }
    public static void main(String[] args) {
        try(var app=SpringApplication.run(CatalogApplication.class,"--server.port=0","--debug=false","--logging.level.root=WARN",
                "--spring.main.banner-mode=off","--logging.level.org.springframework.boot.security.autoconfigure=ERROR",
                "--app.bootstrap.owner.email=","--app.bootstrap.warehouse.email=","--app.schema.initializer.enabled=true")) {
            var jdbc=app.getBean(JdbcTemplate.class);
            check(jdbc.queryForObject("SELECT COUNT(*) FROM schema_migrations WHERE script_name='add_order_cancellations.sql'",Long.class)==1,"cancellation schema is applied");
            var orders=app.getBean(OrderService.class);
            var cancellations=app.getBean(OrderCancellationService.class);
            long[] userId={0},productId={0};
            var fixtureOrders=new java.util.ArrayList<Long>();
            var transaction=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status->{
                status.setRollbackOnly();
                String tag=UUID.randomUUID().toString();
                var user=app.getBean(AuthService.class).registerUser("cancel-"+tag+"@example.invalid",UUID.randomUUID().toString(),"Cancellation rollback test");
                userId[0]=user.getId();
                var catalog=app.getBean(CatalogService.class);
                var category=catalog.createCategory(new CategoryRequest("Cancellation test",tag,null));
                var product=catalog.createProduct(new ProductCreateRequest(category.id(),"CANCEL-"+tag,"Rollback test instrument",null,BigDecimal.ZERO,BigDecimal.valueOf(25000)));
                productId[0]=product.id();long variant=product.productVariants().getFirst().id();
                app.getBean(ReceivingService.class).receive(new ReceivingRequest(variant,10,"Rollback test",null,BigDecimal.valueOf(10000)));
                String voucher="CANCEL-"+tag;
                jdbc.update("INSERT INTO vouchers(code,title,discount_type,discount_value,min_order_value,max_usages_total,max_usages_per_user,valid_from,valid_to) VALUES(?,'Rollback test','FIXED_AMOUNT',1000,0,10,1,CURRENT_TIMESTAMP - INTERVAL '1 day',CURRENT_TIMESTAMP + INTERVAL '1 day')",voucher);
                var customer=new Customer("Rollback buyer",user.getEmail(),"0901234567");
                var address=new ShippingAddress("Rollback buyer","0901234567","Rollback street",null,"Test city");
                var lines=List.of(new OrderLineRequest(product.id(),2));
                for(String method:List.of("CARD","COD","BANK_TRANSFER")) {
                    var order=orders.create(new CreateOrderRequest(customer,address,method,lines,voucher,UUID.randomUUID()),user.getId());
                    fixtureOrders.add(order.id());
                    if(method.equals("BANK_TRANSFER")) app.getBean(PaymentService.class).process(order.orderRef(),new PaymentRequest("SUCCESS",null));
                    if(method.equals("COD")) app.getBean(DeliveryService.class).advance(order.id(),"PREPARING");
                    var result=cancellations.cancelMine(order.id(),user.getId(),"Rollback test cancellation");
                    check(result.status().equals("CANCELLED") && result.cancellation()!=null,method+" cancellation records its reason and time");
                    check(jdbc.queryForObject("SELECT reserved_quantity FROM inventory_items WHERE variant_id=?",Integer.class,variant)==0
                            && jdbc.queryForObject("SELECT quantity FROM inventory_items WHERE variant_id=?",Integer.class,variant)==10,"cancellation releases reservations without changing physical stock");
                    check(jdbc.queryForObject("SELECT current_usage_count FROM vouchers WHERE code=?",Integer.class,voucher)==0,"cancellation returns voucher usage");
                    var repeated=cancellations.cancelMine(order.id(),user.getId(),"Retry with another reason");
                    check(repeated.cancellation().equals(result.cancellation()) && jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_type='ORDER' AND reference_id=? AND movement_type='ORDER_CANCELLED_RELEASE'",Integer.class,order.id())==1,"repeated cancellation preserves the original audit and does not release stock twice");
                    if(method.equals("BANK_TRANSFER")) check(result.cancellation().paymentOutcome().equals("SIMULATED_REFUND") && result.cancellation().refundAmount()==49000,"paid simulation records the exact discounted refund amount");
                }
                var shipped=orders.create(new CreateOrderRequest(customer,address,"COD",lines,null,UUID.randomUUID()),user.getId());fixtureOrders.add(shipped.id());
                var delivery=app.getBean(DeliveryService.class);delivery.advance(shipped.id(),"PREPARING");delivery.advance(shipped.id(),"SHIPPED");
                try { cancellations.cancelMine(shipped.id(),user.getId(),"Too late");throw new IllegalStateException("Shipped order was cancelled"); }
                catch(CatalogConflictException expected) { check(orders.getMine(shipped.id(),user.getId()).status().equals("PENDING_PAYMENT"),"shipped order cancellation is rejected without changing its status"); }
                check(orders.listMine(user.getId(),0,10,null,"CANCELLED").totalItems()==3,"cancelled history filters and counts correctly on PostgreSQL");
            });
            check(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Integer.class,userId[0])==0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Integer.class,productId[0])==0,"test customer and product are rolled back");
            for(long id:fixtureOrders) check(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?",Integer.class,id)==0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM order_cancellations WHERE order_id=?",Integer.class,id)==0,"test order and cancellation audit are rolled back");
        }
    }
}
