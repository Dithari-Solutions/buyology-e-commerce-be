package com.buyology.ecommerce.story.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import com.buyology.ecommerce.story.domain.StoryStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Request body for creating a new story")
public class CreateStoryRequest {

    @NotEmpty(message = "At least one translation is required")
    @Valid
    @ArraySchema(schema = @Schema(implementation = StoryTranslationRequest.class))
    private List<StoryTranslationRequest> translations;

    @Schema(description = "Status of the story", example = "ACTIVE", enumAsRef = true)
    private StoryStatus status;

    // ========================
    // Getters & Setters
    // ========================

    public List<StoryTranslationRequest> getTranslations() {
        return translations;
    }

    public void setTranslations(List<StoryTranslationRequest> translations) {
        this.translations = translations;
    }

    public StoryStatus getStatus() {
        return status;
    }

    public void setStatus(StoryStatus status) {
        this.status = status;
    }
}
