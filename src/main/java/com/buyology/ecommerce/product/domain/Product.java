package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(columnNames = "sku")
})
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    // Many-to-one relationship to category
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    // Optional brand
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    // Product type enum
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 20)
    private ProductType productType;

    @Column(name = "is_refurbished", nullable = false)
    private Boolean isRefurbished = false;

    // Refurbishment grade enum
    @Enumerated(EnumType.STRING)
    @Column(name = "refurb_grade", length = 10)
    private RefurbGrade refurbGrade;

    @Column(name = "base_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "sku", nullable = false, unique = true, length = 255)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", length = 20)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.IN_STOCK;

    @Column(name = "is_super_deal", nullable = false)
    private Boolean isSuperDeal = false;

    @Column(name = "is_limited_stock", nullable = false)
    private Boolean isLimitedStock = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Enums
    public enum ProductType {
        SIMPLE,
        DIY,
        ACCESSORY
    }

    public enum AvailabilityStatus {
        IN_STOCK,
        OUT_OF_STOCK,
        PRE_ORDER
    }

    public enum RefurbGrade {
        A,
        B,
        C
    }

    public enum DiscountType {
        FIXED,
        PERCENTAGE
    }

    // Constructors
    public Product() {
    }

    public Product(ProductCategory category, Brand brand, ProductType productType, Boolean isRefurbished,
            RefurbGrade refurbGrade, BigDecimal basePrice, DiscountType discountType, BigDecimal discountValue,
            String sku, String status, AvailabilityStatus availabilityStatus,
            Boolean isSuperDeal, Boolean isLimitedStock) {
        this.category = category;
        this.brand = brand;
        this.productType = productType;
        this.isRefurbished = isRefurbished != null ? isRefurbished : false;
        this.refurbGrade = refurbGrade;
        this.basePrice = basePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.sku = sku;
        this.status = status != null ? status : "ACTIVE";
        this.availabilityStatus = availabilityStatus != null ? availabilityStatus : AvailabilityStatus.IN_STOCK;
        this.isSuperDeal = isSuperDeal != null ? isSuperDeal : false;
        this.isLimitedStock = isLimitedStock != null ? isLimitedStock : false;
    }

    // Lifecycle hooks
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isRefurbished == null) this.isRefurbished = false;
        if (this.status == null) this.status = "ACTIVE";
        if (this.availabilityStatus == null) this.availabilityStatus = AvailabilityStatus.IN_STOCK;
        if (this.isSuperDeal == null) this.isSuperDeal = false;
        if (this.isLimitedStock == null) this.isLimitedStock = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
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

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
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
