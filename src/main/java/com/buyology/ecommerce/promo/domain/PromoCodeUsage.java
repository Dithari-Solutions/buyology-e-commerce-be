package com.buyology.ecommerce.promo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promo_code_usages",
        indexes = {
                @Index(name = "idx_pcu_promo_user", columnList = "promo_code_id, user_id"),
                @Index(name = "idx_pcu_order", columnList = "order_id")
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
}
