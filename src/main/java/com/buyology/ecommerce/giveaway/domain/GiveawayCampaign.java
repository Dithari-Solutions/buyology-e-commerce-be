package com.buyology.ecommerce.giveaway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Whether a giveaway is accepting entries.
 *
 * <p>Closing is not deleting. Entries stay exactly where they are — they are the draw — and the
 * campaign simply stops taking new ones, which is why this is a flag on its own row rather than
 * anything that touches {@link GiveawayEntry}.
 */
@Entity
@Table(name = "giveaway_campaigns")
public class GiveawayCampaign {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "campaign", nullable = false, unique = true, length = 60)
    private String campaign;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    /** When it was last closed — shown in the dashboard so "closed" has a date against it. */
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

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
    public String getCampaign() { return campaign; }
    public void setCampaign(String campaign) { this.campaign = campaign; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
