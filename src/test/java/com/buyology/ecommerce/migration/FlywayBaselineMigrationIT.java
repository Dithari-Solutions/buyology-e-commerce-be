package com.buyology.ecommerce.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the exact production startup failure we hit ("Found non-empty
 * schema(s) but no schema history table") against a real Postgres, and proves
 * the baseline-on-migrate configuration resolves it without a crash — and is
 * idempotent on a second run.
 */
@Testcontainers
class FlywayBaselineMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load();
    }

    @Test
    void baselineOnMigrate_handlesPreexistingNonEmptySchemaAndIsIdempotent() throws Exception {
        // Simulate a database already populated by Hibernate ddl-auto (no Flyway history table).
        try (Connection c = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE promo_code_usages ("
                    + "id uuid PRIMARY KEY, promo_code_id uuid, user_id uuid, order_id uuid)");
            // Insert two NON-duplicate rows so the V1 dedupe migration runs but removes nothing.
            s.execute("INSERT INTO promo_code_usages VALUES "
                    + "(gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid()),"
                    + "(gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid())");
        }

        // First migrate: must succeed (this is the prod scenario that previously crashed).
        MigrateResult first = flyway().migrate();
        assertTrue(first.success, "Flyway must baseline the existing schema and run migrations without error");

        // Second migrate: no pending work, still succeeds (idempotent).
        MigrateResult second = flyway().migrate();
        assertTrue(second.success);
        assertEquals(0, second.migrationsExecuted, "re-running must not re-apply migrations");

        // The dedupe migration must not have deleted the two distinct rows.
        try (Connection c = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement s = c.createStatement()) {
            var rs = s.executeQuery("SELECT count(*) FROM promo_code_usages");
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }
}
