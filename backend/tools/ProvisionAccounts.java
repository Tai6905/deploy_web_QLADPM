import com.modernshopflow.catalog.CatalogApplication;
import com.shopflow.backend.entity.User;
import com.shopflow.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates only the four explicitly configured accounts; credentials remain in an ignored local file. */
class ProvisionAccounts {
    public static void main(String[] args) throws Exception {
        var setup = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("accounts.local.properties"))) { setup.load(reader); }
        String[][] roles = {{"owner", "SHOP_OWNER"}, {"sales", "SALES_STAFF"}, {"warehouse", "WAREHOUSE"}, {"customer", "CUSTOMER"}};
        for (var definition : roles) {
            String prefix = definition[0];
            String email = setup.getProperty(prefix + ".email", "").trim();
            String password = setup.getProperty(prefix + ".password", "");
            if (!email.contains("@") || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("Set a valid email and password for " + prefix + " in accounts.local.properties");
            }
        }
        try (var app = SpringApplication.run(CatalogApplication.class, "--server.port=0", "--debug=false",
                "--logging.level.root=WARN", "--spring.main.banner-mode=off", "--app.schema.initializer.enabled=false",
                "--app.bootstrap.owner.email=", "--app.bootstrap.warehouse.email=")) {
            var users = app.getBean(UserRepository.class);
            var passwords = app.getBean(PasswordEncoder.class);
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                for (var definition : roles) {
                    String prefix = definition[0], role = definition[1];
                    String email = setup.getProperty(prefix + ".email").trim().toLowerCase(Locale.ROOT);
                    String password = setup.getProperty(prefix + ".password");
                    var existing = users.findByEmail(email);
                    if (existing.isPresent()) {
                        if (!existing.get().getRole().equals(role) || !passwords.matches(password, existing.get().getPasswordHash())) {
                            throw new IllegalStateException("Account already exists with different role or password; preserved: " + email);
                        }
                        System.out.println("Already configured: " + email + " [" + role + "]");
                        continue;
                    }
                    var user = new User();
                    user.setEmail(email); user.setDisplayName(setup.getProperty(prefix + ".display-name", prefix));
                    user.setPasswordHash(passwords.encode(password)); user.setRole(role); user.setActive(true);
                    users.save(user);
                    System.out.println("Prepared account: " + email + " [" + role + "]");
                }
            });
            System.out.println("Account provisioning committed. Existing unrelated accounts were preserved.");
        }
    }
}
