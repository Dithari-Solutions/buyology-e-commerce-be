package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, unique = true, length = 255)
    private String sku;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructors
    public ProductVariant() {
    }

    public ProductVariant(Product product, String sku) {
        this.product = product;
        this.sku = sku;
    }

    // Lifecycle hook
    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
