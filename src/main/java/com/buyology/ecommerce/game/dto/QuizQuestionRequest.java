package com.buyology.ecommerce.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class QuizQuestionRequest {

    @Min(0)
    @Max(3)
    private int correctOptionIndex;

    @Min(0)
    private int points;

    private boolean isActive = true;

    @NotEmpty
    private List<QuizTranslationRequest> translations;

    public int getCorrectOptionIndex() {
        return correctOptionIndex;
    }

    public void setCorrectOptionIndex(int correctOptionIndex) {
        this.correctOptionIndex = correctOptionIndex;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public List<QuizTranslationRequest> getTranslations() {
        return translations;
    }

    public void setTranslations(List<QuizTranslationRequest> translations) {
        this.translations = translations;
    }
}
