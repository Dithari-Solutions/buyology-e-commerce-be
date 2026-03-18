package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "global_spec_groups", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
public class GlobalSpecGroup {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Machine-readable identifier — must match the filter param code convention:
     * ram, storage, processor, screen_size, touchable_screen, operating_system, keyboard_language
     */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GlobalSpecGroup() {
    }

    public GlobalSpecGroup(String code) {
        this.code = code;
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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
