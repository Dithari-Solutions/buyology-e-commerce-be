package com.buyology.ecommerce.promo.dto;

import com.buyology.ecommerce.promo.domain.DiscountType;
import com.buyology.ecommerce.promo.domain.TokenRedemptionConfig;

import java.math.BigDecimal;

/** Admin-facing view/edit shape of the token-redemption configuration. */
public class TokenRedemptionConfigDto {
    private boolean enabled;
    private int tokenCost;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderAmount;
    private int couponValidityDays;
    private int maxUsesPerCoupon;
    private Integer maxRedemptionsPerCustomer;

    public TokenRedemptionConfigDto() {}

    public static TokenRedemptionConfigDto from(TokenRedemptionConfig c) {
        TokenRedemptionConfigDto d = new TokenRedemptionConfigDto();
        d.enabled = c.isEnabled();
        d.tokenCost = c.getTokenCost();
        d.discountType = c.getDiscountType();
        d.discountValue = c.getDiscountValue();
        d.minimumOrderAmount = c.getMinimumOrderAmount();
        d.couponValidityDays = c.getCouponValidityDays();
        d.maxUsesPerCoupon = c.getMaxUsesPerCoupon();
        d.maxRedemptionsPerCustomer = c.getMaxRedemptionsPerCustomer();
        return d;
    }

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
}
