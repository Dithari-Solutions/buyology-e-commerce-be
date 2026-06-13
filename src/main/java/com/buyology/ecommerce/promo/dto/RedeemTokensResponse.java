package com.buyology.ecommerce.promo.dto;

import com.buyology.ecommerce.promo.domain.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/** Returned to a customer after they redeem tokens for a personal coupon. */
public class RedeemTokensResponse {
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderAmount;
    private Instant expiresAt;
    private int remainingTokens;
    /** null = unlimited further redemptions. */
    private Integer redemptionsRemaining;

    public RedeemTokensResponse(String code, DiscountType discountType, BigDecimal discountValue,
                                BigDecimal minimumOrderAmount, Instant expiresAt,
                                int remainingTokens, Integer redemptionsRemaining) {
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minimumOrderAmount = minimumOrderAmount;
        this.expiresAt = expiresAt;
        this.remainingTokens = remainingTokens;
        this.redemptionsRemaining = redemptionsRemaining;
    }

    public String getCode() { return code; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinimumOrderAmount() { return minimumOrderAmount; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getRemainingTokens() { return remainingTokens; }
    public Integer getRedemptionsRemaining() { return redemptionsRemaining; }
}
