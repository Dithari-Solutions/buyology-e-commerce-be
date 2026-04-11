package com.buyology.ecommerce.game.dto;

import java.util.List;
import java.util.UUID;

public class QuizQuestionResponse {

    private UUID id;
    private int correctOptionIndex;
    private int points;
    private boolean isActive;
    private List<QuizTranslationResponse> translations;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public List<QuizTranslationResponse> getTranslations() {
        return translations;
    }

    public void setTranslations(List<QuizTranslationResponse> translations) {
        this.translations = translations;
    }
}
