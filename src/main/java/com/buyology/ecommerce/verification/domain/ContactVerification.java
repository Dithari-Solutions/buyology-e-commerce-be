package com.buyology.ecommerce.verification.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A single contact-verification record, keyed by (channel, target).
 *
 * <p>EMAIL verifications are self-rolled via SendGrid: the 6-digit code lives in
 * {@code otpCode} and is checked locally. PHONE verifications are delegated to
 * Twilio Verify — Twilio holds and validates the code, so {@code otpCode} stays
 * null and we only persist the row once the phone has been verified, as proof for
 * the subsequent submit step.</p>
 */
@Entity
@Table(name = "contact_verifications", indexes = {
        @Index(name = "idx_contact_verif_channel_target", columnList = "channel,target"),
        @Index(name = "idx_contact_verif_expires_at", columnList = "expires_at")
})
public class ContactVerification {

    public enum Channel { EMAIL, PHONE }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private Channel channel;

    @Column(name = "target", nullable = false, length = 255)
    private String target;

    /** Set for EMAIL (self-rolled via SendGrid); null for PHONE (Twilio Verify holds the code). */
    @Column(name = "otp_code", length = 6)
    private String otpCode;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isMaxAttemptsReached() {
        return attempts >= 5;
    }

    // ── Getters / setters ────────────────────────────────────────────────────
    public UUID getId() { return id; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
