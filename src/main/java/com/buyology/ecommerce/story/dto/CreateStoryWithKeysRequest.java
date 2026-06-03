package com.buyology.ecommerce.story.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.buyology.ecommerce.story.domain.StoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for creating a story whose media was already uploaded directly
 * to storage (via presigned URLs). Only the resulting storage keys are sent
 * here — no file bytes pass through the application.
 */
@Schema(description = "Create a story from already-uploaded media keys")
public class CreateStoryWithKeysRequest {

    @NotNull(message = "Translations are required")
    @Valid
    private StoryTranslationRequest translation;

    @Schema(description = "Status of the story", example = "ACTIVE", enumAsRef = true)
    private StoryStatus status;

    @NotBlank(message = "thumbnailKey is required")
    @Schema(description = "Storage key of the already-uploaded thumbnail image")
    private String thumbnailKey;

    @Valid
    @Schema(description = "Already-uploaded media items, in display order")
    private List<MediaItem> media = new ArrayList<>();

    public StoryTranslationRequest getTranslation() {
        return translation;
    }

    public void setTranslation(StoryTranslationRequest translation) {
        this.translation = translation;
    }

    public StoryStatus getStatus() {
        return status;
    }

    public void setStatus(StoryStatus status) {
        this.status = status;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public void setThumbnailKey(String thumbnailKey) {
        this.thumbnailKey = thumbnailKey;
    }

    public List<MediaItem> getMedia() {
        return media;
    }

    public void setMedia(List<MediaItem> media) {
        this.media = media;
    }

    @Schema(description = "A single already-uploaded media item")
    public static class MediaItem {

        @NotBlank(message = "media key is required")
        @Schema(description = "Storage key of the already-uploaded media file")
        private String key;

        @Schema(description = "MIME type of the file, used to classify image vs video", example = "video/mp4")
        private String contentType;

        @Schema(description = "Display order within the story")
        private int orderIndex;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public int getOrderIndex() {
            return orderIndex;
        }

        public void setOrderIndex(int orderIndex) {
            this.orderIndex = orderIndex;
        }
    }
}
