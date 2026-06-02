package com.buyology.ecommerce.payment.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency ledger for Paymob webhooks. Exactly one row may exist per
 * provider event key (the numeric Paymob transaction id, or the
 * merchant_order_id for flows that do not carry a transaction id, e.g. the
 * {@code CRED-} B2B credit-payback branch).
 *
 * Processing is INSERT-first: a row is written before any state is mutated.
 * A duplicate/replayed webhook collides on the UNIQUE constraint and is
 * rejected (DataIntegrityViolationException), guaranteeing each event is
 * applied at most once even under concurrent delivery.
 */
@Entity
@Table(name = "processed_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_processed_webhook_event_key", columnNames = "event_key")
})
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The provider event key: the numeric Paymob transaction id (as text) when
     * present, otherwise the merchant_order_id. UNIQUE — drives idempotency.
     */
    @Column(name = "event_key", nullable = false, unique = true, length = 150)
    private String eventKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProcessedWebhookEvent() {
    }

    public ProcessedWebhookEvent(String eventKey) {
        this.eventKey = eventKey;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
