package com.buyology.ecommerce.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

@Schema(description = "Update price, stock, or active status of a store product variant")
public class UpdateStoreVariantRequest {

    @DecimalMin(value = "0.00", message = "storePrice must be non-negative")
    @Schema(description = "New variant price in the store's local currency")
    private BigDecimal storePrice;

    @Min(value = 0, message = "stock must be non-negative")
    @Schema(description = "New stock quantity")
    private Integer stock;

    @Schema(description = "Activate or deactivate this variant in the store")
    private Boolean isActive;

    // Getters & Setters

    public BigDecimal getStorePrice() { return storePrice; }
    public void setStorePrice(BigDecimal storePrice) { this.storePrice = storePrice; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
