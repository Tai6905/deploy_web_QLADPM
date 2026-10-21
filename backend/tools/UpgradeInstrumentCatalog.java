import com.modernshopflow.catalog.CatalogApplication;
import com.modernshopflow.catalog.repository.CatalogRepository;
import com.modernshopflow.catalog.service.CatalogService;
import com.modernshopflow.catalog.service.InstrumentSpecifications;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Adds specifications without changing prices, inventory, accounts or order history. */
class UpgradeInstrumentCatalog {
    public record Seed(String sku, Map<String, String> specifications) {}
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !Set.of("--apply", "--dry-run").contains(args[0])))
            throw new IllegalArgumentException("Use --dry-run (default) or --apply from the backend directory.");
        boolean apply = args.length == 1 && args[0].equals("--apply");
        var seeds = new ObjectMapper().readValue(Files.readAllBytes(Path.of("../db/seeds/instrument-specifications.json")), Seed[].class);
        for (var seed : seeds) {
            if (!seed.sku().matches("MOC-(ACOU|ELEC|PIANO|DRUM|VIOL|TRUMP)-00[1-4]")) throw new IllegalArgumentException("Unexpected seed SKU");
            InstrumentSpecifications.validate(seed.specifications());
        }
        try (var app = SpringApplication.run(CatalogApplication.class, "--server.port=0", "--app.schema.initializer.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=none", "--app.bootstrap.owner.email=", "--app.bootstrap.warehouse.email=",
                "--debug=false", "--logging.level.root=WARN", "--spring.main.banner-mode=off",
                "--logging.level.org.springframework.boot.security.autoconfigure=ERROR")) {
            var jdbc = app.getBean(JdbcTemplate.class);
            var repository = app.getBean(CatalogRepository.class);
            var catalog = app.getBean(CatalogService.class);
            var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            int[] saved = {0}, skipped = {0}, missing = {0};
            transaction.executeWithoutResult(status -> {
                if (!apply) status.setRollbackOnly();
                jdbc.execute("SELECT pg_advisory_xact_lock(79240310022)");
                jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/add_instrument_specifications.sql"));
                    return null;
                });
                jdbc.update("INSERT INTO schema_migrations(script_name) SELECT 'add_instrument_specifications.sql' WHERE NOT EXISTS (SELECT 1 FROM schema_migrations WHERE script_name='add_instrument_specifications.sql')");
                for (var seed : seeds) {
                    var rows = jdbc.queryForList("SELECT id,description FROM products WHERE sku=? AND is_deleted=false", seed.sku());
                    if (rows.isEmpty()) { missing[0]++; continue; }
                    var row = rows.getFirst();
                    long id = ((Number) row.get("id")).longValue();
                    if (!String.valueOf(row.get("description")).contains("Dữ liệu mẫu") || !repository.findSpecifications(List.of(id)).isEmpty()) {
                        skipped[0]++; continue;
                    }
                    repository.saveSpecifications(id, seed.specifications());
                    if (!catalog.getProduct(id).specifications().equals(seed.specifications())) throw new IllegalStateException("Specification round-trip failed");
                    saved[0]++;
                }
                for (var type : List.of("guitar", "piano", "drums", "violin", "wind")) {
                    var result = catalog.listProducts(0, 100, null, null, null, null, null, "newest", null, Map.of("instrumentType", type));
                    if (result.items().stream().anyMatch(item -> !type.equals(item.specifications().get("instrumentType"))))
                        throw new IllegalStateException("Instrument type filter failed");
                }
            });
            System.out.println((apply ? "COMMITTED" : "ROLLED BACK") + ": instrument specifications=" + saved[0] + ", existing/custom skipped=" + skipped[0] + ", missing seed SKUs=" + missing[0]);
        }
    }
}
