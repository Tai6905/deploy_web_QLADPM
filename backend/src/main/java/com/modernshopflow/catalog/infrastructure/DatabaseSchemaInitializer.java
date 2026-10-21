package com.modernshopflow.catalog.infrastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/** Applies the ordered schema scripts whose filenames contain only their purpose. */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.schema.initializer.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final List<String> SCHEMA_SCRIPTS = List.of(
            "create_users_auth_and_addresses.sql",
            "create_catalog_tables.sql",
            "create_voucher_tables.sql",
            "create_cart_and_inventory_tables.sql",
            "create_catalog_api_indexes.sql",
            "create_orders_and_payments.sql",
            "create_delivery_status_history.sql",
            "create_receiving_records_and_stock_movements.sql",
            "create_customer_returns.sql",
            "enhance_storefront_and_receiving.sql",
            "add_checkout_addresses_and_vouchers.sql",
            "add_instrument_specifications.sql");

    private final DataSource dataSource;

    public DatabaseSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureSchemaMigrationsTable(connection);
                importExistingFlywayHistory(connection);
                applyPendingScripts(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void ensureSchemaMigrationsTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        script_name VARCHAR(255) PRIMARY KEY,
                        applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private void importExistingFlywayHistory(Connection connection) throws SQLException {
        if (hasAppliedScripts(connection) || !tableExists(connection, "flyway_schema_history")) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT script FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String oldScriptName = resultSet.getString("script");
                    String scriptName = normalizeLegacyScriptName(oldScriptName);
                    if (SCHEMA_SCRIPTS.contains(scriptName)) {
                        recordAppliedScript(connection, scriptName);
                    }
                }
            }
        }
    }

    private String normalizeLegacyScriptName(String scriptName) {
        int descriptionSeparator = scriptName.indexOf("__");
        if (descriptionSeparator >= 0) {
            return scriptName.substring(descriptionSeparator + 2);
        }
        return scriptName.startsWith("V") ? scriptName.substring(1) : scriptName;
    }

    private void applyPendingScripts(Connection connection) throws Exception {
        for (String scriptName : SCHEMA_SCRIPTS) {
            if (isApplied(connection, scriptName)) {
                continue;
            }
            ScriptUtils.executeSqlScript(
                    connection, new ClassPathResource("db/migration/" + scriptName));
            recordAppliedScript(connection, scriptName);
        }
    }

    private boolean hasAppliedScripts(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM schema_migrations)")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean isApplied(Connection connection, String scriptName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM schema_migrations WHERE script_name = ?)")) {
            statement.setString(1, scriptName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private void recordAppliedScript(Connection connection, String scriptName) throws SQLException {
        if (isApplied(connection, scriptName)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_migrations(script_name) VALUES (?)")) {
            statement.setString(1, scriptName);
            statement.executeUpdate();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = ?
                )
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }
}
