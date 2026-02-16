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
    private String status;
    private List<MediaItem> media;
    private LocalDateTime createdAt;

    public static StoryResponse from(Story story, Language language) {
        StoryResponse response = new StoryResponse();
        response.id = story.getId();
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
    // Getters
    // ========================

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public List<MediaItem> getMedia() {
        return media;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ========================
    // Nested media DTO
    // ========================

    public static class MediaItem {
        private String mediaType;
        private String url;
        private String thumbnailUrl;
        private int orderIndex;

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

        public String getUrl() {
            return url;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        public int getOrderIndex() {
            return orderIndex;
        }
    }
}
