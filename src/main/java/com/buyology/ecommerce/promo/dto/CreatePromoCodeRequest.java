package com.buyology.ecommerce.promo.dto;

import com.buyology.ecommerce.promo.domain.DiscountType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CreatePromoCodeRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String code;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal discountValue;

    @DecimalMin("0.00")
    private BigDecimal minimumOrderAmount;

    @Min(1)
    private Integer maxUsesTotal;

    @Min(1)
    private Integer maxUsesPerCustomer;

    private List<UUID> applicableProductIds;
    private List<UUID> applicableCategoryIds;
    /** Categories the code may NOT be used on (e.g. laptops). */
    private List<UUID> excludedCategoryIds;

    private Instant expiresAt;
    /** Per-user validity window in days from signup (independent of expiresAt). */
    @Min(1)
    private Integer validDaysFromSignup;
    private String description;

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
    public List<UUID> getApplicableProductIds() { return applicableProductIds; }
    public void setApplicableProductIds(List<UUID> applicableProductIds) { this.applicableProductIds = applicableProductIds; }
    public List<UUID> getApplicableCategoryIds() { return applicableCategoryIds; }
    public void setApplicableCategoryIds(List<UUID> applicableCategoryIds) { this.applicableCategoryIds = applicableCategoryIds; }
    public List<UUID> getExcludedCategoryIds() { return excludedCategoryIds; }
    public void setExcludedCategoryIds(List<UUID> excludedCategoryIds) { this.excludedCategoryIds = excludedCategoryIds; }
    public Integer getValidDaysFromSignup() { return validDaysFromSignup; }
    public void setValidDaysFromSignup(Integer validDaysFromSignup) { this.validDaysFromSignup = validDaysFromSignup; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
