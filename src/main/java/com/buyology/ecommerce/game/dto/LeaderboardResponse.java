package com.buyology.ecommerce.game.dto;

import java.util.UUID;

public class LeaderboardResponse {

    private UUID userId;
    private String fullName;
    private int score;
    private int streak;

    public LeaderboardResponse() {
    }

    public LeaderboardResponse(UUID userId, String fullName, int score, int streak) {
        this.userId = userId;
        this.fullName = fullName;
        this.score = score;
        this.streak = streak;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }
}
