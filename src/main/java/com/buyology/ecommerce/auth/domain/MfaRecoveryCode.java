package com.buyology.ecommerce.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

/**
 * A single-use recovery code for a user's 2FA. Generated (10 at a time) when the
 * user confirms enrollment and shown to them exactly once. Only the SHA-256 hash
 * is persisted — the plaintext is never stored. Each code can be consumed once to
 * sign in when the authenticator device is unavailable.
 */
@Entity
@Table(
    name = "mfa_recovery_codes",
    indexes = { @Index(name = "idx_mfa_recovery_codes_user_id", columnList = "user_id") }
)
public class MfaRecoveryCode {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hex of the plaintext recovery code (uppercased, no separators). */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used", nullable = false)
    private Boolean used = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MfaRecoveryCode() {}

    public MfaRecoveryCode(UUID userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (used == null) used = false;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
