package com.buyology.ecommerce.product.dto;

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
    private String productType;
    private Boolean isRefurbished;
    private String refurbGrade;
    private BigDecimal basePrice;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal effectivePrice;
    private String sku;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    private String title;
    private String description;
    private String slug;
    private List<MediaDto> media;
    private List<SpecGroupDto> specs;
    private List<ColorOptionDto> colors;
    private List<VariantDto> variants;
    private List<UUID> accessoryIds;

    // ========================
    // Nested DTOs
    // ========================

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
        private BigDecimal additionalPrice;

        public SpecOptionDto() {
        }

        public SpecOptionDto(UUID id, String value, BigDecimal additionalPrice) {
            this.id = id;
            this.value = value;
            this.additionalPrice = additionalPrice;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
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

    @Schema(description = "Product variant with its linked spec option IDs")
    public static class VariantDto {

        private UUID id;
        private String sku;
        private BigDecimal price;
        private Integer stock;
        private List<UUID> specOptionIds;

        public VariantDto() {
        }

        public VariantDto(UUID id, String sku, BigDecimal price, Integer stock, List<UUID> specOptionIds) {
            this.id = id;
            this.sku = sku;
            this.price = price;
            this.stock = stock;
            this.specOptionIds = specOptionIds;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
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
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public Boolean getIsRefurbished() { return isRefurbished; }
    public void setIsRefurbished(Boolean isRefurbished) { this.isRefurbished = isRefurbished; }
    public String getRefurbGrade() { return refurbGrade; }
    public void setRefurbGrade(String refurbGrade) { this.refurbGrade = refurbGrade; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getEffectivePrice() { return effectivePrice; }
    public void setEffectivePrice(BigDecimal effectivePrice) { this.effectivePrice = effectivePrice; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
