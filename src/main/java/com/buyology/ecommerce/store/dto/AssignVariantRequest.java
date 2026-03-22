package com.buyology.ecommerce.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Assign a product variant to a store with store-specific price and stock quantity")
public class AssignVariantRequest {

    @NotNull(message = "variantId is required")
    @Schema(description = "UUID of the global ProductVariant to assign")
    private UUID variantId;

    @NotNull(message = "storePrice is required")
    @DecimalMin(value = "0.00", message = "storePrice must be non-negative")
    @Schema(description = "Variant price in the store's local currency", example = "47000.00")
    private BigDecimal storePrice;

    @NotNull(message = "stock is required")
    @Min(value = 0, message = "stock must be non-negative")
    @Schema(description = "Available stock quantity for this variant in this store", example = "15")
    private Integer stock;

    @Schema(description = "Whether this variant is active in the store", defaultValue = "true")
    private Boolean isActive = true;

    // Getters & Setters

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public BigDecimal getStorePrice() { return storePrice; }
    public void setStorePrice(BigDecimal storePrice) { this.storePrice = storePrice; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
