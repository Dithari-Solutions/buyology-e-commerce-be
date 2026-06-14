package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin-editable registry of valid product spec codes (e.g. ram, storage, gpu).
 * This is the controlled vocabulary the dashboard offers when tagging products
 * and building the spec library — previously a hardcoded frontend constant.
 *
 * The {@code code} is the machine identifier copied onto GlobalSpecGroup/ProductSpecGroup.
 * {@code filterable} marks the codes the storefront catalog filter recognises today
 * (informational for the admin; the filter engine itself still honours its fixed set).
 */
@Entity
@Table(name = "spec_codes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_spec_codes_code", columnNames = "code")
})
public class SpecCode {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "label_en", length = 100)
    private String labelEn;

    @Column(name = "label_az", length = 100)
    private String labelAz;

    @Column(name = "label_ar", length = 100)
    private String labelAr;

    /** True for codes the storefront catalog filter currently recognises. */
    @Column(name = "filterable", nullable = false)
    private boolean filterable = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabelEn() { return labelEn; }
    public void setLabelEn(String labelEn) { this.labelEn = labelEn; }
    public String getLabelAz() { return labelAz; }
    public void setLabelAz(String labelAz) { this.labelAz = labelAz; }
    public String getLabelAr() { return labelAr; }
    public void setLabelAr(String labelAr) { this.labelAr = labelAr; }
    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
