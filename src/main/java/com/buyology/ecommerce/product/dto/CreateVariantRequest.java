package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request data for creating a product variant with its linked spec options")
public class CreateVariantRequest {

    @NotBlank(message = "Variant SKU is required")
    @Size(max = 255, message = "Variant SKU must not exceed 255 characters")
    @Schema(description = "Unique stock-keeping unit identifier for this variant", example = "PROD-RED-XL-001")
    private String sku;

    @Schema(description = "List of existing ProductSpecOption UUIDs that define this variant (e.g. color, size)")
    private List<UUID> specOptionIds;

    @Schema(description = "List of localKeys referencing spec options defined inline in the same create-product request")
    private List<String> specOptionLocalKeys;

    // ========================
    // Getters & Setters
    // ========================

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public List<UUID> getSpecOptionIds() {
        return specOptionIds;
    }

    public void setSpecOptionIds(List<UUID> specOptionIds) {
        this.specOptionIds = specOptionIds;
    }

    public List<String> getSpecOptionLocalKeys() {
        return specOptionLocalKeys;
    }

    public void setSpecOptionLocalKeys(List<String> specOptionLocalKeys) {
        this.specOptionLocalKeys = specOptionLocalKeys;
    }
}
