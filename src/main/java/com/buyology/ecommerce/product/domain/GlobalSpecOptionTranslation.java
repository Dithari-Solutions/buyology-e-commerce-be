package com.buyology.ecommerce.product.domain;

import com.buyology.ecommerce.common.enums.Language;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "global_spec_option_translations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"option_id", "language"})
})
public class GlobalSpecOptionTranslation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private GlobalSpecOption option;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Language language;

    @Column(name = "value", nullable = false, length = 100)
    private String value;

    public GlobalSpecOptionTranslation() {
    }

    public GlobalSpecOptionTranslation(GlobalSpecOption option, Language language, String value) {
        this.option = option;
        this.language = language;
        this.value = value;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GlobalSpecOption getOption() { return option; }
    public void setOption(GlobalSpecOption option) { this.option = option; }

    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
