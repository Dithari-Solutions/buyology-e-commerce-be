package com.buyology.ecommerce.product.domain;

import com.buyology.ecommerce.common.enums.SpecUnit;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", length = 20)
    private SpecUnit unit;

    @Column(name = "additional_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    // Optional hex color code — only populated for color-type options (e.g. "#C0C0C0")
    @Column(name = "color_code", length = 20)
    private String colorCode;

    // Constructors
    public ProductSpecOption() {
    }

    public ProductSpecOption(ProductSpecGroup group, String value, BigDecimal additionalPrice) {
        this.group = group;
        this.value = value;
        this.additionalPrice = additionalPrice != null ? additionalPrice : BigDecimal.ZERO;
    }

    public ProductSpecOption(ProductSpecGroup group, String value, SpecUnit unit, BigDecimal additionalPrice) {
        this.group = group;
        this.value = value;
        this.unit = unit;
        this.additionalPrice = additionalPrice != null ? additionalPrice : BigDecimal.ZERO;
    }

    public ProductSpecOption(ProductSpecGroup group, String value, BigDecimal additionalPrice, String colorCode) {
        this.group = group;
        this.value = value;
        this.additionalPrice = additionalPrice != null ? additionalPrice : BigDecimal.ZERO;
        this.colorCode = colorCode;
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

    public SpecUnit getUnit() {
        return unit;
    }

    public void setUnit(SpecUnit unit) {
        this.unit = unit;
    }

    public BigDecimal getAdditionalPrice() {
        return additionalPrice;
    }

    public void setAdditionalPrice(BigDecimal additionalPrice) {
        this.additionalPrice = additionalPrice;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }
}
