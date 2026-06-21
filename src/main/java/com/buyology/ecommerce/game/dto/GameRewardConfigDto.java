package com.buyology.ecommerce.game.dto;

import com.buyology.ecommerce.game.domain.GameRewardConfig;

/** Admin-facing view/edit shape of the per-game token reward configuration. */
public class GameRewardConfigDto {
    private int quizReward;
    private int miniGameReward;

    public GameRewardConfigDto() {}

    public static GameRewardConfigDto from(GameRewardConfig c) {
        GameRewardConfigDto d = new GameRewardConfigDto();
        d.quizReward = c.getQuizReward();
        d.miniGameReward = c.getMiniGameReward();
        return d;
    }

    public int getQuizReward() { return quizReward; }
    public void setQuizReward(int quizReward) { this.quizReward = quizReward; }

    public int getMiniGameReward() { return miniGameReward; }
    public void setMiniGameReward(int miniGameReward) { this.miniGameReward = miniGameReward; }
}
