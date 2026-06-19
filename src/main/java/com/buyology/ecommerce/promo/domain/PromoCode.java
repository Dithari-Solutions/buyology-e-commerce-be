package com.buyology.ecommerce.promo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promo_codes", indexes = {
        @Index(name = "idx_pc_code", columnList = "code"),
        @Index(name = "idx_pc_active", columnList = "is_active")
})
public class PromoCode {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "minimum_order_amount", precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount;

    /** null = unlimited total uses */
    @Column(name = "max_uses_total")
    private Integer maxUsesTotal;

    /** null = unlimited per customer */
    @Column(name = "max_uses_per_customer")
    private Integer maxUsesPerCustomer;

    /** JSON array of product UUIDs — null means applies to all products */
    @Column(name = "applicable_product_ids", columnDefinition = "text")
    private String applicableProductIds;

    /** JSON array of category UUIDs — null means applies to all categories */
    @Column(name = "applicable_category_ids", columnDefinition = "text")
    private String applicableCategoryIds;

    /**
     * JSON array of category UUIDs the code may NOT be used on — null means no exclusions.
     * If any item in the order belongs to an excluded category, the code is rejected.
     * (Used by WELCOME10 to exclude laptops.)
     */
    @Column(name = "excluded_category_ids", columnDefinition = "text")
    private String excludedCategoryIds;

    /**
     * When set, the code is only valid for this many days after the customer's signup
     * (a per-user window, independent of {@link #expiresAt}). null = no signup window.
     * (Used by WELCOME10 for its 1-week validity.)
     */
    @Column(name = "valid_days_from_signup")
    private Integer validDaysFromSignup;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * When set, this code is PERSONAL: only the user with this id may validate/apply it.
     * null = public code usable by anyone (the historical behaviour).
     */
    @Column(name = "target_user_id")
    private UUID targetUserId;

    /**
     * Audit: when this code was minted by a customer redeeming tokens, the number of
     * tokens spent. null = not a token-redemption code (admin-created / personal-issued).
     */
    @Column(name = "redeemed_from_tokens")
    private Integer redeemedFromTokens;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
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
    public String getApplicableProductIds() { return applicableProductIds; }
    public void setApplicableProductIds(String applicableProductIds) { this.applicableProductIds = applicableProductIds; }
    public String getApplicableCategoryIds() { return applicableCategoryIds; }
    public void setApplicableCategoryIds(String applicableCategoryIds) { this.applicableCategoryIds = applicableCategoryIds; }
    public String getExcludedCategoryIds() { return excludedCategoryIds; }
    public void setExcludedCategoryIds(String excludedCategoryIds) { this.excludedCategoryIds = excludedCategoryIds; }
    public Integer getValidDaysFromSignup() { return validDaysFromSignup; }
    public void setValidDaysFromSignup(Integer validDaysFromSignup) { this.validDaysFromSignup = validDaysFromSignup; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public UUID getTargetUserId() { return targetUserId; }
    public void setTargetUserId(UUID targetUserId) { this.targetUserId = targetUserId; }
    public Integer getRedeemedFromTokens() { return redeemedFromTokens; }
    public void setRedeemedFromTokens(Integer redeemedFromTokens) { this.redeemedFromTokens = redeemedFromTokens; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
