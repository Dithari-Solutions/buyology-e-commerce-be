package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.product.domain.Product.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

@Schema(description = "Update price, discount, or active status of a store product assignment")
public class UpdateStoreProductRequest {

    @DecimalMin(value = "0.00", message = "storePrice must be non-negative")
    @Schema(description = "New price in the store's local currency")
    private BigDecimal storePrice;

    @Schema(description = "Discount type — FIXED or PERCENTAGE. Send null to remove discount.")
    private DiscountType discountType;

    @DecimalMin(value = "0.00", message = "discountValue must be non-negative")
    @Schema(description = "Discount amount. Send null to remove discount.")
    private BigDecimal discountValue;

    @Schema(description = "Activate or deactivate this product in the store")
    private Boolean isActive;

    // Getters & Setters

    public BigDecimal getStorePrice() { return storePrice; }
    public void setStorePrice(BigDecimal storePrice) { this.storePrice = storePrice; }

    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
