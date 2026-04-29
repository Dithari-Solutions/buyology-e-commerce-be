package com.buyology.ecommerce.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_setup_tokens", indexes = {
        @Index(name = "idx_supplier_setup_token", columnList = "token")
})
public class SupplierSetupToken {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "used", nullable = false)
    private Boolean used = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (used == null) used = false;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
