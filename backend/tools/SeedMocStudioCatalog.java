import com.modernshopflow.catalog.CatalogApplication;
import com.modernshopflow.catalog.api.CategoryRequest;
import com.modernshopflow.catalog.api.ProductCreateRequest;
import com.modernshopflow.catalog.inventory.InventoryDtos.ThresholdRequest;
import com.modernshopflow.catalog.inventory.InventoryService;
import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingRequest;
import com.modernshopflow.catalog.receiving.ReceivingService;
import com.modernshopflow.catalog.service.CatalogService;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Explicit, atomic demo-data import. Does not run on application startup. */
class SeedMocStudioCatalog {
    public record Category(String name, String slug, String description) {}
    public record Product(String sku, String categorySlug, String name, long costPrice, long listPrice,
            int quantity, int lowStockThreshold, String description, String usageInstructions, List<String> imageUrls) {}
    public record Dataset(String dataset, List<Category> categories, List<Product> products) {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void main(String[] args) throws Exception {
        check(args.length <= 1 && (args.length == 0 || Set.of("--apply", "--dry-run").contains(args[0])),
                "Usage (from backend): java -cp <classpath> tools/SeedMocStudioCatalog.java [--dry-run|--apply]");
        boolean apply = args.length == 1 && args[0].equals("--apply");
        byte[] bytes = Files.readAllBytes(Path.of("../db/seeds/moc-studio-catalog.json"));
        Dataset data = new ObjectMapper().readValue(bytes, Dataset.class);
        check(data.dataset() != null && data.dataset().matches("moc-studio-catalog-v[0-9]+"), "Invalid dataset identifier");
        check(data.categories() != null && !data.categories().isEmpty() && data.products() != null && !data.products().isEmpty(), "Dataset is empty");
        Set<String> slugs = new HashSet<>(), skus = new HashSet<>();
        for (var category : data.categories()) check(slugs.add(category.slug()), "Duplicate category slug");
        for (var product : data.products()) {
            check(product.sku() != null && product.sku().matches("MOC-[A-Z]+-[0-9]{3}") && skus.add(product.sku()), "Invalid/duplicate seed SKU");
            check(slugs.contains(product.categorySlug()), "Unknown seed category");
            check(product.costPrice() > 0 && product.listPrice() > product.costPrice() && product.listPrice() <= 9999999999L, "Invalid seed prices");
            check(product.quantity() > 0 && product.quantity() <= 1000 && product.lowStockThreshold() >= 0, "Invalid seed inventory");
            check(product.description() != null && product.description().contains("Dữ liệu mẫu"), "Demo disclosure is required");
            check(product.usageInstructions() != null && !product.usageInstructions().isBlank(), "Usage instructions required");
            check(product.imageUrls() != null && !product.imageUrls().isEmpty(), "Product images required");
            for (String image : product.imageUrls()) {
                var uri = java.net.URI.create(image);
                check("https".equals(uri.getScheme()) && "images.unsplash.com".equals(uri.getHost()) && uri.getUserInfo() == null, "Unexpected demo image URL");
            }
        }
        System.out.println("Dataset: " + data.dataset() + ", SHA-256: " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        try (var app = SpringApplication.run(CatalogApplication.class,
                "--server.port=0", "--app.schema.initializer.enabled=false", "--app.bootstrap.owner.email=",
                "--app.bootstrap.warehouse.email=", "--debug=false", "--logging.level.root=WARN",
                "--spring.main.banner-mode=off", "--logging.level.org.springframework.boot.security.autoconfigure=ERROR")) {
            var jdbc = app.getBean(JdbcTemplate.class);
            check(jdbc.queryForObject("SELECT COUNT(*) FROM schema_migrations WHERE script_name='enhance_storefront_and_receiving.sql'", Long.class) == 1,
                    "Apply the storefront migration before seeding");
            List<String> tables = List.of("categories", "products", "product_variants", "product_images", "inventory_items",
                    "receiving_records", "stock_movements", "orders", "order_items", "product_reviews", "users");
            var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            // Compare within one snapshot: live registrations/orders must not look like seed writes.
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            int[] inserted = {0}, skipped = {0}, categoriesCreated = {0};
            long[] receivedValue = {0}, receivedQuantity = {0};
            List<Long> createdProductIds = new ArrayList<>(), createdCategoryIds = new ArrayList<>(), createdReceiptIds = new ArrayList<>();
            transaction.executeWithoutResult(status -> {
                if (!apply) status.setRollbackOnly();
                jdbc.execute("SELECT pg_advisory_xact_lock(79240310021)");
                var before = counts(jdbc, tables);
                var catalog = app.getBean(CatalogService.class);
                var receiving = app.getBean(ReceivingService.class);
                var inventory = app.getBean(InventoryService.class);
                var validator = app.getBean(Validator.class);
                Map<String, Long> categoryIds = new HashMap<>();
                for (var category : data.categories()) {
                    var existing = jdbc.queryForList("SELECT id,is_deleted FROM categories WHERE slug=?", category.slug());
                    if (existing.isEmpty()) {
                        var input = new CategoryRequest(category.name(), category.slug(), category.description());
                        check(validator.validate(input).isEmpty(), "Invalid category input");
                        long categoryId = catalog.createCategory(input).id();
                        categoryIds.put(category.slug(), categoryId);
                        createdCategoryIds.add(categoryId);
                        categoriesCreated[0]++;
                    } else {
                        check(!Boolean.TRUE.equals(existing.getFirst().get("is_deleted")), "Seed category is archived; no rows were changed");
                        categoryIds.put(category.slug(), ((Number) existing.getFirst().get("id")).longValue());
                    }
                }
                for (var product : data.products()) {
                    if (jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE UPPER(sku)=?", Long.class, product.sku()) > 0) {
                        skipped[0]++;
                        continue; // Preserve all existing content, stock and prices, including archived rows.
                    }
                    var input = new ProductCreateRequest(categoryIds.get(product.categorySlug()), product.sku(), product.name(),
                            product.description(), BigDecimal.valueOf(product.costPrice()), BigDecimal.valueOf(product.listPrice()),
                            product.usageInstructions(), product.imageUrls());
                    check(validator.validate(input).isEmpty(), "Invalid product input: " + product.sku());
                    var created = catalog.createProduct(input);
                    createdProductIds.add(created.id());
                    long variant = created.productVariants().getFirst().id();
                    var receipt = receiving.receive(new ReceivingRequest(variant, product.quantity(), "Mộc Studio · dữ liệu mẫu",
                            data.dataset() + " | " + product.sku() + " | Tồn đầu kỳ minh họa đồ án", BigDecimal.valueOf(product.costPrice())));
                    createdReceiptIds.add(receipt.id());
                    inventory.updateThreshold(created.id(), new ThresholdRequest(product.lowStockThreshold()));
                    var saved = catalog.getProduct(created.id());
                    check(saved.name().equals(product.name()) && saved.description().equals(product.description())
                            && saved.usageInstructions().equals(product.usageInstructions())
                            && saved.listPrice() == product.listPrice() && saved.costPrice() == product.costPrice()
                            && saved.productImages().size() == product.imageUrls().size()
                            && saved.productVariants().getFirst().availableQuantity() == product.quantity(), "Saved product mismatch: " + product.sku());
                    inserted[0]++;
                    receivedValue[0] += product.costPrice() * product.quantity();
                    receivedQuantity[0] += product.quantity();
                    System.out.println((apply ? "ADDED: " : "DRY RUN: ") + product.sku() + " | " + product.name());
                }
                // Seeding a catalog must not manufacture customers, orders or reviews.
                for (String table : List.of("orders", "order_items", "product_reviews", "users")) {
                    check(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class).equals(before.get(tables.indexOf(table))),
                            "Unrelated business data changed: " + table);
                }
                var after = counts(jdbc, tables);
                for (String table : List.of("products", "product_variants", "inventory_items", "receiving_records", "stock_movements")) {
                    int index = tables.indexOf(table);
                    check(after.get(index) - before.get(index) == inserted[0], "Unexpected row count for " + table);
                }
            });
            if (!apply) {
                check(countIds(jdbc, "products", createdProductIds) == 0
                        && countIds(jdbc, "categories", createdCategoryIds) == 0
                        && countIds(jdbc, "receiving_records", createdReceiptIds) == 0, "Dry run left seed rows behind");
            } else {
                check(countIds(jdbc, "products", createdProductIds) == inserted[0]
                        && countIds(jdbc, "receiving_records", createdReceiptIds) == inserted[0], "Committed seed rows not found");
            }
            System.out.printf(Locale.ROOT, "%s: categories=%d, products=%d, skipped=%d, units=%d, receipt_value_vnd=%d%n",
                    apply ? "COMMITTED" : "ROLLED BACK", categoriesCreated[0], inserted[0], skipped[0], receivedQuantity[0], receivedValue[0]);
        }
    }

    private static List<Long> counts(JdbcTemplate jdbc, List<String> tables) {
        return tables.stream().map(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).toList();
    }

    private static long countIds(JdbcTemplate jdbc, String table, List<Long> ids) {
        if (ids.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id IN (" + placeholders + ")", Long.class, ids.toArray());
    }
}
