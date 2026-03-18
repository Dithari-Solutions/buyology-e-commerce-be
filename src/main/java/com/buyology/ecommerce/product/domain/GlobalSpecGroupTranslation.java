package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "global_spec_group_translations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"group_id", "language"})
})
public class GlobalSpecGroupTranslation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private GlobalSpecGroup group;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    public GlobalSpecGroupTranslation() {
    }

    public GlobalSpecGroupTranslation(GlobalSpecGroup group, String language, String name) {
        this.group = group;
        this.language = language;
        this.name = name;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GlobalSpecGroup getGroup() { return group; }
    public void setGroup(GlobalSpecGroup group) { this.group = group; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
