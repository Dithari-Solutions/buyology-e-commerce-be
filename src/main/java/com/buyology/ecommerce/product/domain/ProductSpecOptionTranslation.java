package com.buyology.ecommerce.product.domain;

import java.util.UUID;
import jakarta.persistence.*;
import com.buyology.ecommerce.common.enums.Language;

@Entity
@Table(name = "product_spec_option_translations", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "option_id", "language" })
})
public class ProductSpecOptionTranslation {

    @Id
    @GeneratedValue
    private UUID id;

    // Many-to-one relationship to ProductSpecOption
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private ProductSpecOption option;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Language language;

    @Column(name = "value", nullable = false, length = 100)
    private String value;

    // Constructors
    public ProductSpecOptionTranslation() {
    }

    public ProductSpecOptionTranslation(ProductSpecOption option, Language language, String value) {
        this.option = option;
        this.language = language;
        this.value = value;
    }

    // Getters & Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductSpecOption getOption() {
        return option;
    }

    public void setOption(ProductSpecOption option) {
        this.option = option;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
