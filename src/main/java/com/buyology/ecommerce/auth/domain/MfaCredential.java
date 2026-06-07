package com.buyology.ecommerce.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

/**
 * TOTP (Google Authenticator) credential for a single user.
 *
 * One row per user. The shared TOTP secret is stored ENCRYPTED at rest
 * (AES-GCM via {@code MfaSecretCipher}); it can never be hashed because the
 * server must reproduce the same one-time codes to verify them.
 *
 * {@code enabled=false} means enrollment has started (a secret exists and a QR
 * was shown) but the user has not yet confirmed a valid code. Only once a code
 * is confirmed does {@code enabled} flip to true and 2FA become mandatory at login.
 */
@Entity
@Table(
    name = "mfa_credentials",
    uniqueConstraints = { @UniqueConstraint(name = "uq_mfa_credentials_user_id", columnNames = "user_id") }
)
public class MfaCredential {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** AES-GCM encrypted base32 TOTP secret (IV-prefixed, base64). */
    @Column(name = "secret_encrypted", nullable = false, length = 512)
    private String secretEncrypted;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public MfaCredential() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (enabled == null) enabled = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getSecretEncrypted() { return secretEncrypted; }
    public void setSecretEncrypted(String secretEncrypted) { this.secretEncrypted = secretEncrypted; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
