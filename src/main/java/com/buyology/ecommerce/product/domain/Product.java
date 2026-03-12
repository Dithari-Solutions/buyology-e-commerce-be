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

    public Product(ProductCategory category, ProductType productType, Boolean isRefurbished,
            RefurbGrade refurbGrade, BigDecimal basePrice, DiscountType discountType, BigDecimal discountValue,
            String sku, String status) {
        this.category = category;
        this.productType = productType;
        this.isRefurbished = isRefurbished != null ? isRefurbished : false;
        this.refurbGrade = refurbGrade;
        this.basePrice = basePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.sku = sku;
        this.status = status != null ? status : "ACTIVE";
    }

    // Lifecycle hooks
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isRefurbished == null)
            this.isRefurbished = false;
        if (this.status == null)
            this.status = "ACTIVE";
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
}
