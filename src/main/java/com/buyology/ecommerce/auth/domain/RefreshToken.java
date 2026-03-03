package com.buyology.ecommerce.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auth_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_token_auth"))
    private AuthCredentials authCredentials;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // ----------------------
    // Constructors
    // ----------------------
    public RefreshToken() {
    }

    public RefreshToken(AuthCredentials authCredentials, String token, Instant expiresAt, String deviceInfo) {
        this.authCredentials = authCredentials;
        this.token = token;
        this.expiresAt = expiresAt;
        this.deviceInfo = deviceInfo;
    }

    // ----------------------
    // Getters & Setters
    // ----------------------
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AuthCredentials getAuthCredential() {
        return authCredentials;
    }

    public void setAuthCredential(AuthCredentials authCredentials) {
        this.authCredentials = authCredentials;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ----------------------
    // Utility methods
    // ----------------------
    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public void revoke() {
        this.revoked = true;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
