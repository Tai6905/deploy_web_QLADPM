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
        for (String origin : new String[]{"http://localhost:5173", "https://untrusted.invalid"}) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/products"))
                    .header("Origin", origin).header("Access-Control-Request-Method", "GET").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            var response = client().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(origin.contains("localhost") ? 200 : 403, response.statusCode());
            if (response.statusCode() == 200) assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        }
    }
    @Test void swaggerAndAnonymousSessionArePublic() throws Exception {
        assertEquals(200, request(client(), "/swagger-ui/index.html", "GET", null, null).statusCode());
        assertEquals(401, request(client(), "/orders", "GET", null, null).statusCode());
        assertEquals(200, request(client(), "/api/auth/session", "GET", null, null).statusCode());
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
