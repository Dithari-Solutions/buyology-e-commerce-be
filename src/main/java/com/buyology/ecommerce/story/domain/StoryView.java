package com.buyology.ecommerce.story.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_views", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "story_id", "user_id" })
})
public class StoryView {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    // ----------------------
    // Constructors
    // ----------------------
    public StoryView() {
    }

    public StoryView(UUID storyId, UUID userId) {
        this.storyId = storyId;
        this.userId = userId;
    }

    // ----------------------
    // Lifecycle Callbacks
    // ----------------------
    @PrePersist
    public void prePersist() {
        if (viewedAt == null) {
            viewedAt = Instant.now();
        }
    }

    // ----------------------
    // Getters & Setters
    // ----------------------
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStoryId() {
        return storyId;
    }

    public void setStoryId(UUID storyId) {
        this.storyId = storyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(Instant viewedAt) {
        this.viewedAt = viewedAt;
    }
}
