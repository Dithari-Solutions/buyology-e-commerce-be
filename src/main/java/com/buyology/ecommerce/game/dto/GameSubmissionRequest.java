package com.buyology.ecommerce.game.dto;

import com.buyology.ecommerce.game.enums.GameType;
import jakarta.validation.constraints.NotNull;

public class GameSubmissionRequest {

    @NotNull
    private GameType gameType;

    private boolean isSuccess;

    private int score;

    /** Time taken in milliseconds (mini-games). Null for quizzes. */
    private Integer time;

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
    }
}
