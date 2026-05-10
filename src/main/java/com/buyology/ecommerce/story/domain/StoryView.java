package com.buyology.ecommerce.story.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_views", uniqueConstraints = {
        @UniqueConstraint(name = "uk_story_views_story_user", columnNames = { "story_id", "user_id" }),
        @UniqueConstraint(name = "uk_story_views_story_hash", columnNames = { "story_id", "viewer_hash" })
})
public class StoryView {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "viewer_hash", length = 64)
    private String viewerHash;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    public StoryView() {
    }

    public StoryView(UUID storyId, UUID userId, String viewerHash) {
        this.storyId = storyId;
        this.userId = userId;
        this.viewerHash = viewerHash;
    }

    @PrePersist
    public void prePersist() {
        if (viewedAt == null) {
            viewedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getViewerHash() { return viewerHash; }
    public void setViewerHash(String viewerHash) { this.viewerHash = viewerHash; }

    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }
}
