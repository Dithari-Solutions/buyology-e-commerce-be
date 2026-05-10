package com.buyology.ecommerce.story.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_story_likes_story_user", columnNames = { "story_id", "user_id" })
})
public class StoryLike {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "liked_at", nullable = false)
    private Instant likedAt;

    public StoryLike() {
    }

    public StoryLike(UUID storyId, UUID userId) {
        this.storyId = storyId;
        this.userId = userId;
    }

    @PrePersist
    public void prePersist() {
        if (likedAt == null) {
            likedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Instant getLikedAt() { return likedAt; }
    public void setLikedAt(Instant likedAt) { this.likedAt = likedAt; }
}
