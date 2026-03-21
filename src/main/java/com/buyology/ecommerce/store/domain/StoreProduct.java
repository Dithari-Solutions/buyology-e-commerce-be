package com.buyology.ecommerce.store.domain;

import com.buyology.ecommerce.product.domain.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_products", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "store_id", "product_id" })
}, indexes = {
        @Index(name = "idx_store_products_store_id", columnList = "store_id"),
        @Index(name = "idx_store_products_product_id", columnList = "product_id"),
        @Index(name = "idx_store_products_deleted_at", columnList = "deleted_at"),
        @Index(name = "idx_store_products_active", columnList = "store_id, is_active")
})
public class StoreProduct {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Store-specific price — all pricing lives here, not on the global product
    @Column(name = "store_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal storePrice;

    // Reuses Product.DiscountType (FIXED / PERCENTAGE) — no duplication
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20)
    private Product.DiscountType discountType;

    @Column(name = "discount_value", precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Constructors

    public StoreProduct() {
    }

    public StoreProduct(Store store, Product product, BigDecimal storePrice) {
        this.store = store;
        this.product = product;
        this.storePrice = storePrice;
    }

    // Lifecycle hooks

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) this.isActive = true;
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

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getStorePrice() {
        return storePrice;
    }

    public void setStorePrice(BigDecimal storePrice) {
        this.storePrice = storePrice;
    }

    public Product.DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(Product.DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
