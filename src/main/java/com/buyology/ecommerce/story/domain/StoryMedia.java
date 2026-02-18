package com.buyology.ecommerce.story.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_media", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "story_id", "order_index" })
})
public class StoryMedia {

    @Id
    @GeneratedValue
    private UUID id;

    // ✅ Owning side of the relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "media_type", nullable = false, length = 10)
    private String mediaType;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ----------------------
    // Constructors
    // ----------------------

    protected StoryMedia() {
        // JPA only
    }

    public StoryMedia(Story story,
            String mediaType,
            String url,
            String thumbnailUrl,
            int orderIndex) {
        this.story = story;
        this.mediaType = mediaType;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.orderIndex = orderIndex;
    }

    // ----------------------
    // Lifecycle Callbacks
    // ----------------------

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ----------------------
    // Getters & Setters
    // ----------------------

    public UUID getId() {
        return id;
    }

    public Story getStory() {
        return story;
    }

    public void setStory(Story story) {
        this.story = story;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
