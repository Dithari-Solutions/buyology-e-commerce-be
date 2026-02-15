package com.buyology.ecommerce.story.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class StoryMediaRequest {

    @NotBlank(message = "Media type is required")
    @Pattern(regexp = "^(IMAGE|VIDEO)$", message = "Media type must be either IMAGE or VIDEO")
    private String mediaType;

    @NotBlank(message = "Media URL is required")
    private String url;

    private String thumbnailUrl;

    @Min(value = 0, message = "Order index must be non-negative")
    private int orderIndex;

    // ========================
    // Getters & Setters
    // ========================

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
