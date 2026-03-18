package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Spec option to attach to a product spec group — must reference an existing global spec option")
public class CreateSpecOptionRequest {

    @NotBlank(message = "Spec option localKey is required")
    @Schema(description = "Unique key within this request used by variants to reference this option", example = "ram-16gb")
    private String localKey;

    @NotNull(message = "globalOptionId is required — select an option from the global spec library")
    @Schema(description = "ID of the global spec option — names and unit are read from the global spec library")
    private UUID globalOptionId;

    @DecimalMin(value = "0.00", message = "Additional price must be non-negative")
    @Schema(description = "Additional price added to the base price for this option (default 0)", example = "50.00")
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    // ========================
    // Getters & Setters
    // ========================

    public String getLocalKey() {
        return localKey;
    }

    public void setLocalKey(String localKey) {
        this.localKey = localKey;
    }

    public UUID getGlobalOptionId() {
        return globalOptionId;
    }

    public void setGlobalOptionId(UUID globalOptionId) {
        this.globalOptionId = globalOptionId;
    }

    public BigDecimal getAdditionalPrice() {
        return additionalPrice;
    }

    public void setAdditionalPrice(BigDecimal additionalPrice) {
        this.additionalPrice = additionalPrice;
    }
}
