package com.shopflow.backend;

import static org.junit.jupiter.api.Assertions.*;
import com.modernshopflow.catalog.CatalogApplication;
import com.shopflow.backend.service.AuthService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = CatalogApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.url=jdbc:h2:mem:auth-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "debug=false"})
class AuthHttpIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired AuthService auth;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired com.shopflow.backend.repository.UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    static final java.util.concurrent.atomic.AtomicLong orderIds = new java.util.concurrent.atomic.AtomicLong(1000);
    final String password = "Regression-test-2026!";

    @BeforeEach void historySchema() {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("account-history-schema.sql")).execute(jdbc.getDataSource());
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("storefront-schema.sql")).execute(jdbc.getDataSource());
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("checkout-schema.sql")).execute(jdbc.getDataSource());
    }
    com.shopflow.backend.entity.User user(String role) {
        var result = auth.registerUser(UUID.randomUUID() + "@example.invalid", password, "Regression " + role);
        result.setRole(role);
        return users.save(result);
    }
    long seedOrder(long customerId) {
        long id = orderIds.incrementAndGet();
        jdbc.update("INSERT INTO orders(id, order_ref, customer_id, customer_name, receiver_name, receiver_phone, address_line, city) VALUES (?, ?, ?, 'Test Customer', 'Receiver', '0900000000', 'Private address', 'Test city')",
                id, UUID.randomUUID(), customerId);
        jdbc.update("INSERT INTO order_items(order_id, product_id, variant_id, product_name, unit_price, quantity) VALUES (?, 1, 1, 'Regression Cup', 25000, 2)", id);
        jdbc.update("INSERT INTO payment_attempts(order_id, status) VALUES (?, 'SUCCESS')", id);
        return id;
    }

    HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    HttpResponse<String> request(HttpClient client, String path, String method, String body, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json").header("Content-Type", "application/json");
        if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    String csrf(HttpClient client) throws Exception {
        var response = request(client, "/api/auth/session", "GET", null, null);
        assertEquals(200, response.statusCode());
        var matcher = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(response.body());
        assertTrue(matcher.find(), response.body());
        return matcher.group(1);
    }
    void login(HttpClient client, String email) throws Exception {
        var response = request(client, "/api/auth/login", "POST", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        assertEquals(200, response.statusCode(), response.body());
    }

    long checkoutProduct() {
        String tag=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO categories(name,slug) VALUES('Checkout',?)",tag);
        long category=jdbc.queryForObject("SELECT id FROM categories WHERE slug=?",Long.class,tag);
        jdbc.update("INSERT INTO products(category_id,sku,name,slug,list_price) VALUES(?,?,'Checkout cup',?,25000)",category,tag,tag);
        long product=jdbc.queryForObject("SELECT id FROM products WHERE sku=?",Long.class,tag);
        jdbc.update("INSERT INTO product_variants(product_id) VALUES(?)",product);
        long variant=jdbc.queryForObject("SELECT id FROM product_variants WHERE product_id=?",Long.class,product);
        jdbc.update("INSERT INTO inventory_items(variant_id,quantity) VALUES(?,10)",variant);
        return product;
    }
    String checkoutVoucher(String type,int value,int maximum,int minimum) {
        String code="TEST-"+UUID.randomUUID();
        jdbc.update("INSERT INTO vouchers(code,title,discount_type,discount_value,max_discount_amount,min_order_value,max_usages_total,max_usages_per_user,valid_from,valid_to) VALUES(?,'Test offer',?,?,?,?,10,1,?,?)",code,type,value,maximum,minimum,java.time.OffsetDateTime.now().minusDays(1),java.time.OffsetDateTime.now().plusDays(1));
        return code;
    }
    String checkoutBody(long product,String code,String key,String method,long expected) throws Exception {
        return json.writeValueAsString(java.util.Map.of("shippingAddress",java.util.Map.of("receiverName","Test receiver","phone","0901234567","addressLine","12 Test street","district","Test district","city","Test city"),"paymentMethod",method,"items",java.util.List.of(java.util.Map.of("productId",product,"quantity",2)),"voucherCode",code,"checkoutKey",key,"saveAddress",true,"makeDefault",true,"expectedTotal",expected));
    }
    @Test void savedAddressesArePrivateAndHaveOneDefault() throws Exception {
        var first=user("CUSTOMER");var second=user("CUSTOMER");var a=client();var b=client();login(a,first.getEmail());login(b,second.getEmail());
        String input="{\"shippingAddress\":{\"receiverName\":\"Receiver\",\"phone\":\"0901234567\",\"addressLine\":\"12 Test street\",\"city\":\"Test city\"},\"makeDefault\":true}";
        var added=request(a,"/api/account/addresses","POST",input,csrf(a));assertEquals(200,added.statusCode(),added.body());
        long id=json.readTree(added.body()).get("id").asLong();
        assertEquals("[]",request(b,"/api/account/addresses","GET",null,null).body());
        assertEquals(404,request(b,"/api/account/addresses/"+id,"PUT",input,csrf(b)).statusCode());
        assertEquals(404,request(b,"/api/account/addresses/"+id,"DELETE",null,csrf(b)).statusCode());
        var newer=request(a,"/api/account/addresses","POST",input.replace("12 Test street","34 Other street"),csrf(a));assertEquals(200,newer.statusCode());
        long next=json.readTree(newer.body()).get("id").asLong();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id=? AND is_default=TRUE",Integer.class,first.getId()));
        assertEquals(200,request(a,"/api/account/addresses/"+next,"DELETE",null,csrf(a)).statusCode());
        assertTrue(jdbc.queryForObject("SELECT is_default FROM user_addresses WHERE id=?",Boolean.class,id));
    }
    @Test void checkoutValidatesVoucherPreventsDuplicatesAndCollectsCodOnlyAtDelivery() throws Exception {
        var customer=user("CUSTOMER");var browser=client();login(browser,customer.getEmail());
        long product=checkoutProduct();String voucher=checkoutVoucher("PERCENTAGE",10,3000,40000),key=UUID.randomUUID().toString();
        String quote=json.writeValueAsString(java.util.Map.of("items",java.util.List.of(java.util.Map.of("productId",product,"quantity",2)),"voucherCode",voucher));
        var price=request(browser,"/api/checkout/quote","POST",quote,csrf(browser));assertEquals(200,price.statusCode(),price.body());assertTrue(price.body().contains("\"totalAmount\":47000"));
        assertEquals(409,request(browser,"/api/checkout/orders","POST",checkoutBody(product,voucher,key,"COD",1),csrf(browser)).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE checkout_key=?",Integer.class,UUID.fromString(key)));
        String body=checkoutBody(product,voucher,key,"COD",47000);
        var placed=request(browser,"/api/checkout/orders","POST",body,csrf(browser));assertEquals(200,placed.statusCode(),placed.body());
        var result=json.readTree(placed.body());long order=result.get("id").asLong();String reference=result.get("orderRef").asText();
        assertEquals("PENDING_PAYMENT",result.get("status").asText());
        assertEquals(placed.body(),request(browser,"/api/checkout/orders","POST",body,csrf(browser)).body());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id=?",Integer.class,customer.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE order_id=?",Integer.class,order));
        assertEquals(2,jdbc.queryForObject("SELECT reserved_quantity FROM inventory_items WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id=?)",Integer.class,product));
        assertEquals(409,request(browser,"/orders/"+reference+"/payments","POST","{\"result\":\"SUCCESS\"}",csrf(browser)).statusCode());
        assertEquals(409,request(browser,"/api/checkout/orders","POST",body.replace("12 Test street","Different address"),csrf(browser)).statusCode());
        assertEquals(400,request(browser,"/api/checkout/orders","POST",checkoutBody(product,voucher,UUID.randomUUID().toString(),"COD",47000),csrf(browser)).statusCode());
        var warehouse=user("WAREHOUSE");var staff=client();login(staff,warehouse.getEmail());
        for(String state:java.util.List.of("PREPARING","SHIPPED","DELIVERED")) {var delivery=request(staff,"/orders/"+order+"/delivery","PATCH","{\"toStatus\":\""+state+"\"}",csrf(staff));assertEquals(200,delivery.statusCode(),delivery.body());}
        assertEquals("PAID",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order));
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM payment_attempts WHERE order_id=?",String.class,order));
        assertEquals(8,jdbc.queryForObject("SELECT quantity FROM inventory_items WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id=?)",Integer.class,product));
        assertEquals(409,request(staff,"/orders/"+order+"/delivery","PATCH","{\"toStatus\":\"DELIVERED\"}",csrf(staff)).statusCode());
    }
    @Test void failedTransferReleasesVoucherButKeepsOrderDiscountSnapshot() throws Exception {
        var customer=user("CUSTOMER");var browser=client();login(browser,customer.getEmail());
        long product=checkoutProduct();String voucher=checkoutVoucher("FIXED_AMOUNT",1000,1000,0);
        var placed=request(browser,"/api/checkout/orders","POST",checkoutBody(product,voucher,UUID.randomUUID().toString(),"BANK_TRANSFER",49000),csrf(browser));assertEquals(200,placed.statusCode(),placed.body());
        var result=json.readTree(placed.body());long order=result.get("id").asLong();
        var failed=request(browser,"/orders/"+result.get("orderRef").asText()+"/payments","POST","{\"result\":\"FAILED\",\"failureReason\":\"Simulation failed\"}",csrf(browser));assertEquals(200,failed.statusCode(),failed.body());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE order_id=?",Integer.class,order));
        assertEquals(0,jdbc.queryForObject("SELECT current_usage_count FROM vouchers WHERE code=?",Integer.class,voucher));
        assertEquals(1000,jdbc.queryForObject("SELECT discount_amount FROM orders WHERE id=?",Integer.class,order));
        assertEquals(200,request(browser,"/api/checkout/orders","POST",checkoutBody(product,voucher,UUID.randomUUID().toString(),"CARD",49000),csrf(browser)).statusCode());
    }
    @Test void registrationLoginSessionAndRawCsrfLogout() throws Exception {
        var client = client();
        String email = UUID.randomUUID() + "@example.invalid";
        var registered = request(client, "/api/auth/register", "POST", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"Regression\"}", null);
        assertEquals(201, registered.statusCode());
        login(client, email);
        assertTrue(request(client, "/api/auth/session", "GET", null, null).body().contains("\"authenticated\":true"));
        assertEquals(403, request(client, "/api/auth/logout", "POST", null, null).statusCode());
        assertEquals(204, request(client, "/api/auth/logout", "POST", null, csrf(client)).statusCode());
        assertTrue(request(client, "/api/auth/session", "GET", null, null).body().contains("\"authenticated\":false"));
    }
    @Test void secondLoginExpiresFirstSession() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        auth.registerUser(email, password, "Concurrent test");
        var first = client(); var second = client();
        login(first, email); login(second, email);
        assertEquals(401, request(first, "/api/auth/session", "GET", null, null).statusCode());
        assertTrue(request(second, "/api/auth/session", "GET", null, null).body().contains("\"authenticated\":true"));
    }
    @Test void warehouseCannotReadOwnerOnlyEligibleOrders() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        auth.createWarehouseEmployee(email, password, "Warehouse test");
        var client = client(); login(client, email);
        assertEquals(403, request(client, "/returns/orders", "GET", null, null).statusCode());
        assertEquals(403, request(client, "/api/employees", "POST", "{}", csrf(client)).statusCode());
    }
    @Test void corsAllowsConfiguredOriginAndRejectsUnknownOrigin() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "https://deploy-web-qladpm.vercel.app", "https://untrusted.invalid"}) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/products"))
                    .header("Origin", origin).header("Access-Control-Request-Method", "GET").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            var response = client().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(origin.equals("https://untrusted.invalid") ? 403 : 200, response.statusCode());
            if (response.statusCode() == 200) assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        }
    }
    @Test void registrationAllowsFrontendOriginAndBlocksAnUntrustedOrigin() throws Exception {
        String origin = "https://deploy-web-qladpm.vercel.app";
        var endpoint = URI.create("http://localhost:" + port + "/api/auth/register");
        var preflight = HttpRequest.newBuilder(endpoint).header("Origin", origin)
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var allowed = client().send(preflight, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, allowed.statusCode(), allowed.body());
        assertEquals(origin, allowed.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("true", allowed.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
        String email = UUID.randomUUID() + "@example.invalid";
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"CORS regression\"}";
        for (String source : new String[]{"https://untrusted.invalid", origin}) {
            var request = HttpRequest.newBuilder(endpoint).header("Origin", source).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(source.equals(origin) ? 201 : 403, response.statusCode(), response.body());
            if (!source.equals(origin)) assertTrue(users.findByEmail(email).isEmpty());
            else assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        }
    }
    @Test void swaggerAndAnonymousSessionArePublic() throws Exception {
        assertEquals(200, request(client(), "/swagger-ui/index.html", "GET", null, null).statusCode());
        assertEquals(401, request(client(), "/orders", "GET", null, null).statusCode());
        assertEquals(200, request(client(), "/api/auth/session", "GET", null, null).statusCode());
    }
    @Test void storefrontSupportsGalleryReviewsAndServerFilters() throws Exception {
        var owner = user("SHOP_OWNER"); var ownerClient = client(); login(ownerClient, owner.getEmail());
        String tag = UUID.randomUUID().toString();
        var category = request(ownerClient, "/categories", "POST", "{\"name\":\"Test\",\"slug\":\"" + tag + "\"}", csrf(ownerClient));
        assertEquals(201, category.statusCode(), category.body());
        long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug=?", Long.class, tag);
        String content = "{\"categoryId\":" + categoryId + ",\"sku\":\"" + tag + "\",\"name\":\"Bình mộc " + tag + "\",\"description\":\"Product introduction\",\"costPrice\":10000,\"listPrice\":25000,\"usageInstructions\":\"Wash by hand\",\"imageUrls\":[\"https://example.invalid/one.jpg\",\"https://example.invalid/two.jpg\"]}";
        var created = request(ownerClient, "/products", "POST", content, csrf(ownerClient));
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.body().contains("Wash by hand")); assertTrue(created.body().contains("two.jpg"));
        assertFalse(created.body().contains("costPrice"));
        long productId = jdbc.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, tag.toUpperCase());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id=?", Integer.class, productId));
        var customer = user("CUSTOMER"); var customerClient = client(); login(customerClient, customer.getEmail());
        String contentPath = "/products/" + productId + "/content";
        String replacement = "{\"description\":\"Updated description\",\"usageInstructions\":\"Dry after washing\",\"imageUrls\":[\"https://example.invalid/replaced.jpg\"]}";
        assertEquals(403, request(customerClient, contentPath, "PUT", replacement, csrf(customerClient)).statusCode());
        var edited = request(ownerClient, contentPath, "PUT", replacement, csrf(ownerClient));
        assertEquals(200, edited.statusCode(), edited.body());
        assertTrue(edited.body().contains("Dry after washing"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id=?", Integer.class, productId));
        assertEquals(400, request(ownerClient, contentPath, "PUT", replacement.replace("https://example.invalid/replaced.jpg", "javascript:alert(1)"), csrf(ownerClient)).statusCode());
        assertEquals("https://example.invalid/replaced.jpg", jdbc.queryForObject("SELECT image_url FROM product_images WHERE product_id=?", String.class, productId));
        String reviewPath = "/products/" + productId + "/reviews";
        var anonymous = client();
        assertEquals(401, request(anonymous, reviewPath, "PUT", "{\"rating\":5,\"comment\":\"Nice\"}", csrf(anonymous)).statusCode());
        assertEquals(403, request(customerClient, reviewPath, "PUT", "{\"rating\":5,\"comment\":\"Nice\"}", csrf(customerClient)).statusCode());
        long orderId = seedOrder(customer.getId());
        jdbc.update("UPDATE order_items SET product_id=? WHERE order_id=?", productId, orderId);
        jdbc.update("UPDATE orders SET delivery_status='DELIVERED' WHERE id=?", orderId);
        assertEquals(200, request(customerClient, reviewPath, "PUT", "{\"rating\":4,\"comment\":\"Good\"}", csrf(customerClient)).statusCode());
        assertEquals(200, request(customerClient, reviewPath, "PUT", "{\"rating\":5,\"comment\":\"Updated review\"}", csrf(customerClient)).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE product_id=?", Integer.class, productId));
        var reviews = request(client(), reviewPath, "GET", null, null);
        assertEquals(200, reviews.statusCode()); assertTrue(reviews.body().contains("Updated review")); assertFalse(reviews.body().contains(customer.getEmail()));
        String query = java.net.URLEncoder.encode("binh moc " + tag, java.nio.charset.StandardCharsets.UTF_8);
        var found = request(client(), "/products?q=" + query + "&rating=4&minPrice=20000&maxPrice=30000&sort=rating", "GET", null, null);
        assertEquals(200, found.statusCode(), found.body()); assertTrue(found.body().contains("\"totalItems\":1"), found.body());
        var unavailable = request(client(), "/products?q=" + query + "&inStock=true", "GET", null, null);
        assertEquals(200, unavailable.statusCode(), unavailable.body()); assertTrue(unavailable.body().contains("\"totalItems\":0"));
        jdbc.update("UPDATE inventory_items SET quantity=4 WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id=?)", productId);
        var available = request(client(), "/products?q=" + query + "&inStock=true", "GET", null, null);
        assertEquals(200, available.statusCode(), available.body()); assertTrue(available.body().contains("\"totalItems\":1"));
    }
    @Test void productJsonNeverExposesCostPrice() throws Exception {
        var now = java.time.OffsetDateTime.now();
        var product = new com.modernshopflow.catalog.api.ProductDetailResponse(
                1L, 1L, "Test", "TEST", "Product", "product", null, 12345, 25000, now, now, java.util.List.of(), java.util.List.of());
        var body = json.writeValueAsString(product);
        assertFalse(body.contains("costPrice"), body);
        assertTrue(body.contains("\"listPrice\":25000"), body);
    }

    @Test void ownerCreatesBothEmployeeRolesAndCannotGrantOwnerThroughEmployeeApi() throws Exception {
        var owner = user("SHOP_OWNER"); var ownerClient = client(); login(ownerClient, owner.getEmail());
        for (String role : new String[]{"SALES_STAFF", "WAREHOUSE"}) {
            String email = UUID.randomUUID() + "@example.invalid";
            var created = request(ownerClient, "/api/employees", "POST", json.writeValueAsString(java.util.Map.of(
                    "email", email, "password", password, "displayName", "New staff", "role", role)), csrf(ownerClient));
            assertEquals(201, created.statusCode(), created.body());
            assertEquals(role, users.findByEmail(email).orElseThrow().getRole());
        }
        var denied = request(ownerClient, "/api/employees", "POST", json.writeValueAsString(java.util.Map.of(
                "email", UUID.randomUUID() + "@example.invalid", "password", password, "displayName", "Invalid owner", "role", "SHOP_OWNER")), csrf(ownerClient));
        assertEquals(400, denied.statusCode());
        var list = request(ownerClient, "/api/employees?page=0&size=10", "GET", null, null);
        assertEquals(200, list.statusCode()); assertFalse(list.body().contains("passwordHash"));
    }
    @Test void employeeRolesAreEnforcedAndLockingRevokesTheirSessions() throws Exception {
        var owner = user("SHOP_OWNER"); var staff = user("SALES_STAFF");
        var ownerClient = client(); var staffClient = client();
        login(ownerClient, owner.getEmail()); login(staffClient, staff.getEmail());
        assertEquals(200, request(staffClient, "/orders", "GET", null, null).statusCode());
        assertEquals(403, request(staffClient, "/inventory", "GET", null, null).statusCode());
        assertEquals(403, request(staffClient, "/api/employees", "GET", null, null).statusCode());
        var locked = request(ownerClient, "/api/employees/" + staff.getId(), "PATCH", json.writeValueAsString(java.util.Map.of(
                "displayName", staff.getDisplayName(), "role", "SALES_STAFF", "active", false)), csrf(ownerClient));
        assertEquals(200, locked.statusCode(), locked.body());
        assertEquals(401, request(staffClient, "/api/account", "GET", null, null).statusCode());
        assertEquals(401, request(client(), "/api/auth/login", "POST", json.writeValueAsString(java.util.Map.of(
                "email", staff.getEmail(), "password", password)), null).statusCode());
    }
    @Test void profileCannotEscalateRoleAndPasswordChangeRequiresCurrentPassword() throws Exception {
        var customer = user("CUSTOMER"); var client = client(); login(client, customer.getEmail());
        var updated = request(client, "/api/account", "PUT", "{\"displayName\":\"Updated name\",\"phone\":\"0901234567\",\"role\":\"SHOP_OWNER\"}", csrf(client));
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("CUSTOMER", users.findById(customer.getId()).orElseThrow().getRole());
        assertEquals("0901234567", users.findById(customer.getId()).orElseThrow().getPhone());
        assertFalse(updated.body().contains("password"));
        var wrong = request(client, "/api/account/password", "PUT", "{\"currentPassword\":\"incorrect\",\"newPassword\":\"Updated-password-2026!\"}", csrf(client));
        assertEquals(400, wrong.statusCode());
        var changed = request(client, "/api/account/password", "PUT", json.writeValueAsString(java.util.Map.of(
                "currentPassword", password, "newPassword", "Updated-password-2026!")), csrf(client));
        assertEquals(204, changed.statusCode(), changed.body());
        assertEquals(401, request(client(), "/api/auth/login", "POST", json.writeValueAsString(java.util.Map.of(
                "email", customer.getEmail(), "password", password)), null).statusCode());
        assertEquals(200, request(client(), "/api/auth/login", "POST", json.writeValueAsString(java.util.Map.of(
                "email", customer.getEmail(), "password", "Updated-password-2026!")), null).statusCode());
    }
    @Test void customerHistoryAndTrackingNeverExposeOtherCustomersOrders() throws Exception {
        var first = user("CUSTOMER"); var other = user("CUSTOMER");
        long mine = seedOrder(first.getId()), theirs = seedOrder(other.getId());
        var customer = client(); login(customer, first.getEmail());
        var history = request(customer, "/api/account/orders?page=0&size=10", "GET", null, null);
        assertEquals(200, history.statusCode(), history.body());
        var items = json.readTree(history.body()).get("items");
        assertEquals(1, items.size()); assertEquals(mine, items.get(0).get("id").asLong());
        assertEquals(200, request(customer, "/api/account/orders/" + mine, "GET", null, null).statusCode());
        assertEquals(404, request(customer, "/api/account/orders/" + theirs, "GET", null, null).statusCode());
        assertEquals(404, request(customer, "/api/account/orders/" + theirs + "/returns", "POST",
                "{\"reason\":\"Test\",\"items\":[{\"orderItemId\":1,\"quantity\":1}]}", csrf(customer)).statusCode());
        assertEquals(401, request(client(), "/api/account/orders", "GET", null, null).statusCode());
        UUID reference = jdbc.queryForObject("SELECT order_ref FROM orders WHERE id = ?", UUID.class, theirs);
        assertEquals(404, request(customer, "/orders/reference/" + reference, "GET", null, null).statusCode());
        assertEquals(404, request(client(), "/orders/reference/" + reference, "GET", null, null).statusCode());
    }
}
