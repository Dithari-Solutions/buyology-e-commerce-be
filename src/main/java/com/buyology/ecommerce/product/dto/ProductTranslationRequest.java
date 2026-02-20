package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Product translation content in all three required languages: Azerbaijani, English, and Arabic")
public class ProductTranslationRequest {

    @NotBlank(message = "Azerbaijani title is required")
    @Size(max = 255, message = "Azerbaijani title must not exceed 255 characters")
    @Schema(description = "Product title in Azerbaijani", example = "Yeni Məhsul")
    private String titleAz;

    @NotBlank(message = "English title is required")
    @Size(max = 255, message = "English title must not exceed 255 characters")
    @Schema(description = "Product title in English", example = "New Product")
    private String titleEn;

    @NotBlank(message = "Arabic title is required")
    @Size(max = 255, message = "Arabic title must not exceed 255 characters")
    @Schema(description = "Product title in Arabic", example = "منتج جديد")
    private String titleAr;

    @NotBlank(message = "Azerbaijani description is required")
    @Schema(description = "Product description in Azerbaijani", example = "Bu yeni məhsulun ətraflı təsviridir")
    private String descriptionAz;

    @NotBlank(message = "English description is required")
    @Schema(description = "Product description in English", example = "This is the detailed description of the new product")
    private String descriptionEn;

    @NotBlank(message = "Arabic description is required")
    @Schema(description = "Product description in Arabic", example = "هذا وصف تفصيلي للمنتج الجديد")
    private String descriptionAr;

    // ========================
    // Getters & Setters
    // ========================

    public String getTitleAz() {
        return titleAz;
    }

    public void setTitleAz(String titleAz) {
        this.titleAz = titleAz;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getTitleAr() {
        return titleAr;
    }

    public void setTitleAr(String titleAr) {
        this.titleAr = titleAr;
    }

    public String getDescriptionAz() {
        return descriptionAz;
    }

    public void setDescriptionAz(String descriptionAz) {
        this.descriptionAz = descriptionAz;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getDescriptionAr() {
        return descriptionAr;
    }

    public void setDescriptionAr(String descriptionAr) {
        this.descriptionAr = descriptionAr;
    }
}
