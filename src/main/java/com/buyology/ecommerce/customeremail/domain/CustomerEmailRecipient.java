package com.buyology.ecommerce.customeremail.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One addressee of a campaign, carrying the outcome of its own send.
 *
 * <p>Written from the send call's actual return value, never assumed. A unique constraint on
 * (campaign, email) means a customer holding several credential rows is mailed once.
 */
@Entity
@Table(name = "customer_email_recipients")
public class CustomerEmailRecipient {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "sent_at")
    private Instant sentAt;

    public CustomerEmailRecipient() {}

    public CustomerEmailRecipient(UUID campaignId, UUID userId, String email) {
        this.campaignId = campaignId;
        this.userId = userId;
        this.email = email;
    }

    public UUID getId() { return id; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
