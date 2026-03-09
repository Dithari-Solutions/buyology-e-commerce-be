package com.buyology.ecommerce.story.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stories")
public class Story {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private StoryStatus status = StoryStatus.ACTIVE;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========================
    // Relationships
    // ========================

    @OneToMany(
            mappedBy = "story",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<StoryTranslation> translations = new ArrayList<>();

    @OneToMany(
            mappedBy = "story",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<StoryMedia> media = new ArrayList<>();

    // ========================
    // Constructors
    // ========================

    protected Story() {
        // JPA only
    }

    public Story(UUID createdBy) {
        this.createdBy = createdBy;
        this.status = StoryStatus.ACTIVE;
    }

    // ========================
    // Lifecycle Hooks
    // ========================

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = StoryStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========================
    // Relationship Helpers
    // ========================

    public void addTranslation(StoryTranslation translation) {
        translation.setStory(this);
        this.translations.add(translation);
    }

    public void removeTranslation(StoryTranslation translation) {
        translation.setStory(null);
        this.translations.remove(translation);
    }

    public void addMedia(StoryMedia media) {
        media.setStory(this);
        this.media.add(media);
    }

    public void removeMedia(StoryMedia media) {
        media.setStory(null);
        this.media.remove(media);
    }

    // ========================
    // Getters
    // ========================

    public UUID getId() {
        return id;
    }

    public StoryStatus getStatus() {
        return status;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<StoryTranslation> getTranslations() {
        return translations;
    }

    public List<StoryMedia> getMedia() {
        return media;
    }

    // ========================
    // Business Methods
    // ========================

    public void activate() {
        this.status = StoryStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = StoryStatus.INACTIVE;
    }
}
