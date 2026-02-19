package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_variants",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"sku"})
       })
public class ProductVariant {

    @Id
    @GeneratedValue
    private UUID id;

    // Many-to-one relationship to Product
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, unique = true, length = 255)
    private String sku;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructors
    public ProductVariant() {
    }

    public ProductVariant(Product product, String sku, BigDecimal price, Integer stock) {
        this.product = product;
        this.sku = sku;
        this.price = price;
        this.stock = stock != null ? stock : 0;
    }

    // Lifecycle hook
    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        if (this.stock == null) {
            this.stock = 0;
        }
    }

    // Getters & Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
