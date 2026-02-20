package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Request data for creating a product variant with its linked spec options")
public class CreateVariantRequest {

    @NotBlank(message = "Variant SKU is required")
    @Size(max = 255, message = "Variant SKU must not exceed 255 characters")
    @Schema(description = "Unique stock-keeping unit identifier for this variant", example = "PROD-RED-XL-001")
    private String sku;

    @NotNull(message = "Variant price is required")
    @DecimalMin(value = "0.00", message = "Variant price must be non-negative")
    @Schema(description = "Price for this specific variant", example = "149.99")
    private BigDecimal price;

    @NotNull(message = "Variant stock is required")
    @Min(value = 0, message = "Stock must be non-negative")
    @Schema(description = "Stock quantity available for this variant", example = "50")
    private Integer stock;

    @Schema(description = "List of existing ProductSpecOption UUIDs that define this variant (e.g. color, size)")
    private List<UUID> specOptionIds;

    // ========================
    // Getters & Setters
    // ========================

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public List<UUID> getSpecOptionIds() {
        return specOptionIds;
    }

    public void setSpecOptionIds(List<UUID> specOptionIds) {
        this.specOptionIds = specOptionIds;
    }
}
