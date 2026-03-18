package com.buyology.ecommerce.store.domain;

import com.buyology.ecommerce.product.domain.ProductVariant;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_product_variants", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "store_product_id", "variant_id" })
}, indexes = {
        @Index(name = "idx_spv_store_product_id", columnList = "store_product_id"),
        @Index(name = "idx_spv_variant_id", columnList = "variant_id"),
        @Index(name = "idx_spv_is_active", columnList = "store_product_id, is_active")
})
public class StoreProductVariant {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_product_id", nullable = false)
    private StoreProduct storeProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    // Store-specific price for this variant
    @Column(name = "store_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal storePrice;

    // Store's own inventory count — independent of global ProductVariant.stock
    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors

    public StoreProductVariant() {
    }

    public StoreProductVariant(StoreProduct storeProduct, ProductVariant variant, BigDecimal storePrice) {
        this.storeProduct = storeProduct;
        this.variant = variant;
        this.storePrice = storePrice;
    }

    // Lifecycle hooks

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) this.isActive = true;
        if (this.stock == null) this.stock = 0;
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

    public StoreProduct getStoreProduct() {
        return storeProduct;
    }

    public void setStoreProduct(StoreProduct storeProduct) {
        this.storeProduct = storeProduct;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public BigDecimal getStorePrice() {
        return storePrice;
    }

    public void setStorePrice(BigDecimal storePrice) {
        this.storePrice = storePrice;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
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
}
