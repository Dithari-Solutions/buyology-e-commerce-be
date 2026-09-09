package com.buyology.ecommerce.customeremail.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One admin-composed email to customers, and the record of what it actually did.
 *
 * <p>The counts are kept separately from the recipient count on purpose. The broadcasts that
 * existed before this one report the number of rows they looped over as the number of emails
 * sent, so a run where every message bounced looks identical to one where every message arrived.
 * Here {@code recipientCount} is who we intended to reach and {@code sentCount} is who the
 * provider accepted, and the difference is visible.
 */
@Entity
@Table(name = "customer_email_campaigns")
public class CustomerEmailCampaign {

    public enum Audience { SELECTED, ALL_CUSTOMERS }

    /** DRAFT is the only state a send may start from, which is what makes the claim exclusive. */
    public enum Status { DRAFT, SENDING, SENT, FAILED, ABORTED }

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 30)
    private Audience audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "abort_reason", length = 500)
    private String abortReason;

    /** Captured on the request thread — the async worker has no SecurityContext to read. */
    @Column(name = "created_by_admin_id", nullable = false)
    private UUID createdByAdminId;

    /** Snapshotted rather than joined, because admins leave and the record should outlive them. */
    @Column(name = "created_by_admin_name", length = 200)
    private String createdByAdminName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public UUID getId() { return id; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBodyHtml() { return bodyHtml; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public Audience getAudience() { return audience; }
    public void setAudience(Audience audience) { this.audience = audience; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getRecipientCount() { return recipientCount; }
    public void setRecipientCount(int recipientCount) { this.recipientCount = recipientCount; }
    public int getSentCount() { return sentCount; }
    public void setSentCount(int sentCount) { this.sentCount = sentCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public String getAbortReason() { return abortReason; }
    public void setAbortReason(String abortReason) { this.abortReason = abortReason; }
    public UUID getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(UUID v) { this.createdByAdminId = v; }
    public String getCreatedByAdminName() { return createdByAdminName; }
    public void setCreatedByAdminName(String v) { this.createdByAdminName = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
