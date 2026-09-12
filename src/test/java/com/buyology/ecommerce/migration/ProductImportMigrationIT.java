package com.buyology.ecommerce.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves V51 applies against a database that already has a {@code products} table — which is the
 * production state, and the one the fresh-database test cannot reach.
 *
 * <p>{@link FlywayBaselineMigrationIT} migrates an essentially empty schema, so every
 * {@code to_regclass}-guarded block in V51 is skipped there and the {@code ALTER TABLE} statements
 * are never executed. That is exactly how V49 passed its tests and then broke the deploy: Flyway
 * runs before Hibernate's ddl-auto, so a migration is silently a no-op on a fresh database and only
 * does anything on a real one. This test exercises the branch the deploy will actually take.
 *
 * <p>It also pins the re-run: the deploy applies migrations to a database that may already have
 * been migrated, so every statement in V51 has to tolerate being seen twice.
 */
@Testcontainers
class ProductImportMigrationIT {

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
    void v51AddsImportColumnsToAnExistingProductsTableAndIsRepeatable() throws Exception {
        // Stand in for the Hibernate-created table production already has. Only the columns V51
        // touches matter; the guard keys off the table existing at all.
        try (Connection c = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE products ("
                    + "id uuid PRIMARY KEY, sku varchar(255), status varchar(20), "
                    + "stock_quantity integer, availability_status varchar(20))");
            s.execute("INSERT INTO products VALUES "
                    + "(gen_random_uuid(), 'DTAX0001', 'ACTIVE', 3, 'IN_STOCK')");
        }

        MigrateResult first = flyway().migrate();
        assertTrue(first.success, "V51 must apply against a database that already has products");

        try (Connection c = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement s = c.createStatement()) {

            assertTrue(columnExists(s, "products", "needs_fulfilment"), "needs_fulfilment must be added");
            assertTrue(columnExists(s, "products", "import_notes"), "import_notes must be added");
            assertTrue(columnExists(s, "products", "import_job_id"), "import_job_id must be added");

            assertTrue(tableExists(s, "product_import_jobs"));
            assertTrue(tableExists(s, "product_import_rows"));

            // The existing row must keep its stock. A migration that backfilled NULL stock to 0
            // would make every untracked product unbuyable under the new guard, so V51
            // deliberately backfills nothing.
            try (ResultSet rs = s.executeQuery(
                    "SELECT stock_quantity, needs_fulfilment FROM products WHERE sku = 'DTAX0001'")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "existing stock must be left alone");
                assertFalse(rs.getBoolean(2), "existing products are not import drafts");
            }
        }

        // The deploy re-applies migrations against an already-migrated database.
        MigrateResult second = flyway().migrate();
        assertTrue(second.success, "re-running must not fail");
        assertEquals(0, second.migrationsExecuted, "already-applied migrations must not re-run");
    }

    private static boolean columnExists(Statement s, String table, String column) throws Exception {
        try (ResultSet rs = s.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            return rs.next();
        }
    }

    private static boolean tableExists(Statement s, String table) throws Exception {
        try (ResultSet rs = s.executeQuery("SELECT to_regclass('public." + table + "')")) {
            return rs.next() && rs.getString(1) != null;
        }
    }
}
