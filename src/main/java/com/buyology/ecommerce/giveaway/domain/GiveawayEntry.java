package com.buyology.ecommerce.giveaway.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One customer's entry into a giveaway campaign.
 *
 * Two uniqueness rules are enforced at the DB level, not just in the service: one entry per
 * {@code userId} and one per {@code instagramHandle}, both scoped to {@code campaign}. The
 * handle is stored normalised (lower-case, no {@code @}, no profile-URL prefix) so casing or
 * a pasted URL cannot buy a second entry. The raw input is kept alongside it purely so
 * support can see exactly what the customer typed.
 */
@Entity
@Table(name = "giveaway_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_giveaway_entries_user", columnNames = {"campaign", "user_id"}),
        @UniqueConstraint(name = "uq_giveaway_entries_handle", columnNames = {"campaign", "instagram_handle"})
})
public class GiveawayEntry {

    /** The only campaign so far; a column rather than a constant so the next one needs no migration. */
    public static final String DEFAULT_CAMPAIGN = "IPHONE_18_PRO";

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "campaign", nullable = false, length = 60)
    private String campaign = DEFAULT_CAMPAIGN;

    /** users.id (uid). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** auth_credentials.id (sub) — audit only; ownership is keyed on userId. */
    @Column(name = "credential_id")
    private UUID credentialId;

    /** Normalised handle: lower-case, no leading '@', no instagram.com/ prefix. */
    @Column(name = "instagram_handle", nullable = false, length = 30)
    private String instagramHandle;

    /** Exactly what the customer typed, for support. */
    @Column(name = "instagram_handle_raw", length = 200)
    private String instagramHandleRaw;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCampaign() { return campaign; }
    public void setCampaign(String campaign) { this.campaign = campaign; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }
    public String getInstagramHandle() { return instagramHandle; }
    public void setInstagramHandle(String instagramHandle) { this.instagramHandle = instagramHandle; }
    public String getInstagramHandleRaw() { return instagramHandleRaw; }
    public void setInstagramHandleRaw(String instagramHandleRaw) { this.instagramHandleRaw = instagramHandleRaw; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public Instant getCreatedAt() { return createdAt; }
}
