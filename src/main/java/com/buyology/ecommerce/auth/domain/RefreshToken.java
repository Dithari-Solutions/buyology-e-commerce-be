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
    private AuthCredentials authCredential;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    /**
     * Which client this session belongs to — "web", "dashboard" or "mobile" — captured once when
     * the token is issued and reused for every access token rotated out of it.
     *
     * <p>It is the access token's audience, and for a privileged account the audience decides
     * whether the request is authenticated at all: {@code JwtAuthenticationFilter} drops the
     * authentication of any admin/supplier principal whose token is not audience "dashboard".
     * Deriving it from the X-Client-Type header on every call meant one refresh sent without that
     * header re-minted an admin's token as "web" and signed them out. Stored here, rotation cannot
     * change what the session is.
     *
     * <p>Null on sessions issued before this column existed; those fall back to the header.
     */
    @Column(name = "client_type", length = 20)
    private String clientType;

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
        this.authCredential = authCredentials;
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
        return authCredential;
    }

    public void setAuthCredential(AuthCredentials authCredentials) {
        this.authCredential = authCredentials;
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

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
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
