package com.buyology.ecommerce.story.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import com.buyology.ecommerce.story.domain.StoryStatus;

import java.time.LocalDateTime;
import java.util.List;

public class CreateStoryRequest {

    @NotEmpty(message = "At least one translation is required")
    @Valid
    private List<StoryTranslationRequest> translations;

    private StoryStatus status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @NotEmpty(message = "Media order list is required")
    private List<Integer> mediaOrders;

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

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }
}
