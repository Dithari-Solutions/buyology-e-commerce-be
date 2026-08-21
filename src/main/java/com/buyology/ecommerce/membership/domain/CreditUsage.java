package com.buyology.ecommerce.membership.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_usages", indexes = {
        @Index(name = "idx_cu_user_id", columnList = "user_id"),
        @Index(name = "idx_cu_membership_id", columnList = "membership_id"),
        @Index(name = "idx_cu_order_id", columnList = "order_id"),
        @Index(name = "idx_cu_status", columnList = "status"),
        @Index(name = "idx_cu_due_at", columnList = "due_at")
})
public class CreditUsage {

    public enum Status {
        OUTSTANDING, PARTIAL, PAID, OVERDUE,
        /**
         * The order was cancelled or fully refunded, so the credit is no longer owed and has been
         * returned to the wallet.
         *
         * <p>Deliberately not PAID: the member never repaid this, and every report that counts
         * repayments would otherwise count money that was never collected. It is absent from the
         * OUTSTANDING/PARTIAL/OVERDUE lists that drive chasing and membership gating, which is
         * exactly the intent — a member must not be pursued for goods they no longer have.
         */
        REVERSED
    }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.OUTSTANDING;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paymob_intention_id", length = 200)
    private String paymobIntentionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.usedAt == null) this.usedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getMembershipId() { return membershipId; }
    public void setMembershipId(UUID membershipId) { this.membershipId = membershipId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public String getPaymobIntentionId() { return paymobIntentionId; }
    public void setPaymobIntentionId(String paymobIntentionId) { this.paymobIntentionId = paymobIntentionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
