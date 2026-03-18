package com.buyology.ecommerce.product.domain;

import com.buyology.ecommerce.common.enums.SpecUnit;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "global_spec_options")
public class GlobalSpecOption {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private GlobalSpecGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", length = 20)
    private SpecUnit unit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GlobalSpecOption() {
    }

    public GlobalSpecOption(GlobalSpecGroup group, SpecUnit unit) {
        this.group = group;
        this.unit = unit;
    }

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
    public void setId(UUID id) { this.id = id; }

    public GlobalSpecGroup getGroup() { return group; }
    public void setGroup(GlobalSpecGroup group) { this.group = group; }

    public SpecUnit getUnit() { return unit; }
    public void setUnit(SpecUnit unit) { this.unit = unit; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
