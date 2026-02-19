package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "product_variant_options",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"variant_id", "option_id"})
       })
public class ProductVariantOption {

    @Id
    @GeneratedValue
    private UUID id;

    // Many-to-one relationship to ProductVariant
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    // Many-to-one relationship to ProductSpecOption
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private ProductSpecOption option;

    // Constructors
    public ProductVariantOption() {
    }

    public ProductVariantOption(ProductVariant variant, ProductSpecOption option) {
        this.variant = variant;
        this.option = option;
    }

    // Getters & Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public ProductSpecOption getOption() {
        return option;
    }

    public void setOption(ProductSpecOption option) {
        this.option = option;
    }
}
