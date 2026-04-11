package com.buyology.ecommerce.game.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "correct_option_index", nullable = false)
    private int correctOptionIndex;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @OneToMany(mappedBy = "quizQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizQuestionTranslation> translations = new ArrayList<>();

    public QuizQuestion() {
    }

    public QuizQuestion(int correctOptionIndex, int points, boolean isActive) {
        this.correctOptionIndex = correctOptionIndex;
        this.points = points;
        this.isActive = isActive;
    }

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

    public List<QuizQuestionTranslation> getTranslations() {
        return translations;
    }

    public void setTranslations(List<QuizQuestionTranslation> translations) {
        this.translations = translations;
    }
}
