package com.buyology.ecommerce.payment.domain;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Central table — one row per payment attempt, regardless of outcome.
 * PENDING and PROCESSING are the only mutable states.
 * Once SUCCESS, FAILED, or CANCELLED: the status must never be changed
 * except by the refund workflow (→ REFUNDED / PARTIALLY_REFUNDED).
 */
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_transactions_app_order", columnList = "app_order_id"),
        @Index(name = "idx_payment_transactions_status", columnList = "status"),
        @Index(name = "idx_payment_transactions_provider_txn", columnList = "provider_transaction_id")
})
public class PaymentTransaction {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK to orders service — stored as plain UUID (cross-service boundary)
    @Column(name = "app_order_id", nullable = false)
    private UUID appOrderId;

    // Nullable: set once the Paymob order is created
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_order_id")
    private PaymentProviderOrder providerOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "method_config_id", nullable = false)
    private PaymentMethodConfig methodConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    private PaymentMethodType methodType;

    // Human display / accounting; use amountCents for all Paymob communication
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    // Set when Paymob webhook arrives; UNIQUE ensures no double-processing
    @Column(name = "provider_transaction_id", unique = true, length = 100)
    private String providerTransactionId;

    // Paymob payment key (~1 hour TTL); do not expose beyond initial redirect
    @Column(name = "payment_key_token", columnDefinition = "TEXT")
    private String paymentKeyToken;

    // Tabby / Tamara redirect URL — stored for session-recovery without re-hitting Paymob
    @Column(name = "redirect_url", columnDefinition = "TEXT")
    private String redirectUrl;

    // Customer snapshot at time of payment — immutable audit anchor
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    @Column(name = "billing_name", length = 255)
    private String billingName;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = PaymentStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAppOrderId() { return appOrderId; }
    public void setAppOrderId(UUID appOrderId) { this.appOrderId = appOrderId; }

    public PaymentProviderOrder getProviderOrder() { return providerOrder; }
    public void setProviderOrder(PaymentProviderOrder providerOrder) { this.providerOrder = providerOrder; }

    public PaymentMethodConfig getMethodConfig() { return methodConfig; }
    public void setMethodConfig(PaymentMethodConfig methodConfig) { this.methodConfig = methodConfig; }

    public PaymentMethodType getMethodType() { return methodType; }
    public void setMethodType(PaymentMethodType methodType) { this.methodType = methodType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }

    public String getPaymentKeyToken() { return paymentKeyToken; }
    public void setPaymentKeyToken(String paymentKeyToken) { this.paymentKeyToken = paymentKeyToken; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getBillingName() { return billingName; }
    public void setBillingName(String billingName) { this.billingName = billingName; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
