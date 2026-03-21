package com.buyology.ecommerce.payment.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only raw log of every Paymob webhook callback.
 * Rule: store first, process second.
 * Never delete rows — they are the audit trail for disputes and reconciliation.
 */
@Entity
@Table(name = "payment_webhook_events", indexes = {
        @Index(name = "idx_webhook_events_txn", columnList = "transaction_id"),
        @Index(name = "idx_webhook_events_provider_txn_id", columnList = "provider_txn_id")
})
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Nullable: set after lookup by provider_txn_id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private PaymentProvider provider;

    // Nullable: may be NULL if the provider_txn_id cannot be matched on arrival
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private PaymentTransaction transaction;

    @Column(name = "event_type", length = 100)
    private String eventType;

    // Paymob transaction ID from the webhook payload
    @Column(name = "provider_txn_id", length = 100)
    private String providerTxnId;

    // If false: log the row but never update the transaction; alert on this
    @Column(name = "hmac_valid", nullable = false)
    private boolean hmacValid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public PaymentProvider getProvider() { return provider; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }

    public PaymentTransaction getTransaction() { return transaction; }
    public void setTransaction(PaymentTransaction transaction) { this.transaction = transaction; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getProviderTxnId() { return providerTxnId; }
    public void setProviderTxnId(String providerTxnId) { this.providerTxnId = providerTxnId; }

    public boolean isHmacValid() { return hmacValid; }
    public void setHmacValid(boolean hmacValid) { this.hmacValid = hmacValid; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
