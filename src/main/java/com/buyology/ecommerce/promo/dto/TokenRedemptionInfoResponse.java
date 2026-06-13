package com.buyology.ecommerce.promo.dto;

import com.buyology.ecommerce.promo.domain.DiscountType;

import java.math.BigDecimal;

/** Customer-facing view of the redemption offer (for rendering the redeem button). */
public class TokenRedemptionInfoResponse {
    private boolean enabled;
    private int tokenCost;
    private DiscountType discountType;
    private BigDecimal discountValue;
    /** null = unlimited redemptions remaining for this customer. */
    private Integer redemptionsRemaining;

    public TokenRedemptionInfoResponse(boolean enabled, int tokenCost, DiscountType discountType,
                                       BigDecimal discountValue, Integer redemptionsRemaining) {
        this.enabled = enabled;
        this.tokenCost = tokenCost;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.redemptionsRemaining = redemptionsRemaining;
    }

    public boolean isEnabled() { return enabled; }
    public int getTokenCost() { return tokenCost; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public Integer getRedemptionsRemaining() { return redemptionsRemaining; }
}
