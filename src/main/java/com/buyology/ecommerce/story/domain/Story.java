package com.buyology.ecommerce.story.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stories")
public class Story {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StoryStatus status = StoryStatus.ACTIVE;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoryTranslation> translations;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "story_id")
    private List<StoryMedia> media;

    // ========================
    // Lifecycle Hooks
    // ========================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = StoryStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========================
    // Getters & Setters
    // ========================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StoryStatus getStatus() {
        return status;
    }

    public void setStatus(StoryStatus status) {
        this.status = status;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
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

    public void setTranslations(List<StoryTranslation> translations) {
        this.translations = translations;
    }

    public List<StoryMedia> getMedia() {
        return media;
    }

    public void setMedia(List<StoryMedia> media) {
        this.media = media;
    }
}
