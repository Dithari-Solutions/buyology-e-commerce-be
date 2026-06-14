package com.buyology.ecommerce.product.dto;

import com.buyology.ecommerce.product.domain.Product.AvailabilityStatus;
import com.buyology.ecommerce.product.domain.Product.ProductType;
import com.buyology.ecommerce.product.domain.Product.RefurbGrade;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body for creating a new product with translations, media, variants, and accessories")
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateProductRequest {

    @NotNull(message = "Category ID is required")
    @Schema(description = "UUID of the existing product category", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID categoryId;

    @Schema(description = "UUID of the brand (optional)")
    private UUID brandId;

    @Schema(description = "Availability status of the product", allowableValues = {"IN_STOCK", "OUT_OF_STOCK", "PRE_ORDER"}, defaultValue = "PRE_ORDER")
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.PRE_ORDER;

    @Schema(description = "Mark product as a super deal (shows in super deals section)", defaultValue = "false")
    private Boolean isSuperDeal = false;

    @Schema(description = "Mark product as limited stock", defaultValue = "false")
    private Boolean isLimitedStock = false;

    @NotNull(message = "Product type is required")
    @Schema(description = "Type of the product", allowableValues = {"SIMPLE", "DIY", "ACCESSORY"})
    private ProductType productType;

    @Schema(description = "Optional product SKU. If provided it is used as-is (must be globally unique); " +
            "if omitted, a SKU is auto-generated (DTAX-/DTDX- prefix).", example = "DTAX0993")
    private String sku;

    @Schema(description = "Whether the product is refurbished", example = "false", defaultValue = "false")
    private Boolean isRefurbished = false;

    @Schema(description = "Refurbishment grade — required when isRefurbished is true", allowableValues = {"A", "B", "C"})
    private RefurbGrade refurbGrade;

    @Schema(description = "Product status", example = "ACTIVE", defaultValue = "ACTIVE")
    private String status = "ACTIVE";

    @NotNull(message = "Translations are required")
    @Valid
    @Schema(description = "Required translations in Azerbaijani, English, and Arabic")
    private ProductTranslationRequest translations;

    @Valid
    @Schema(description = "Optional list of spec groups (e.g. RAM, Storage) with their options to create inline during product creation")
    private List<CreateSpecGroupRequest> specs;

    @Valid
    @Schema(description = "Optional list of available colors, each mapped to specific uploaded media files via mediaIndices")
    private List<CreateColorRequest> colors;

    @Valid
    @Schema(description = "Optional list of product variants (e.g. different sizes or colors)")
    private List<CreateVariantRequest> variants;

    @Schema(description = "Optional list of existing product UUIDs to link as accessories to this product")
    private List<UUID> accessoryIds;

    // ========================
    // Getters & Setters
    // ========================

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ProductTranslationRequest getTranslations() {
        return translations;
    }

    public void setTranslations(ProductTranslationRequest translations) {
        this.translations = translations;
    }

    public List<CreateSpecGroupRequest> getSpecs() {
        return specs;
    }

    public void setSpecs(List<CreateSpecGroupRequest> specs) {
        this.specs = specs;
    }

    public List<CreateColorRequest> getColors() {
        return colors;
    }

    public void setColors(List<CreateColorRequest> colors) {
        this.colors = colors;
    }

    public List<CreateVariantRequest> getVariants() {
        return variants;
    }

    public void setVariants(List<CreateVariantRequest> variants) {
        this.variants = variants;
    }

    public List<UUID> getAccessoryIds() {
        return accessoryIds;
    }

    public void setAccessoryIds(List<UUID> accessoryIds) {
        this.accessoryIds = accessoryIds;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public void setBrandId(UUID brandId) {
        this.brandId = brandId;
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
}
