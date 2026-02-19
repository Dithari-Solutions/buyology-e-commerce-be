package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "product_spec_group_translations",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"group_id", "language"})
       })
public class ProductSpecGroupTranslation {

    @Id
    @GeneratedValue
    private UUID id;

    // Many-to-one relationship to ProductSpecGroup
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductSpecGroup group;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // Constructors
    public ProductSpecGroupTranslation() {
    }

    public ProductSpecGroupTranslation(ProductSpecGroup group, String language, String name) {
        this.group = group;
        this.language = language;
        this.name = name;
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
