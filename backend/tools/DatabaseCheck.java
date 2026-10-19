import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

/** Read-only connectivity/schema check. Never prints connection secrets or customer rows. */
class DatabaseCheck {
    public static void main(String[] args) throws Exception {
        var local = new Properties();
        var config = Path.of("application-local.properties");
        if (Files.exists(config)) try (var reader = Files.newBufferedReader(config)) { local.load(reader); }
        String url = local.getProperty("spring.datasource.url", System.getenv("DB_URL"));
        String username = local.getProperty("spring.datasource.username", System.getenv("DB_USERNAME"));
        String password = local.getProperty("spring.datasource.password", "${DB_PASSWORD:}");
        if (password.startsWith("${DB_PASSWORD")) password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Enter the database password in application-local.properties or DB_PASSWORD first.");
        var settings = new Properties();
        settings.setProperty("user", username);
        settings.setProperty("password", password);
        settings.setProperty("connectTimeout", "10");
        settings.setProperty("socketTimeout", "20");
        settings.setProperty("ApplicationName", "ShopFlow-readonly-check");
        try (var connection = DriverManager.getConnection(url, settings)) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            try (var statement = connection.createStatement()) {
                var result = statement.executeQuery("SELECT current_database(), current_user, (SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid())");
                result.next();
                System.out.println("Connected: database=" + result.getString(1) + ", user=" + result.getString(2) + ", SSL=" + result.getBoolean(3));
                result = statement.executeQuery("SELECT table_name, string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' GROUP BY table_name ORDER BY table_name");
                while (result.next()) System.out.println(result.getString(1) + ": " + result.getString(2));
                result = statement.executeQuery("SELECT to_regclass('public.users'), to_regclass('public.schema_migrations')");
                result.next();
                boolean usersExist = result.getString(1) != null;
                boolean migrationsExist = result.getString(2) != null;
                if (usersExist) {
                    result = statement.executeQuery("SELECT role, count(*) FROM users GROUP BY role ORDER BY role");
                    while (result.next()) System.out.println("Accounts " + result.getString(1) + ": " + result.getLong(2));
                }
                if (migrationsExist) {
                    result = statement.executeQuery("SELECT script_name FROM schema_migrations ORDER BY script_name");
                    while (result.next()) System.out.println("Applied migration: " + result.getString(1));
                }
            } finally { connection.rollback(); }
        } catch (java.sql.SQLException exception) {
            System.err.println("Database check failed (SQLState " + exception.getSQLState() + "): " + exception.getMessage());
            System.exit(1);
        }
    }
}
