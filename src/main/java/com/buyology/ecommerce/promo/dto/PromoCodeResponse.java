package com.buyology.ecommerce.promo.dto;

import com.buyology.ecommerce.promo.domain.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PromoCodeResponse {
    private UUID id;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderAmount;
    private Integer maxUsesTotal;
    private Integer maxUsesPerCustomer;
    private long totalUsed;
    private Instant expiresAt;
    private UUID targetUserId;
    private boolean isActive;
    private String description;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMinimumOrderAmount() { return minimumOrderAmount; }
    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) { this.minimumOrderAmount = minimumOrderAmount; }
    public Integer getMaxUsesTotal() { return maxUsesTotal; }
    public void setMaxUsesTotal(Integer maxUsesTotal) { this.maxUsesTotal = maxUsesTotal; }
    public Integer getMaxUsesPerCustomer() { return maxUsesPerCustomer; }
    public void setMaxUsesPerCustomer(Integer maxUsesPerCustomer) { this.maxUsesPerCustomer = maxUsesPerCustomer; }
    public long getTotalUsed() { return totalUsed; }
    public void setTotalUsed(long totalUsed) { this.totalUsed = totalUsed; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public UUID getTargetUserId() { return targetUserId; }
    public void setTargetUserId(UUID targetUserId) { this.targetUserId = targetUserId; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
