package com.buyology.ecommerce.cart.admin;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.customeremail.service.CustomerEmailGuard;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL coverage for lateral joins, pagination, migration and duplicate send claims. */
@Testcontainers(disabledWithoutDocker = true)
class AdminCartActivityIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    JdbcTemplate jdbc;
    AdminCartActivityService service;
    EmailService email;
    PushNotificationService push;
    final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), empty = UUID.randomUUID();
    final UUID aliceCredential = UUID.randomUUID(), bobCredential = UUID.randomUUID(), aliceCart = UUID.randomUUID(), bobCart = UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        jdbc.execute("""
            CREATE TABLE users(id UUID PRIMARY KEY, first_name TEXT, last_name TEXT, user_type TEXT, status TEXT, is_guest BOOLEAN DEFAULT FALSE, deleted_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT NOW(), email_opt_out_at TIMESTAMPTZ, email_opt_out_token UUID DEFAULT gen_random_uuid());
            CREATE TABLE auth_credentials(id UUID PRIMARY KEY, user_id UUID, email TEXT, phone_number TEXT, provider TEXT DEFAULT 'EMAIL', is_active BOOLEAN DEFAULT TRUE, created_at TIMESTAMPTZ DEFAULT NOW());
            CREATE TABLE carts(id UUID PRIMARY KEY, auth_credential_id UUID, status TEXT, currency TEXT DEFAULT 'AED', country_code TEXT DEFAULT 'UAE', updated_at TIMESTAMPTZ DEFAULT NOW());
            CREATE TABLE products(id UUID PRIMARY KEY, sku TEXT);
            CREATE TABLE product_variants(id UUID PRIMARY KEY, sku TEXT);
            CREATE TABLE product_translations(product_id UUID, language TEXT, title TEXT);
            CREATE TABLE product_media(id UUID PRIMARY KEY, product_id UUID, media_type TEXT, url TEXT, is_primary BOOLEAN, order_index INTEGER);
            CREATE TABLE cart_items(id UUID PRIMARY KEY, cart_id UUID, product_id UUID, variant_id UUID, quantity INTEGER, selected BOOLEAN DEFAULT TRUE, unit_price NUMERIC, total_price NUMERIC, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
            CREATE TABLE newsletter_subscribers(id UUID PRIMARY KEY, email TEXT, is_active BOOLEAN);
            CREATE TABLE notification_history(id UUID PRIMARY KEY, user_id UUID, title TEXT, body TEXT, type TEXT, is_read BOOLEAN, created_at TIMESTAMPTZ);
            """);
        try (var in = getClass().getResourceAsStream("/db/migration/V62__admin_cart_activity.sql"); Connection connection = ds.getConnection()) {
            assertNotNull(in);
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            connection.createStatement().execute(sql);
            connection.createStatement().execute(sql); // guarded migration can be reapplied safely
        }
        jdbc.update("INSERT INTO users(id, first_name, last_name, user_type, status) VALUES (?, 'Alice', 'Shopper', 'CUSTOMER', 'ACTIVE'), (?, 'Bob', 'Buyer', 'CUSTOMER', 'ACTIVE'), (?, 'Empty', 'Customer', 'CUSTOMER', 'ACTIVE')", alice, bob, empty);
        jdbc.update("INSERT INTO auth_credentials(id, user_id, email, phone_number) VALUES (?, ?, 'alice@example.com', '+971100'), (?, ?, 'bob@example.com', '+971200')", aliceCredential, alice, bobCredential, bob);
        jdbc.update("INSERT INTO carts(id, auth_credential_id, status, last_added_at) VALUES (?, ?, 'ACTIVE', NOW() - INTERVAL '1 hour'), (?, ?, 'CHECKED_OUT', NOW())", aliceCart, aliceCredential, bobCart, bobCredential);
        addItems(aliceCart, 5); addItems(bobCart, 1);
        email = mock(EmailService.class); push = mock(PushNotificationService.class);
        var objects = mock(ContaboObjectService.class);
        when(objects.getPresignedUrl(anyString())).thenAnswer(call -> call.getArgument(0));
        service = new AdminCartActivityService(jdbc, objects, email, push, mock(CustomerEmailGuard.class), "https://buyology.online");
    }
    void addItems(UUID cart, int count) {
        for (int i = 0; i < count; i++) {
            UUID product = UUID.randomUUID();
            jdbc.update("INSERT INTO products VALUES (?, ?)", product, "SKU-" + i);
            jdbc.update("INSERT INTO product_translations VALUES (?, 'EN', ?)", product, "Product " + i);
            jdbc.update("INSERT INTO cart_items(id, cart_id, product_id, quantity, unit_price, total_price, selected) VALUES (?, ?, ?, 2, 10, 20, FALSE)", UUID.randomUUID(), cart, product);
        }
    }
    @SuppressWarnings("unchecked") List<Map<String, Object>> content(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("content");
    }
    @Test void allCustomersIncludeEmptyCartsAndNewestAdditionRanksFirstWithBoundedPreviews() {
        var result = service.list(0, 24, "", false);
        var rows = content(result);
        assertEquals(3L, result.get("totalElements"));
        assertEquals(bob, rows.get(0).get("user_id"));
        assertEquals(alice, rows.get(1).get("user_id"));
        assertEquals(3, ((List<?>) rows.get(1).get("items")).size());
        assertEquals(5L, rows.get(1).get("item_count"));
        assertEquals(empty, rows.get(2).get("user_id"));
        assertEquals(0L, rows.get(2).get("item_count"));
        assertEquals(2L, service.list(0, 24, "", true).get("totalElements"));
    }
    @Test void searchPaginationAndMultipleCredentialsDoNotDuplicateCustomerCards() {
        jdbc.update("INSERT INTO auth_credentials(id, user_id, email, phone_number) VALUES (?, ?, 'alice-google@example.com', '+971999')", UUID.randomUUID(), alice);
        assertEquals(1L, service.list(0, 24, "971999", false).get("totalElements"));
        assertEquals(alice, content(service.list(0, 24, "alice-google", false)).get(0).get("user_id"));
        assertEquals(3L, service.list(0, 1, "", false).get("totalElements"));
        assertEquals(1, content(service.list(1, 1, "", false)).size());
    }
    @Test void detailsMatchEditableCartAndDoNotResumeCheckoutOrRewritePrices() {
        var detail = service.detail(bob);
        assertEquals(1, ((List<?>) detail.get("items")).size());
        assertEquals("CHECKED_OUT", jdbc.queryForObject("SELECT status FROM carts WHERE id = ?", String.class, bobCart));
        // An active cart takes precedence over the old pending checkout, matching customer cart reads.
        UUID active = UUID.randomUUID();
        jdbc.update("INSERT INTO carts(id, auth_credential_id, status) VALUES (?, ?, 'ACTIVE')", active, bobCredential);
        assertTrue(((List<?>) service.detail(bob).get("items")).isEmpty());
    }
    @Test void sendRecordsBothChannelOutcomesAndSameRequestIsNotDeliveredTwice() {
        when(email.sendCartActivityEmail(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        UUID request = UUID.randomUUID(), admin = UUID.randomUUID();
        var result = service.send(alice, admin, request, "Your cart", "Can we help?");
        assertEquals("ACCEPTED", result.get("email_status"));
        assertEquals("QUEUED", result.get("notification_status"));
        service.send(alice, admin, request, "Your cart", "Can we help?");
        verify(email, times(1)).sendCartActivityEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(push, times(1)).deliverRecordedToUser(eq(alice), anyString(), anyString(), anyMap());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_history WHERE user_id = ?", Integer.class, alice));
    }
}
