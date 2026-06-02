package com.buyology.ecommerce.payment;

import com.buyology.ecommerce.payment.domain.ProcessedWebhookEvent;
import com.buyology.ecommerce.payment.repository.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test against a real Postgres (Testcontainers). Proves the webhook
 * idempotency ledger's UNIQUE(event_key) constraint actually rejects a replayed
 * event at the database level — the guarantee that prevents double-processing /
 * double-credit of Paymob webhooks.
 *
 * Validates the whole JPA model maps against real Postgres as a side effect
 * (ddl-auto creates every entity). Flyway is disabled here so Hibernate owns the
 * schema for this slice; FlywayBaselineMigrationIT covers the migration path.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        // Against a real (non-embedded) Postgres, @DataJpaTest leaves ddl-auto=none,
        // so the schema must be created explicitly for this slice.
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class WebhookIdempotencyConstraintIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    ProcessedWebhookEventRepository repo;

    @Test
    void duplicateEventKey_isRejectedByUniqueConstraint() {
        repo.saveAndFlush(new ProcessedWebhookEvent("paymob-txn-364384894"));
        assertThrows(DataIntegrityViolationException.class,
                () -> repo.saveAndFlush(new ProcessedWebhookEvent("paymob-txn-364384894")),
                "a replayed webhook event_key must collide on the unique constraint");
    }

    @Test
    void distinctEventKeys_areAccepted() {
        repo.saveAndFlush(new ProcessedWebhookEvent("CRED-usage-1"));
        repo.saveAndFlush(new ProcessedWebhookEvent("CRED-usage-2"));
        assertEquals(2, repo.count());
    }
}
