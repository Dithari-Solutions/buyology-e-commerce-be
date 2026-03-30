package com.buyology.ecommerce.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps one of our app orders to a Paymob-side order (Step 2 of the Paymob flow).
 * One provider order can be shared by multiple payment_transactions (retry attempts).
 */
@Entity
@Table(name = "payment_provider_orders", indexes = {
        @Index(name = "idx_provider_orders_app_order", columnList = "app_order_id")
})
public class PaymentProviderOrder {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK to the orders service — stored as UUID without a DB-level FK (cross-service).
    // Null for the cart-first flow (order doesn't exist yet when payment is initiated).
    @Column(name = "app_order_id")
    private UUID appOrderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private PaymentProvider provider;

    // Paymob's order ID — unique constraint enforced by Paymob and mirrored here
    @Column(name = "provider_order_id", nullable = false, unique = true, length = 100)
    private String providerOrderId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAppOrderId() { return appOrderId; }
    public void setAppOrderId(UUID appOrderId) { this.appOrderId = appOrderId; }

    public PaymentProvider getProvider() { return provider; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }

    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
