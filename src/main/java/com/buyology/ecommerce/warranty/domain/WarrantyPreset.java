package com.buyology.ecommerce.warranty.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A named warranty length an admin picks for a product instead of typing it, e.g. "Laptop" for
 * 12 months or "Accessory" for 6.
 *
 * <p>Products that use a preset copy its months into {@code products.warranty_months} and keep a
 * reference to it, so changing the preset moves every one of them with it (see
 * {@code WarrantyPresetService#update}). Cards already issued never change: they carry their own
 * dates.
 */
@Entity
@Table(name = "warranty_presets")
public class WarrantyPreset {

    @Id
    @GeneratedValue
    private UUID id;

    // Unique case-insensitively (index uq_warranty_presets_name); the service checks first so the
    // admin gets a readable message rather than a constraint violation.
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "duration_months", nullable = false)
    private int durationMonths;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WarrantyPreset() {
    }

    public WarrantyPreset(String name, int durationMonths) {
        this.name = name;
        this.durationMonths = durationMonths;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
