package com.buyology.ecommerce.product.dto;

import com.buyology.ecommerce.common.enums.SpecUnit;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Full product response including translations, media, specs, colors, variants, and accessories")
public class ProductResponse {

    private UUID id;
    private UUID categoryId;
    private UUID brandId;
    private String brandName;
    private String productType;
    private Boolean isRefurbished;
    private String refurbGrade;
    private String sku;
    private String availabilityStatus;
    private Boolean isSuperDeal;
    private Boolean isLimitedStock;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;

    private String title;
    private String description;
    private String slug;

    /**
     * The store that offers the lowest price in the requested country.
     * The frontend must pass this as {@code storeId} when calling POST /api/cart/{id}/items.
     * Null when no countryCode was supplied or the product isn't listed in that country.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID storeId;

    /**
     * Lowest price for this product in the requested country's stores,
     * already converted to the requested display currency.
     * Null when no countryCode was supplied or the product isn't listed in that country.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal storePrice;

    /**
     * ISO 4217 currency code the storePrice is expressed in (e.g. "AZN", "AED").
     * Null when storePrice is null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String currency;

    /**
     * Whether this product is available in at least one store in the requested country.
     * Null when no countryCode was supplied.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean availableInSelectedCountry;

    /**
     * True = the primary store for this product is within ≤12.5 km of the customer's location
     * (express delivery, ≤30 min). False = regular shipping.
     * Null when no lat/lng was supplied.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean expressDelivery;

    /**
     * All stores carrying this product in the requested country, each with their own price
     * and delivery badge. Null when no countryCode was supplied.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<StoreOptionDto> storeOptions;

    private List<MediaDto> media;
    private List<SpecGroupDto> specs;
    private List<ColorOptionDto> colors;
    private List<VariantDto> variants;
    private List<UUID> accessoryIds;

    // ========================
    // Nested DTOs
    // ========================

    @Schema(description = "A store option for this product — price and delivery badge per store")
    public static class StoreOptionDto {

        private UUID storeId;
        private BigDecimal storePrice;
        private String currency;
        private Boolean expressDelivery;

        public StoreOptionDto() {}

        public StoreOptionDto(UUID storeId, BigDecimal storePrice, String currency, Boolean expressDelivery) {
            this.storeId = storeId;
            this.storePrice = storePrice;
            this.currency = currency;
            this.expressDelivery = expressDelivery;
        }

        public UUID getStoreId() { return storeId; }
        public void setStoreId(UUID storeId) { this.storeId = storeId; }
        public BigDecimal getStorePrice() { return storePrice; }
        public void setStorePrice(BigDecimal storePrice) { this.storePrice = storePrice; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public Boolean getExpressDelivery() { return expressDelivery; }
        public void setExpressDelivery(Boolean expressDelivery) { this.expressDelivery = expressDelivery; }
    }

    @Schema(description = "Product media item (image or video)")
    public static class MediaDto {

        private UUID id;
        private String mediaType;
        private String url;
        private String thumbnailUrl;
        private Boolean isPrimary;
        private Integer orderIndex;

        public MediaDto() {
        }

        public MediaDto(UUID id, String mediaType, String url, String thumbnailUrl,
                Boolean isPrimary, Integer orderIndex) {
            this.id = id;
            this.mediaType = mediaType;
            this.url = url;
            this.thumbnailUrl = thumbnailUrl;
            this.isPrimary = isPrimary;
            this.orderIndex = orderIndex;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getThumbnailUrl() { return thumbnailUrl; }
        public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
        public Boolean getIsPrimary() { return isPrimary; }
        public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
        public Integer getOrderIndex() { return orderIndex; }
        public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    }

    @Schema(description = "Spec option belonging to a spec group — additionalPrice is 0 for base specs, > 0 for upgrade options")
    public static class SpecOptionDto {

        private UUID id;
        private String value;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private SpecUnit unit;
        private BigDecimal additionalPrice;

        public SpecOptionDto() {
        }

        public SpecOptionDto(UUID id, String value, SpecUnit unit, BigDecimal additionalPrice) {
            this.id = id;
            this.value = value;
            this.unit = unit;
            this.additionalPrice = additionalPrice;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public SpecUnit getUnit() { return unit; }
        public void setUnit(SpecUnit unit) { this.unit = unit; }
        public BigDecimal getAdditionalPrice() { return additionalPrice; }
        public void setAdditionalPrice(BigDecimal additionalPrice) { this.additionalPrice = additionalPrice; }
    }

    @Schema(description = "Spec group with its options")
    public static class SpecGroupDto {

        private UUID id;
        private String code;
        private String name;
        private List<SpecOptionDto> options;

        public SpecGroupDto() {
        }

        public SpecGroupDto(UUID id, String code, String name, List<SpecOptionDto> options) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.options = options;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<SpecOptionDto> getOptions() { return options; }
        public void setOptions(List<SpecOptionDto> options) { this.options = options; }
    }

    @Schema(description = "A color option with its own set of media")
    public static class ColorOptionDto {

        private UUID id;
        private String value;
        private String colorCode;
        private List<MediaDto> media;

        public ColorOptionDto() {
        }

        public ColorOptionDto(UUID id, String value, String colorCode, List<MediaDto> media) {
            this.id = id;
            this.value = value;
            this.colorCode = colorCode;
            this.media = media;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getColorCode() { return colorCode; }
        public void setColorCode(String colorCode) { this.colorCode = colorCode; }
        public List<MediaDto> getMedia() { return media; }
        public void setMedia(List<MediaDto> media) { this.media = media; }
    }

    @Schema(description = "Product variant definition — price and stock are set per-store by store admins")
    public static class VariantDto {

        private UUID id;
        private String sku;
        private List<UUID> specOptionIds;

        public VariantDto() {
        }

        public VariantDto(UUID id, String sku, List<UUID> specOptionIds) {
            this.id = id;
            this.sku = sku;
            this.specOptionIds = specOptionIds;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public List<UUID> getSpecOptionIds() { return specOptionIds; }
        public void setSpecOptionIds(List<UUID> specOptionIds) { this.specOptionIds = specOptionIds; }
    }

    // ========================
    // Getters & Setters
    // ========================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public UUID getBrandId() { return brandId; }
    public void setBrandId(UUID brandId) { this.brandId = brandId; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public Boolean getIsSuperDeal() { return isSuperDeal; }
    public void setIsSuperDeal(Boolean isSuperDeal) { this.isSuperDeal = isSuperDeal; }
    public Boolean getIsLimitedStock() { return isLimitedStock; }
    public void setIsLimitedStock(Boolean isLimitedStock) { this.isLimitedStock = isLimitedStock; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public Boolean getIsRefurbished() { return isRefurbished; }
    public void setIsRefurbished(Boolean isRefurbished) { this.isRefurbished = isRefurbished; }
    public String getRefurbGrade() { return refurbGrade; }
    public void setRefurbGrade(String refurbGrade) { this.refurbGrade = refurbGrade; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public BigDecimal getStorePrice() { return storePrice; }
    public void setStorePrice(BigDecimal storePrice) { this.storePrice = storePrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getAvailableInSelectedCountry() { return availableInSelectedCountry; }
    public void setAvailableInSelectedCountry(Boolean availableInSelectedCountry) { this.availableInSelectedCountry = availableInSelectedCountry; }
    public Boolean getExpressDelivery() { return expressDelivery; }
    public void setExpressDelivery(Boolean expressDelivery) { this.expressDelivery = expressDelivery; }
    public List<StoreOptionDto> getStoreOptions() { return storeOptions; }
    public void setStoreOptions(List<StoreOptionDto> storeOptions) { this.storeOptions = storeOptions; }
    public List<MediaDto> getMedia() { return media; }
    public void setMedia(List<MediaDto> media) { this.media = media; }
    public List<SpecGroupDto> getSpecs() { return specs; }
    public void setSpecs(List<SpecGroupDto> specs) { this.specs = specs; }
    public List<ColorOptionDto> getColors() { return colors; }
    public void setColors(List<ColorOptionDto> colors) { this.colors = colors; }
    public List<VariantDto> getVariants() { return variants; }
    public void setVariants(List<VariantDto> variants) { this.variants = variants; }
    public List<UUID> getAccessoryIds() { return accessoryIds; }
    public void setAccessoryIds(List<UUID> accessoryIds) { this.accessoryIds = accessoryIds; }
}
