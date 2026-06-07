package com.buyology.ecommerce.story.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.buyology.ecommerce.story.domain.StoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for creating a new story")
public class CreateStoryRequest {

    @NotNull(message = "Translations are required")
    @Valid
    @Schema(description = "Titles (required) and descriptions (optional) in AZ, EN, and AR")
    private StoryTranslationRequest translation;

    @Schema(description = "Status of the story", example = "ACTIVE", enumAsRef = true)
    private StoryStatus status;

    @Schema(description = "Display order in the feed (lower shown first). Defaults to 0.", example = "0")
    private Integer displayOrder;

    // ========================
    // Getters & Setters
    // ========================

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

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
}
