package com.buyology.ecommerce.support.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer support ticket: a software bug report, an "I'm stuck" request, or any other help
 * ask. Any logged-in customer can open one. Ownership is keyed on {@code credentialId}
 * (auth_credentials.id / sub) with users.id (uid) denormalized alongside, exactly like
 * {@link com.buyology.ecommerce.repair.domain.RepairRequest}. Contact email is snapshotted from
 * the profile at submit time so later notifications don't need a live lookup.
 *
 * Up to four screenshots are stored as newline-delimited Contabo S3 keys in {@code imageKeys};
 * presigned URLs are generated on read. Column names mirror V42 (support_tickets) exactly so
 * Hibernate ddl-auto=update stays a no-op.
 */
@Entity
@Table(name = "support_tickets", indexes = {
        @Index(name = "idx_support_tickets_credential", columnList = "credential_id"),
        @Index(name = "idx_support_tickets_status", columnList = "status"),
        @Index(name = "idx_support_tickets_admin_unread", columnList = "admin_unread"),
        @Index(name = "idx_support_tickets_created_at", columnList = "created_at DESC")
})
public class SupportTicket {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Human-friendly display reference, e.g. ST-2026-001. */
    @Column(name = "reference", length = 30)
    private String reference;

    /** Owner — auth_credentials.id (sub). */
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    /** users.id (uid) — resolved from the credential at creation time. */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SupportCategory category;

    @Column(name = "subject", nullable = false, length = 150)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Where the customer got stuck — the page URL, optional. */
    @Column(name = "page_url", length = 500)
    private String pageUrl;

    /** Up to four Contabo S3 object keys, newline-delimited (presigned URLs generated on read). */
    @Column(name = "image_keys", columnDefinition = "text")
    private String imageKeys;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    /** Last team note / resolution text (also mirrored into the message thread when set). */
    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    /** Admin users.id (uid) of the last update. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Snapshotted from the customer's profile at submit time. */
    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    /** Drives the dashboard badge; every customer-side action re-raises it. */
    @Column(name = "admin_unread", nullable = false)
    private boolean adminUnread = true;

    /** Raised by every team-side action; cleared when the customer opens the ticket. */
    @Column(name = "customer_unread", nullable = false)
    private boolean customerUnread = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public SupportCategory getCategory() { return category; }
    public void setCategory(SupportCategory category) { this.category = category; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }
    public String getImageKeys() { return imageKeys; }
    public void setImageKeys(String imageKeys) { this.imageKeys = imageKeys; }
    public SupportTicketStatus getStatus() { return status; }
    public void setStatus(SupportTicketStatus status) { this.status = status; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public boolean isAdminUnread() { return adminUnread; }
    public void setAdminUnread(boolean adminUnread) { this.adminUnread = adminUnread; }
    public boolean isCustomerUnread() { return customerUnread; }
    public void setCustomerUnread(boolean customerUnread) { this.customerUnread = customerUnread; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
