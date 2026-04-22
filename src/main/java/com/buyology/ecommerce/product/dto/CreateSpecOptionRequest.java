package com.buyology.ecommerce.product.dto;

import com.buyology.ecommerce.common.enums.SpecUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Spec option to attach to a product spec group. Provide globalOptionId to reference an existing global spec option, " +
        "or provide valueAz/valueEn/valueAr (and optionally unit) to create a new global spec option on the fly.")
public class CreateSpecOptionRequest {

    @NotBlank(message = "Spec option localKey is required")
    @Schema(description = "Unique key within this request used by variants to reference this option", example = "ram-16gb")
    private String localKey;

    @Schema(description = "ID of an existing global spec option. If provided, names and unit are read from the global spec library. " +
            "If omitted, valueAz/valueEn/valueAr are required to create a new global spec option.")
    private UUID globalOptionId;

    @Schema(description = "Option value in Azerbaijani. Required when globalOptionId is omitted.", example = "5000 mAh")
    private String valueAz;

    @Schema(description = "Option value in English. Required when globalOptionId is omitted.", example = "5000 mAh")
    private String valueEn;

    @Schema(description = "Option value in Arabic. Required when globalOptionId is omitted.", example = "5000 مللي أمبير")
    private String valueAr;

    @Schema(description = "Unit for the option value. Used when globalOptionId is omitted.", example = "Wh")
    private SpecUnit unit;

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
}
