package com.buyology.ecommerce.auth.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(
    name = "email_otp",
    indexes = {
        @Index(name = "idx_email_otp_email", columnList = "email"),
        @Index(name = "idx_email_otp_expires_at", columnList = "expires_at"),
        @Index(name = "idx_email_otp_type", columnList = "type")
    }
)
public class EmailOtp {

    public enum OtpType {
        SIGNUP,
        PASSWORD_RESET
    }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * Discriminates between signup and password-reset OTPs so the two flows
     * never consume each other's tokens. Defaults to SIGNUP for backward compatibility.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private OtpType type = OtpType.SIGNUP;

    /**
     * Pre-hashed password stored temporarily until OTP is verified (signup flow).
     * Stored as empty string "" for PASSWORD_RESET type — not needed until reset step.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash = "";

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private Boolean used = false;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (used == null) used = false;
        if (attempts == null) attempts = 0;
        if (type == null) type = OtpType.SIGNUP;
        if (passwordHash == null) passwordHash = "";
    }

    // ── Domain helpers ────────────────────────────────────────────────────────

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isMaxAttemptsReached() {
        return attempts >= 5;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public OtpType getType() { return type; }
    public void setType(OtpType type) { this.type = type; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public Instant getCreatedAt() { return createdAt; }
}
