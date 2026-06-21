package com.buyology.ecommerce.game.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Single-row configuration for how many tokens a successful game/quiz awards.
 * Admin-editable from the dashboard so the rewards can be tuned without a deploy
 * (replaces the old hardcoded TOKENS_PER_WIN constant in GameService).
 */
@Entity
@Table(name = "game_reward_config")
public class GameRewardConfig {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Tokens awarded for a successful quiz. */
    @Column(name = "quiz_reward", nullable = false)
    private int quizReward = 10;

    /** Tokens awarded for a successful mini-game. */
    @Column(name = "mini_game_reward", nullable = false)
    private int miniGameReward = 10;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public int getQuizReward() { return quizReward; }
    public void setQuizReward(int quizReward) { this.quizReward = quizReward; }

    public int getMiniGameReward() { return miniGameReward; }
    public void setMiniGameReward(int miniGameReward) { this.miniGameReward = miniGameReward; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
