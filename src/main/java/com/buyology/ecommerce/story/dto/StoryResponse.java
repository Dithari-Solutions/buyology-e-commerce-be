package com.buyology.ecommerce.story.dto;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.story.domain.StoryMedia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class StoryResponse {

    private UUID id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String status;
    private List<MediaItem> media;
    private LocalDateTime createdAt;

    public StoryResponse() {
    }

    public static StoryResponse from(Story story, Language language) {
        StoryResponse response = new StoryResponse();
        response.id = story.getId();
        response.thumbnailUrl = story.getThumbnailUrl();
        response.status = story.getStatus().name();
        response.createdAt = story.getCreatedAt();

        // Pick the translation matching the requested language
        if (story.getTranslations() != null) {
            story.getTranslations().stream()
                    .filter(t -> t.getLanguage() == language)
                    .findFirst()
                    .ifPresent(t -> {
                        response.title = t.getTitle();
                        response.description = t.getDescription();
                    });
        }

        // Map media
        if (story.getMedia() != null) {
            response.media = story.getMedia().stream()
                    .map(MediaItem::from)
                    .toList();
        }

        return response;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<MediaItem> getMedia() {
        return media;
    }

    public void setMedia(List<MediaItem> media) {
        this.media = media;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ========================
    // Nested media DTO
    // ========================

    public static class MediaItem {
        private String mediaType;
        private String url;
        private String thumbnailUrl;
        private int orderIndex;

        public MediaItem() {
        }

        public static MediaItem from(StoryMedia m) {
            MediaItem item = new MediaItem();
            item.mediaType = m.getMediaType();
            item.url = m.getUrl();
            item.thumbnailUrl = m.getThumbnailUrl();
            item.orderIndex = m.getOrderIndex();
            return item;
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
    }
}
