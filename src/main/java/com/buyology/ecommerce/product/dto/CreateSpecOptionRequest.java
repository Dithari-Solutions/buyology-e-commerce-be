package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

@Schema(description = "Inline spec option to create during product creation (e.g. 16GB, 512GB SSD)")
public class CreateSpecOptionRequest {

    @NotBlank(message = "Spec option localKey is required")
    @Schema(description = "Unique key within this request used by variants to reference this option", example = "ram-16gb")
    private String localKey;

    @NotBlank(message = "Spec option value (AZ) is required")
    @Schema(description = "Option value in Azerbaijani", example = "16 GB")
    private String valueAz;

    @NotBlank(message = "Spec option value (EN) is required")
    @Schema(description = "Option value in English", example = "16 GB")
    private String valueEn;

    @NotBlank(message = "Spec option value (AR) is required")
    @Schema(description = "Option value in Arabic", example = "16 جيجابايت")
    private String valueAr;

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

    public String getValueAz() {
        return valueAz;
    }

    public void setValueAz(String valueAz) {
        this.valueAz = valueAz;
    }

    public String getValueEn() {
        return valueEn;
    }

    public void setValueEn(String valueEn) {
        this.valueEn = valueEn;
    }

    public String getValueAr() {
        return valueAr;
    }

    public void setValueAr(String valueAr) {
        this.valueAr = valueAr;
    }

    public BigDecimal getAdditionalPrice() {
        return additionalPrice;
    }

    public void setAdditionalPrice(BigDecimal additionalPrice) {
        this.additionalPrice = additionalPrice;
    }
}
