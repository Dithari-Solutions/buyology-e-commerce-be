package com.buyology.ecommerce.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_otps", indexes = {
        @Index(name = "idx_supplier_otp_app_id", columnList = "application_id"),
        @Index(name = "idx_supplier_otp_target", columnList = "target"),
        @Index(name = "idx_supplier_otp_expires_at", columnList = "expires_at")
})
public class SupplierOtp {

    public enum OtpChannel {
        EMAIL, PHONE
    }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private OtpChannel channel;

    @Column(name = "target", nullable = false, length = 255)
    private String target;

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
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isMaxAttemptsReached() {
        return attempts >= 5;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public OtpChannel getChannel() { return channel; }
    public void setChannel(OtpChannel channel) { this.channel = channel; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
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
