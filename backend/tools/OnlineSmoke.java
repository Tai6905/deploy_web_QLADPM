import com.modernshopflow.catalog.CatalogApplication;
import com.modernshopflow.catalog.api.CategoryRequest;
import com.modernshopflow.catalog.api.ProductCreateRequest;
import com.modernshopflow.catalog.service.CatalogService;
import com.modernshopflow.catalog.inventory.InventoryService;
import com.modernshopflow.catalog.inventory.InventoryDtos.ThresholdRequest;
import com.modernshopflow.catalog.receiving.ReceivingService;
import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingRequest;
import com.modernshopflow.catalog.receiving.ReceivingRepository;
import com.modernshopflow.catalog.orders.OrderService;
import com.modernshopflow.catalog.orders.OrderDtos.*;
import com.modernshopflow.catalog.payments.PaymentService;
import com.modernshopflow.catalog.payments.PaymentDtos.PaymentRequest;
import com.modernshopflow.catalog.delivery.DeliveryService;
import com.modernshopflow.catalog.returns.ReturnService;
import com.modernshopflow.catalog.returns.ReturnDtos.*;
import com.shopflow.backend.service.AuthService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Applies pending schema migrations, then rolls back all business test data. */
class OnlineSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Smoke check failed: " + message);
        System.out.println("PASS: " + message);
    }

    public static void main(String[] args) {
        try (var app = SpringApplication.run(CatalogApplication.class,
                "--server.port=0", "--debug=false", "--logging.level.root=WARN", "--spring.main.banner-mode=off")) {
            var jdbc = app.getBean(JdbcTemplate.class);
            List<String> tables = List.of("users", "categories", "products", "product_variants", "inventory_items",
                    "orders", "order_items", "payment_attempts", "stock_holds", "receiving_records",
                    "stock_movements", "delivery_status_history", "return_requests", "return_request_items");
            var before = tables.stream().map(t -> jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class)).toList();
            var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                status.setRollbackOnly();
                var catalog = app.getBean(CatalogService.class);
                var inventory = app.getBean(InventoryService.class);
                var receiving = app.getBean(ReceivingService.class);
                var orders = app.getBean(OrderService.class);
                var payments = app.getBean(PaymentService.class);
                var deliveries = app.getBean(DeliveryService.class);
                var returns = app.getBean(ReturnService.class);
                String tag = UUID.randomUUID().toString();
                var auth = app.getBean(AuthService.class);
                String password = UUID.randomUUID().toString();
                var customer = auth.registerUser("smoke-" + tag + "@example.invalid", password, "Rollback test");
                check(auth.authenticateUser(customer.getEmail(), password).getId().equals(customer.getId()), "registration and password login on PostgreSQL");
                var accountService = app.getBean(com.shopflow.backend.service.AccountService.class);
                var customerAuthentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        customer.getEmail(), null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));
                var profile = accountService.updateProfile(customerAuthentication, "Updated rollback profile", "0901234567");
                check(profile.phone().equals("0901234567") && profile.role().equals("CUSTOMER"), "self profile update preserves role");
                var employee = auth.createEmployee("staff-" + tag + "@example.invalid", password, "Rollback staff", "SALES_STAFF");
                check(accountService.employees(0, 10, tag).items().stream().anyMatch(u -> u.userId().equals(employee.getId())), "owner employee search includes sales staff");
                check(!accountService.updateEmployee(employee.getId(), "Rollback warehouse", null, "WAREHOUSE", false).active(), "employee role update and account locking");
                var category = catalog.createCategory(new CategoryRequest("Smoke " + tag, "smoke-" + tag, null));
                var product = catalog.createProduct(new ProductCreateRequest(category.id(), "AUDIT-" + tag,
                        "Smoke " + tag, null, BigDecimal.ZERO, BigDecimal.valueOf(25000)));
                long variant = product.productVariants().getFirst().id();
                var receipt = receiving.receive(new ReceivingRequest(variant, 10, "Rollback test", null));
                check(receipt.availableStock() == 10, "product creation and stock receiving");
                check(catalog.listProducts(0, 10, null, product.sku(), null, null, null, null).totalItems() == 1, "catalog search by SKU");
                var emptyPage = catalog.listProducts(1000, 10, null, product.sku(), null, null, null, null);
                check(emptyPage.items().isEmpty() && emptyPage.totalItems() == 1, "out-of-range pagination preserves total");
                var order = orders.create(new CreateOrderRequest(
                        new Customer("Smoke " + tag, null, "0900000000"),
                        new ShippingAddress("Smoke", "0900000000", "Rollback address", null, "Test city"),
                        "CARD", List.of(new OrderLineRequest(product.id(), 2))), customer.getId());
                check(orders.listMine(customer.getId(), 0, 10, null).totalItems() == 1
                        && orders.listMine(Long.MAX_VALUE, 0, 10, null).totalItems() == 0, "purchase history is scoped to customer ID");
                check(orders.getMine(order.id(), customer.getId()).id().equals(order.id()), "customer can read their own order detail");
                check(app.getBean(ReceivingRepository.class).get(receipt.id()).availableStock() == 8,
                        "receiving availability subtracts active checkout holds");
                check(payments.process(order.orderRef(), new PaymentRequest("SUCCESS", null)).status().equals("SUCCESS"), "payment settles stock hold");
                check(orders.list(0, 10, tag).totalItems() == 1 && orders.list(0, 10, "SF-" + order.id()).totalItems() == 1,
                        "order search uses server-side filtering");
                var dashboard = app.getBean(com.modernshopflow.catalog.dashboard.DashboardService.class).summary("7d");
                check(dashboard.dailyRevenue().size() == 7 && dashboard.dailyRevenue().stream().mapToLong(day -> day.revenue()).sum() == dashboard.revenue(),
                        "dashboard daily revenue matches paid totals");
                deliveries.advance(order.id(), "PREPARING");
                deliveries.advance(order.id(), "SHIPPED");
                check(deliveries.advance(order.id(), "DELIVERED").deliveryStatus().equals("DELIVERED"), "full delivery lifecycle from NONE");
                var eligible = returns.listReturnableOrders().stream().filter(o -> o.orderId() == order.id()).findFirst().orElseThrow();
                var request = returns.create(new CreateReturnRequest(order.id(), "Rollback test",
                        List.of(new ReturnItemRequest(eligible.items().getFirst().orderItemId(), 2))));
                returns.update(request.id(), "APPROVED", true, Set.of("ROLE_SHOP_OWNER"));
                check(returns.update(request.id(), "RESTOCKED", null, Set.of("ROLE_WAREHOUSE")).status().equals("RESTOCKED"),
                        "owner approval and warehouse return restocking");
                check(inventory.list().stream().filter(i -> i.variantId() == variant).findFirst().orElseThrow().onHandStock() == 10,
                        "inventory restored after returned delivery");
                inventory.updateThreshold(product.id(), new ThresholdRequest(3));
                check(catalog.getProduct(product.id()).productVariants().getFirst().availableQuantity() == 10, "product detail and inventory threshold update");
            });
            var after = tables.stream().map(t -> jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class)).toList();
            check(before.equals(after), "all business test rows rolled back; existing row counts unchanged");
        }
    }
}
