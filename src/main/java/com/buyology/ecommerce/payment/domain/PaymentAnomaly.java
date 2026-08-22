package com.buyology.ecommerce.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One settled payment that could not be applied to its order.
 *
 * <p>Before this table, such a payment vanished: onPaymentSucceeded had no else-branch, so a
 * payment landing on a cancelled order was money captured with no record anywhere except a
 * transaction row nobody was looking at. The row here is the durable evidence, the admin queue,
 * and the idempotency key.
 *
 * <p>The unique index on {@code payment_transaction_id} is not tidiness: it is what makes
 * detection exactly-once across two replicas with no ShedLock, and the reason an auto-refund can
 * be attempted at most once per payment.
 *
 * <p>{@code kind} and {@code resolution} are plain strings, not @Enumerated — Hibernate emits a
 * CHECK from an enum's values at table-creation time and never revisits it, which has broken this
 * repo twice (V33, V34). A future kind must never need a migration.
 *
 * <p>No @ManyToOne associations, by design: a REQUIRES_NEW insert here runs while the listener's
 * transaction holds writes on payment_transactions and orders, and an FK would take FOR KEY SHARE
 * on those parents — the exact lock pattern that hung every refund in this repo once already.
 */
@Entity
@Table(name = "payment_anomalies",
        indexes = {
                @Index(name = "idx_payment_anomalies_resolution", columnList = "resolution, created_at"),
                @Index(name = "idx_payment_anomalies_order", columnList = "app_order_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_payment_anomalies_tx", columnNames = {"payment_transaction_id"})
        })
public class PaymentAnomaly {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_transaction_id", nullable = false)
    private UUID paymentTransactionId;

    @Column(name = "app_order_id")
    private UUID appOrderId;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "resolution", nullable = false, length = 40)
    private String resolution = "OPEN";

    @Column(name = "order_status", length = 40)
    private String orderStatus;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /** LISTENER or RECONCILER — which path noticed it, for diagnosing coverage gaps. */
    @Column(name = "detected_by", nullable = false, length = 20)
    private String detectedBy;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "refund_id")
    private UUID refundId;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(UUID v) { this.paymentTransactionId = v; }
    public UUID getAppOrderId() { return appOrderId; }
    public void setAppOrderId(UUID v) { this.appOrderId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public String getResolution() { return resolution; }
    public void setResolution(String v) { this.resolution = v; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String v) { this.orderStatus = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { this.detail = v; }
    public String getDetectedBy() { return detectedBy; }
    public void setDetectedBy(String v) { this.detectedBy = v; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { this.attempts = v; }
    public UUID getRefundId() { return refundId; }
    public void setRefundId(UUID v) { this.refundId = v; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String v) { this.resolutionNote = v; }
    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID v) { this.resolvedBy = v; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant v) { this.resolvedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
