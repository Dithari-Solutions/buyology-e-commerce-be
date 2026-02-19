package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_spec_options")
public class ProductSpecOption {

    @Id
    @GeneratedValue
    private UUID id;

    // Many-to-one relationship to ProductSpecGroup
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductSpecGroup group;

    @Column(name = "value", nullable = false, length = 100)
    private String value;

    @Column(name = "additional_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    // Constructors
    public ProductSpecOption() {
    }

    public ProductSpecOption(ProductSpecGroup group, String value, BigDecimal additionalPrice) {
        this.group = group;
        this.value = value;
        this.additionalPrice = additionalPrice != null ? additionalPrice : BigDecimal.ZERO;
    }

    // Lifecycle hook
    @PrePersist
    public void prePersist() {
        if (this.additionalPrice == null) {
            this.additionalPrice = BigDecimal.ZERO;
        }
    }

    // Getters & Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductSpecGroup getGroup() {
        return group;
    }

    public void setGroup(ProductSpecGroup group) {
        this.group = group;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public BigDecimal getAdditionalPrice() {
        return additionalPrice;
    }

    public void setAdditionalPrice(BigDecimal additionalPrice) {
        this.additionalPrice = additionalPrice;
    }
}
