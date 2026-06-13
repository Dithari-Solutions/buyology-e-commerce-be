package com.buyology.ecommerce.promo.dto;

import com.buyology.ecommerce.promo.domain.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Admin request: mint a coupon bound to ONE user and notify only that user. */
public class IssuePersonalCodeRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal discountValue;

    private BigDecimal minimumOrderAmount;

    /** Days until expiry; null = never expires. */
    private Integer validityDays;

    /** Max uses for the minted code; null = single-use. */
    private Integer maxUses;

    private String description;

    private boolean sendEmail = true;
    private boolean sendPush = false;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMinimumOrderAmount() { return minimumOrderAmount; }
    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) { this.minimumOrderAmount = minimumOrderAmount; }
    public Integer getValidityDays() { return validityDays; }
    public void setValidityDays(Integer validityDays) { this.validityDays = validityDays; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSendEmail() { return sendEmail; }
    public void setSendEmail(boolean sendEmail) { this.sendEmail = sendEmail; }
    public boolean isSendPush() { return sendPush; }
    public void setSendPush(boolean sendPush) { this.sendPush = sendPush; }
}
