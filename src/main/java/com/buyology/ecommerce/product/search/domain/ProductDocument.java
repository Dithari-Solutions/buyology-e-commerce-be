package com.buyology.ecommerce.product.search.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;
import java.util.UUID;

@Document(indexName = "products")
public class ProductDocument {

    @Id
    private UUID id;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Nested)
    private List<Translation> translations;

    @Field(type = FieldType.Keyword)
    private UUID categoryId;

    @Field(type = FieldType.Text)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private UUID brandId;

    @Field(type = FieldType.Text)
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String productType;

    @Field(type = FieldType.Keyword)
    private String availabilityStatus;

    @Field(type = FieldType.Boolean)
    private Boolean isSuperDeal;

    @Field(type = FieldType.Boolean)
    private Boolean isLimitedStock;

    @Field(type = FieldType.Boolean)
    private Boolean isRefurbished;

    public static class Translation {
        @Field(type = FieldType.Keyword)
        private String language;

        @Field(type = FieldType.Text, analyzer = "standard")
        private String title;

        @Field(type = FieldType.Text, analyzer = "standard")
        private String description;

        @Field(type = FieldType.Keyword)
        private String slug;

        // Getters and Setters
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Translation> getTranslations() { return translations; }
    public void setTranslations(List<Translation> translations) { this.translations = translations; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public UUID getBrandId() { return brandId; }
    public void setBrandId(UUID brandId) { this.brandId = brandId; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public Boolean getIsSuperDeal() { return isSuperDeal; }
    public void setIsSuperDeal(Boolean isSuperDeal) { this.isSuperDeal = isSuperDeal; }
    public Boolean getIsLimitedStock() { return isLimitedStock; }
    public void setIsLimitedStock(Boolean isLimitedStock) { this.isLimitedStock = isLimitedStock; }
    public Boolean getIsRefurbished() { return isRefurbished; }
    public void setIsRefurbished(Boolean isRefurbished) { this.isRefurbished = isRefurbished; }
}
