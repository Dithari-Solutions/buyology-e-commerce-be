package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Request body for updating an existing product category. All fields are optional — only provided fields will be updated.")
public class UpdateCategoryRequest {

    @Schema(description = "New parent category ID. Pass null to make this a root category.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
    private UUID parentId;

    @Schema(description = "Category status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
    private String status;

    @Schema(description = "Updated translations. If provided, all language fields inside must be filled.")
    private Translations translations;

    // ========================
    // Nested translations DTO
    // ========================

    @Schema(description = "Updated translation content per language")
    public static class Translations {

        @Size(max = 255, message = "Azerbaijani name must not exceed 255 characters")
        @Schema(description = "Category name in Azerbaijani", example = "Elektronika")
        private String nameAz;

        @Schema(description = "Category description in Azerbaijani", example = "Bütün elektron məhsullar")
        private String descriptionAz;

        @Size(max = 255, message = "Azerbaijani slug must not exceed 255 characters")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Azerbaijani slug must be lowercase alphanumeric with hyphens")
        @Schema(description = "URL-friendly slug in Azerbaijani", example = "elektronika")
        private String slugAz;

        @Size(max = 255, message = "English name must not exceed 255 characters")
        @Schema(description = "Category name in English", example = "Electronics")
        private String nameEn;

        @Schema(description = "Category description in English", example = "All electronic products")
        private String descriptionEn;

        @Size(max = 255, message = "English slug must not exceed 255 characters")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "English slug must be lowercase alphanumeric with hyphens")
        @Schema(description = "URL-friendly slug in English", example = "electronics")
        private String slugEn;

        @Size(max = 255, message = "Arabic name must not exceed 255 characters")
        @Schema(description = "Category name in Arabic", example = "إلكترونيات")
        private String nameAr;

        @Schema(description = "Category description in Arabic", example = "جميع المنتجات الإلكترونية")
        private String descriptionAr;

        @Size(max = 255, message = "Arabic slug must not exceed 255 characters")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Arabic slug must be lowercase alphanumeric with hyphens")
        @Schema(description = "URL-friendly slug in Arabic", example = "electronics-ar")
        private String slugAr;

        public String getNameAz() { return nameAz; }
        public void setNameAz(String nameAz) { this.nameAz = nameAz; }

        public String getDescriptionAz() { return descriptionAz; }
        public void setDescriptionAz(String descriptionAz) { this.descriptionAz = descriptionAz; }

        public String getSlugAz() { return slugAz; }
        public void setSlugAz(String slugAz) { this.slugAz = slugAz; }

        public String getNameEn() { return nameEn; }
        public void setNameEn(String nameEn) { this.nameEn = nameEn; }

        public String getDescriptionEn() { return descriptionEn; }
        public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

        public String getSlugEn() { return slugEn; }
        public void setSlugEn(String slugEn) { this.slugEn = slugEn; }

        public String getNameAr() { return nameAr; }
        public void setNameAr(String nameAr) { this.nameAr = nameAr; }

        public String getDescriptionAr() { return descriptionAr; }
        public void setDescriptionAr(String descriptionAr) { this.descriptionAr = descriptionAr; }

        public String getSlugAr() { return slugAr; }
        public void setSlugAr(String slugAr) { this.slugAr = slugAr; }
    }

    // ========================
    // Getters & Setters
    // ========================

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Translations getTranslations() { return translations; }
    public void setTranslations(Translations translations) { this.translations = translations; }
}
