package com.buyology.ecommerce.product.dto;

import com.buyology.ecommerce.common.enums.SpecUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Inline spec option to create during product creation (e.g. 16, 512, 1)")
public class CreateSpecOptionRequest {

    @NotBlank(message = "Spec option localKey is required")
    @Schema(description = "Unique key within this request used by variants to reference this option", example = "ram-16gb")
    private String localKey;

    @Schema(description = "Optional: reference a GlobalSpecOption by ID — its valueAz/En/Ar and unit will be copied automatically, no need to fill those fields manually")
    private UUID globalOptionId;

    @Schema(description = "Option value in Azerbaijani — required only when globalOptionId is not provided", example = "16")
    private String valueAz;

    @Schema(description = "Option value in English — required only when globalOptionId is not provided", example = "16")
    private String valueEn;

    @Schema(description = "Option value in Arabic — required only when globalOptionId is not provided", example = "16")
    private String valueAr;

    @Schema(
            description = "Unit for numeric spec values. Must be one of the allowed enum values: " +
                    "KB, MB, GB, TB, MHz, GHz, mAh, Wh, W, INCH, MP, Mbps, Gbps, G, KG, HZ, RPM",
            example = "GB"
    )
    private SpecUnit unit;

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

    public SpecUnit getUnit() {
        return unit;
    }

    public void setUnit(SpecUnit unit) {
        this.unit = unit;
    }

    public BigDecimal getAdditionalPrice() {
        return additionalPrice;
    }

    public void setAdditionalPrice(BigDecimal additionalPrice) {
        this.additionalPrice = additionalPrice;
    }
}
