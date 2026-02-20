package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Full product response including translations, media, variants, and accessories")
public class ProductResponse {

    private UUID id;
    private UUID categoryId;
    private String productType;
    private Boolean isRefurbished;
    private String refurbGrade;
    private BigDecimal basePrice;
    private String sku;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    private List<TranslationDto> translations;
    private List<MediaDto> media;
    private List<VariantDto> variants;
    private List<UUID> accessoryIds;

    // ========================
    // Nested DTOs
    // ========================

    @Schema(description = "Product translation in a single language")
    public static class TranslationDto {

        private String language;
        private String title;
        private String description;

        public TranslationDto() {
        }

        public TranslationDto(String language, String title, String description) {
            this.language = language;
            this.title = title;
            this.description = description;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
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

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getMediaType() {
            return mediaType;
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        public void setThumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
        }

        public Boolean getIsPrimary() {
            return isPrimary;
        }

        public void setIsPrimary(Boolean isPrimary) {
            this.isPrimary = isPrimary;
        }

        public Integer getOrderIndex() {
            return orderIndex;
        }

        public void setOrderIndex(Integer orderIndex) {
            this.orderIndex = orderIndex;
        }
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

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }

        public List<UUID> getSpecOptionIds() {
            return specOptionIds;
        }

        public void setSpecOptionIds(List<UUID> specOptionIds) {
            this.specOptionIds = specOptionIds;
        }
    }

    // ========================
    // Getters & Setters
    // ========================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Boolean getIsRefurbished() {
        return isRefurbished;
    }

    public void setIsRefurbished(Boolean isRefurbished) {
        this.isRefurbished = isRefurbished;
    }

    public String getRefurbGrade() {
        return refurbGrade;
    }

    public void setRefurbGrade(String refurbGrade) {
        this.refurbGrade = refurbGrade;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TranslationDto> getTranslations() {
        return translations;
    }

    public void setTranslations(List<TranslationDto> translations) {
        this.translations = translations;
    }

    public List<MediaDto> getMedia() {
        return media;
    }

    public void setMedia(List<MediaDto> media) {
        this.media = media;
    }

    public List<VariantDto> getVariants() {
        return variants;
    }

    public void setVariants(List<VariantDto> variants) {
        this.variants = variants;
    }

    public List<UUID> getAccessoryIds() {
        return accessoryIds;
    }

    public void setAccessoryIds(List<UUID> accessoryIds) {
        this.accessoryIds = accessoryIds;
    }
}
