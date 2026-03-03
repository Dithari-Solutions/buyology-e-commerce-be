package com.buyology.ecommerce.auth.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(
    name = "email_otp",
    indexes = {
        @Index(name = "idx_email_otp_email", columnList = "email"),
        @Index(name = "idx_email_otp_expires_at", columnList = "expires_at")
    }
)
public class EmailOtp {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // The email being verified
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    // Pre-hashed password stored temporarily until OTP is verified
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Optional: stored for admin signup flows so they are available at verify time
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    // 6-digit numeric OTP code
    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // True once the OTP has been successfully used or explicitly invalidated
    @Column(name = "used", nullable = false)
    private Boolean used = false;

    // Track wrong attempts to prevent brute-force
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (used == null) used = false;
        if (attempts == null) attempts = 0;
    }

    // ── Domain helpers ──────────────────────────────────────────────────────

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isMaxAttemptsReached() {
        return attempts >= 5;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }

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
