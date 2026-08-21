package com.buyology.ecommerce.promo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promo_code_usages",
        indexes = {
                @Index(name = "idx_pcu_promo_user", columnList = "promo_code_id, user_id"),
                @Index(name = "idx_pcu_order", columnList = "order_id"),
                @Index(name = "idx_pcu_promo_status", columnList = "promo_code_id, status")
        },
        uniqueConstraints = {
                // Dedupe at the DB level: a single redemption is identified by
                // (promo, customer, order). This blocks the usage race / single-use
                // bypass — concurrent checkouts for the same order can no longer both
                // insert a usage row, and a retried checkout is idempotent.
                @UniqueConstraint(name = "uq_pcu_promo_user_order",
                        columnNames = {"promo_code_id", "user_id", "order_id"})
        })
public class PromoCodeUsage {

    /**
     * Whether the code has actually been spent, or is only being held.
     *
     * <p>Both count against a code's limits — the difference is what happens next. A RESERVED row
     * belongs to an order that has been placed but not paid for, and is deleted if that order is
     * cancelled or its payment fails, which puts the code back. A REDEEMED row is a discount the
     * customer received and is permanent.
     *
     * <p>Recording only redemptions is what let a single-use code be spent repeatedly: nothing
     * counted the orders already carrying it, so the limit check passed for each in turn and every
     * one of them kept its discount.
     */
    public enum Status {
        /** Claimed by an unpaid order. Counts against the limit; released if the order dies. */
        RESERVED,
        /** Spent on a paid order. Permanent. */
        REDEEMED
    }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCode promoCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "discount_applied", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountApplied;

    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;

    /**
     * Defaults to REDEEMED so that any path which writes a usage row without going through the
     * reservation flow still records a spent code rather than a releasable hold — the safe
     * direction if the two ever drift.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.REDEEMED;

    @PrePersist
    public void prePersist() {
        this.usedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public PromoCode getPromoCode() { return promoCode; }
    public void setPromoCode(PromoCode promoCode) { this.promoCode = promoCode; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public BigDecimal getDiscountApplied() { return discountApplied; }
    public void setDiscountApplied(BigDecimal discountApplied) { this.discountApplied = discountApplied; }
    public Instant getUsedAt() { return usedAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
