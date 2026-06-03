package com.buyology.ecommerce.product.dto;

import java.util.List;
import java.util.UUID;

import com.buyology.ecommerce.product.domain.Product.AvailabilityStatus;
import com.buyology.ecommerce.product.domain.Product.ProductType;
import com.buyology.ecommerce.product.domain.Product.RefurbGrade;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Partial-update request for a product. Every field is optional — only the
 * fields that are present (non-null) are applied. Variants, specs and colors
 * are out of scope here; media is edited via {@link #removeMediaIds},
 * {@link #primaryMediaId}, and any uploaded files on the multipart request.
 */
@Schema(description = "Partial update for a product — only non-null fields are applied")
public class UpdateProductRequest {

    private UUID categoryId;
    private UUID brandId;
    private ProductType productType;
    private String status; // ACTIVE / INACTIVE
    private Boolean isRefurbished;
    private RefurbGrade refurbGrade;
    private AvailabilityStatus availabilityStatus;
    private Boolean isSuperDeal;
    private Boolean isLimitedStock;

    @Schema(description = "Partial translations — each language/field is optional")
    private TranslationPatch translations;

    @Schema(description = "IDs of existing media to delete from the product")
    private List<UUID> removeMediaIds;

    @Schema(description = "ID of an existing media item to mark as the primary image")
    private UUID primaryMediaId;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public void setBrandId(UUID brandId) {
        this.brandId = brandId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsRefurbished() {
        return isRefurbished;
    }

    public void setIsRefurbished(Boolean isRefurbished) {
        this.isRefurbished = isRefurbished;
    }

    public RefurbGrade getRefurbGrade() {
        return refurbGrade;
    }

    public void setRefurbGrade(RefurbGrade refurbGrade) {
        this.refurbGrade = refurbGrade;
    }

    public AvailabilityStatus getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public Boolean getIsSuperDeal() {
        return isSuperDeal;
    }

    public void setIsSuperDeal(Boolean isSuperDeal) {
        this.isSuperDeal = isSuperDeal;
    }

    public Boolean getIsLimitedStock() {
        return isLimitedStock;
    }

    public void setIsLimitedStock(Boolean isLimitedStock) {
        this.isLimitedStock = isLimitedStock;
    }

    public TranslationPatch getTranslations() {
        return translations;
    }

    public void setTranslations(TranslationPatch translations) {
        this.translations = translations;
    }

    public List<UUID> getRemoveMediaIds() {
        return removeMediaIds;
    }

    public void setRemoveMediaIds(List<UUID> removeMediaIds) {
        this.removeMediaIds = removeMediaIds;
    }

    public UUID getPrimaryMediaId() {
        return primaryMediaId;
    }

    public void setPrimaryMediaId(UUID primaryMediaId) {
        this.primaryMediaId = primaryMediaId;
    }

    /**
     * Per-language title/description, all optional. A null field is left
     * unchanged; an empty string clears the description.
     */
    public static class TranslationPatch {
        private String titleAz;
        private String titleEn;
        private String titleAr;
        private String descriptionAz;
        private String descriptionEn;
        private String descriptionAr;

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
}
