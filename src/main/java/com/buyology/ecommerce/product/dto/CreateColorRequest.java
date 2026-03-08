package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "A color variant of the product with its own set of media files")
public class CreateColorRequest {

    @NotBlank(message = "Color localKey is required")
    @Schema(description = "Unique key within this request used by variants to reference this color", example = "color-silver")
    private String localKey;

    @NotBlank(message = "Color value (AZ) is required")
    @Schema(description = "Color name in Azerbaijani", example = "Gümüşü")
    private String valueAz;

    @NotBlank(message = "Color value (EN) is required")
    @Schema(description = "Color name in English", example = "Silver")
    private String valueEn;

    @NotBlank(message = "Color value (AR) is required")
    @Schema(description = "Color name in Arabic", example = "فضي")
    private String valueAr;

    @Schema(description = "Hex color code for UI display (optional)", example = "#C0C0C0")
    private String colorCode;

    @Schema(description = "Indices into the uploaded mediaFiles list that belong to this color (0-based)", example = "[0, 1]")
    private List<Integer> mediaIndices;

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

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public List<Integer> getMediaIndices() {
        return mediaIndices;
    }

    public void setMediaIndices(List<Integer> mediaIndices) {
        this.mediaIndices = mediaIndices;
    }
}
