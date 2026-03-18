package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "brand_translations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"brand_id", "language"})
})
public class BrandTranslation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    public BrandTranslation() {
    }

    public BrandTranslation(Brand brand, String language, String name) {
        this.brand = brand;
        this.language = language;
        this.name = name;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Brand getBrand() { return brand; }
    public void setBrand(Brand brand) { this.brand = brand; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
