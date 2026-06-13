package com.buyology.ecommerce.promo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Single-row configuration controlling how customers convert game tokens into a
 * discount coupon. Admin-editable from the dashboard so the reward and its usage
 * limits can be tuned without a deploy.
 */
@Entity
@Table(name = "token_redemption_config")
public class TokenRedemptionConfig {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Master switch — when false, the redeem endpoint is closed. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Tokens spent per redemption (e.g. 1000). */
    @Column(name = "token_cost", nullable = false)
    private int tokenCost = 1000;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.PERCENTAGE;

    /** Discount the minted coupon carries (percent when PERCENTAGE, currency amount when FIXED_AMOUNT). */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue = BigDecimal.valueOf(10);

    /** Optional minimum order amount stamped onto the minted coupon. */
    @Column(name = "minimum_order_amount", precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount;

    /** Days until the minted coupon expires. */
    @Column(name = "coupon_validity_days", nullable = false)
    private int couponValidityDays = 30;

    /** Max times each minted coupon can be used (defaults to single-use). */
    @Column(name = "max_uses_per_coupon", nullable = false)
    private int maxUsesPerCoupon = 1;

    /** Max number of token-redemptions allowed per customer overall. null = unlimited. */
    @Column(name = "max_redemptions_per_customer")
    private Integer maxRedemptionsPerCustomer = 10;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTokenCost() { return tokenCost; }
    public void setTokenCost(int tokenCost) { this.tokenCost = tokenCost; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMinimumOrderAmount() { return minimumOrderAmount; }
    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) { this.minimumOrderAmount = minimumOrderAmount; }
    public int getCouponValidityDays() { return couponValidityDays; }
    public void setCouponValidityDays(int couponValidityDays) { this.couponValidityDays = couponValidityDays; }
    public int getMaxUsesPerCoupon() { return maxUsesPerCoupon; }
    public void setMaxUsesPerCoupon(int maxUsesPerCoupon) { this.maxUsesPerCoupon = maxUsesPerCoupon; }
    public Integer getMaxRedemptionsPerCustomer() { return maxRedemptionsPerCustomer; }
    public void setMaxRedemptionsPerCustomer(Integer maxRedemptionsPerCustomer) { this.maxRedemptionsPerCustomer = maxRedemptionsPerCustomer; }
    public Instant getUpdatedAt() { return updatedAt; }
}
