package com.buyology.ecommerce.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a customer's FCM device token so the ecommerce backend can send
 * push notifications. One row per user per device type — updated on re-register.
 */
@Entity
@Table(
        name = "user_push_tokens",
        indexes = {
                @Index(name = "idx_push_token_user_id", columnList = "user_id"),
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_push_token_user_device",
                        columnNames = {"user_id", "device_type"})
        }
)
public class UserPushToken {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fcm_token", nullable = false, length = 512)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 10)
    private DeviceType deviceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum DeviceType { IOS, ANDROID }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public DeviceType getDeviceType() { return deviceType; }
    public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
