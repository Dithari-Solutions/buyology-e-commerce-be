package com.buyology.ecommerce.game.dto;

public class GameSubmissionResponse {

    private int tokensEarned;
    private int newStreak;
    private int newTotalTokens;

    public GameSubmissionResponse() {}

    public GameSubmissionResponse(int tokensEarned, int newStreak, int newTotalTokens) {
        this.tokensEarned = tokensEarned;
        this.newStreak = newStreak;
        this.newTotalTokens = newTotalTokens;
    }

    public int getTokensEarned() {
        return tokensEarned;
    }

    public void setTokensEarned(int tokensEarned) {
        this.tokensEarned = tokensEarned;
    }

    public int getNewStreak() {
        return newStreak;
    }

    public void setNewStreak(int newStreak) {
        this.newStreak = newStreak;
    }

    public int getNewTotalTokens() {
        return newTotalTokens;
    }

    public void setNewTotalTokens(int newTotalTokens) {
        this.newTotalTokens = newTotalTokens;
    }
}
