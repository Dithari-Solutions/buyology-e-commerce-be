package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Category translation content in all three required languages: Azerbaijani, English, and Arabic")
public class CategoryTranslationRequest {

    // ========================
    // Azerbaijani
    // ========================

    @NotBlank(message = "Azerbaijani name is required")
    @Size(max = 255, message = "Azerbaijani name must not exceed 255 characters")
    @Schema(description = "Category name in Azerbaijani", example = "Elektronika")
    private String nameAz;

    @NotBlank(message = "Azerbaijani description is required")
    @Schema(description = "Category description in Azerbaijani", example = "Bütün elektron məhsullar")
    private String descriptionAz;

    @NotBlank(message = "Azerbaijani slug is required")
    @Size(max = 255, message = "Azerbaijani slug must not exceed 255 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Azerbaijani slug must be lowercase alphanumeric with hyphens")
    @Schema(description = "URL-friendly slug in Azerbaijani", example = "elektronika")
    private String slugAz;

    // ========================
    // English
    // ========================

    @NotBlank(message = "English name is required")
    @Size(max = 255, message = "English name must not exceed 255 characters")
    @Schema(description = "Category name in English", example = "Electronics")
    private String nameEn;

    @NotBlank(message = "English description is required")
    @Schema(description = "Category description in English", example = "All electronic products")
    private String descriptionEn;

    @NotBlank(message = "English slug is required")
    @Size(max = 255, message = "English slug must not exceed 255 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "English slug must be lowercase alphanumeric with hyphens")
    @Schema(description = "URL-friendly slug in English", example = "electronics")
    private String slugEn;

    // ========================
    // Arabic
    // ========================

    @NotBlank(message = "Arabic name is required")
    @Size(max = 255, message = "Arabic name must not exceed 255 characters")
    @Schema(description = "Category name in Arabic", example = "إلكترونيات")
    private String nameAr;

    @NotBlank(message = "Arabic description is required")
    @Schema(description = "Category description in Arabic", example = "جميع المنتجات الإلكترونية")
    private String descriptionAr;

    @NotBlank(message = "Arabic slug is required")
    @Size(max = 255, message = "Arabic slug must not exceed 255 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Arabic slug must be lowercase alphanumeric with hyphens")
    @Schema(description = "URL-friendly slug in Arabic", example = "electronics-ar")
    private String slugAr;

    // ========================
    // Getters & Setters
    // ========================

    public String getNameAz() {
        return nameAz;
    }

    public void setNameAz(String nameAz) {
        this.nameAz = nameAz;
    }

    public String getDescriptionAz() {
        return descriptionAz;
    }

    public void setDescriptionAz(String descriptionAz) {
        this.descriptionAz = descriptionAz;
    }

    public String getSlugAz() {
        return slugAz;
    }

    public void setSlugAz(String slugAz) {
        this.slugAz = slugAz;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getSlugEn() {
        return slugEn;
    }

    public void setSlugEn(String slugEn) {
        this.slugEn = slugEn;
    }

    public String getNameAr() {
        return nameAr;
    }

    public void setNameAr(String nameAr) {
        this.nameAr = nameAr;
    }

    public String getDescriptionAr() {
        return descriptionAr;
    }

    public void setDescriptionAr(String descriptionAr) {
        this.descriptionAr = descriptionAr;
    }

    public String getSlugAr() {
        return slugAr;
    }

    public void setSlugAr(String slugAr) {
        this.slugAr = slugAr;
    }
}
