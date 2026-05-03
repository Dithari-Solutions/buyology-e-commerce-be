package com.buyology.ecommerce.game.dto;

import com.buyology.ecommerce.game.enums.GameType;

public class DailyGameStatusResponse {

    private GameType gameType;
    private boolean played;
    private int tokens;
    private int streak;

    public DailyGameStatusResponse() {}

    public DailyGameStatusResponse(GameType gameType, boolean played, int tokens, int streak) {
        this.gameType = gameType;
        this.played = played;
        this.tokens = tokens;
        this.streak = streak;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public boolean isPlayed() {
        return played;
    }

    public void setPlayed(boolean played) {
        this.played = played;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }
}
