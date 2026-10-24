package com.buyology.ecommerce.migration;

import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import com.buyology.ecommerce.warranty.domain.WarrantyPreset;
import com.buyology.ecommerce.warranty.repository.WarrantyCardRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V69 against a real Postgres shaped like production before it: the tables it alters or reads
 * already exist (with only the columns that matter here), and Flyway history stops at V68.
 *
 * <p>Covers what the unit tests cannot: that the migration runs and re-runs, that its constraints
 * hold, that the catch-up sweep's native SQL finds exactly the orders still owed cards, and that
 * Hibernate's {@code validate} — production's ddl-auto — accepts the new entities.
 */
@Testcontainers
class WarrantyMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("68")
                .locations("classpath:db/migration")
                .load();
    }

    private static MigrateResult firstRun;

    @BeforeAll
    static void migrateAPreV69Database() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE products (id UUID PRIMARY KEY, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE')");
            s.execute("CREATE TABLE orders (id UUID PRIMARY KEY, status VARCHAR(50) NOT NULL, "
                    + "delivered_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ)");
            s.execute("CREATE TABLE order_items (id UUID PRIMARY KEY, order_id UUID NOT NULL, "
                    + "product_id UUID, quantity INTEGER)");
        }
        firstRun = flyway().migrate();
    }

    @Test
    void runsOnceAndIsANoOpTheSecondTime() {
        assertTrue(firstRun.success);
        assertEquals(1, firstRun.migrationsExecuted, "only V69 is pending after a V68 baseline");
        MigrateResult again = flyway().migrate();
        assertTrue(again.success);
        assertEquals(0, again.migrationsExecuted);
    }

    @Test
    void productsGainBothColumnsWithTheirTypes() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            var rs = s.executeQuery("SELECT column_name, data_type FROM information_schema.columns "
                    + "WHERE table_name = 'products' AND column_name LIKE 'warranty%' ORDER BY column_name");
            List<String> columns = new ArrayList<>();
            while (rs.next()) columns.add(rs.getString(1) + ":" + rs.getString(2));
            assertEquals(List.of("warranty_months:integer", "warranty_preset_id:uuid"), columns);
        }
    }

    @Test
    void lengthsOutsideOneMonthToTenYearsAreRefusedByTheDatabaseToo() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            assertThrows(SQLException.class, () -> s.execute(
                    "INSERT INTO products (id, warranty_months) VALUES (gen_random_uuid(), 0)"));
            assertThrows(SQLException.class, () -> s.execute(
                    "INSERT INTO products (id, warranty_months) VALUES (gen_random_uuid(), 121)"));
            assertThrows(SQLException.class, () -> s.execute("INSERT INTO warranty_presets VALUES "
                    + "(gen_random_uuid(), 'Too long', 121, now(), now())"));
            s.execute("INSERT INTO products (id, warranty_months) VALUES (gen_random_uuid(), 120)");
        }
    }

    @Test
    void presetNamesAreUniqueWhateverTheirCase() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO warranty_presets VALUES (gen_random_uuid(), 'Tablet', 12, now(), now())");
            assertThrows(SQLException.class, () -> s.execute(
                    "INSERT INTO warranty_presets VALUES (gen_random_uuid(), 'tablet', 6, now(), now())"));
        }
    }

    @Test
    void deletingAPresetKeepsTheProductsMonths() throws Exception {
        UUID preset = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO warranty_presets VALUES ('" + preset + "', 'Phone', 12, now(), now())");
            s.execute("INSERT INTO products (id, warranty_months, warranty_preset_id) VALUES ('"
                    + product + "', 12, '" + preset + "')");
            s.execute("DELETE FROM warranty_presets WHERE id = '" + preset + "'");
            var rs = s.executeQuery("SELECT warranty_months, warranty_preset_id FROM products WHERE id = '" + product + "'");
            assertTrue(rs.next());
            assertEquals(12, rs.getInt(1));
            assertNull(rs.getObject(2));
        }
    }

    @Test
    void aSerialAndAUnitCanEachOnlyBeIssuedOnce() throws Exception {
        UUID item = UUID.randomUUID();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(card("DTH-AAAA-AAAA-AAAA", item, 1, "ACTIVE"));
            assertThrows(SQLException.class, () -> s.execute(card("DTH-AAAA-AAAA-AAAA", UUID.randomUUID(), 1, "ACTIVE")),
                    "serials are unique");
            assertThrows(SQLException.class, () -> s.execute(card("DTH-BBBB-BBBB-BBBB", item, 1, "ACTIVE")),
                    "a unit gets one card, which is what makes issuing idempotent");
            assertThrows(SQLException.class, () -> s.execute(card("DTH-CCCC-CCCC-CCCC", item, 2, "EXPIRED")),
                    "expiry is computed, never stored");
        }
    }

    @Test
    void theSweepFindsExactlyTheDeliveredOrdersStillOwedCards() throws Exception {
        UUID warrantied = UUID.randomUUID();
        UUID noWarranty = UUID.randomUUID();
        UUID owed = UUID.randomUUID();          // delivered, 2 units, 1 card
        UUID complete = UUID.randomUUID();      // delivered, 1 unit, 1 card
        UUID stale = UUID.randomUUID();         // delivered too long ago
        UUID inTransit = UUID.randomUUID();     // not delivered
        UUID trashed = UUID.randomUUID();       // delivered but deleted
        UUID noCover = UUID.randomUUID();       // delivered, product has no warranty
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO products (id, warranty_months) VALUES ('" + warrantied + "', 12)");
            s.execute("INSERT INTO products (id) VALUES ('" + noWarranty + "')");
            order(s, owed, "DELIVERED", recent, null);
            UUID owedItem = item(s, owed, warrantied, 2);
            s.execute(card("DTH-OWED-0000-0001", owedItem, 1, "ACTIVE"));
            order(s, complete, "DELIVERED", recent, null);
            s.execute(card("DTH-DONE-0000-0001", item(s, complete, warrantied, 1), 1, "ACTIVE"));
            order(s, stale, "DELIVERED", old, null);
            item(s, stale, warrantied, 1);
            order(s, inTransit, "IN_TRANSIT", null, null);
            item(s, inTransit, warrantied, 1);
            order(s, trashed, "DELIVERED", recent, Instant.now());
            item(s, trashed, warrantied, 1);
            order(s, noCover, "DELIVERED", recent, null);
            item(s, noCover, noWarranty, 1);

            String sql = WarrantyCardRepository.class
                    .getMethod("findDeliveredOrderIdsMissingCards", Instant.class)
                    .getAnnotation(Query.class).value().replace(":since", "?");
            try (PreparedStatement q = c.prepareStatement(sql)) {
                q.setTimestamp(1, Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS)));
                var rs = q.executeQuery();
                List<UUID> found = new ArrayList<>();
                while (rs.next()) found.add((UUID) rs.getObject(1));
                assertEquals(List.of(owed), found);
            }
        }
    }

    @Test
    void hibernateValidatesTheNewEntitiesAgainstTheMigratedSchema() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_JDBC_URL, postgres.getJdbcUrl())
                .applySetting(AvailableSettings.JAKARTA_JDBC_USER, postgres.getUsername())
                .applySetting(AvailableSettings.JAKARTA_JDBC_PASSWORD, postgres.getPassword())
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "validate")
                .build();
        try {
            assertDoesNotThrow(() -> new MetadataSources(registry)
                    .addAnnotatedClass(WarrantyCard.class)
                    .addAnnotatedClass(WarrantyPreset.class)
                    .buildMetadata()
                    .buildSessionFactory()
                    .close());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void order(Statement s, UUID id, String status, Instant deliveredAt, Instant deletedAt) throws SQLException {
        s.execute("INSERT INTO orders (id, status, delivered_at, deleted_at) VALUES ('" + id + "', '" + status + "', "
                + ts(deliveredAt) + ", " + ts(deletedAt) + ")");
    }

    private static UUID item(Statement s, UUID orderId, UUID productId, int quantity) throws SQLException {
        UUID id = UUID.randomUUID();
        s.execute("INSERT INTO order_items (id, order_id, product_id, quantity) VALUES ('" + id + "', '"
                + orderId + "', '" + productId + "', " + quantity + ")");
        return id;
    }

    private static String ts(Instant i) {
        return i == null ? "NULL" : "'" + i + "'";
    }

    private static String card(String serial, UUID orderItem, int unit, String status) {
        return "INSERT INTO warranty_cards (id, serial_number, order_id, order_item_id, unit_index, product_id, "
                + "duration_months, starts_on, ends_on, status, product_name, specs, created_at, updated_at) VALUES ("
                + "gen_random_uuid(), '" + serial + "', gen_random_uuid(), '" + orderItem + "', " + unit
                + ", gen_random_uuid(), 12, DATE '2026-06-24', DATE '2027-06-24', '" + status + "', 'Laptop', "
                + "'[{\"label\":\"Memory\",\"value\":\"16 GB\"}]'::jsonb, now(), now())";
    }
}
