package com.buyology.ecommerce.game.domain;

import com.buyology.ecommerce.game.enums.GameType;
import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "game_results",
        // Enforces the daily-play limit at the DB level so concurrent submits
        // cannot both pass the check-then-act guard and double-award tokens.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_results_user_play_date",
                columnNames = {"user_id", "play_date"})
)
public class GameResult {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "is_success", nullable = false)
    private boolean isSuccess;

    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;

    /** Calendar day of the play, used by the daily-limit unique constraint. */
    @Column(name = "play_date", nullable = false)
    private LocalDate playDate;

    public GameResult() {
    }

    public GameResult(Users user, GameType gameType, int score, boolean isSuccess, LocalDateTime playedAt) {
        this.user = user;
        this.gameType = gameType;
        this.score = score;
        this.isSuccess = isSuccess;
        this.playedAt = playedAt;
        this.playDate = playedAt != null ? playedAt.toLocalDate() : null;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
        this.playDate = playedAt != null ? playedAt.toLocalDate() : null;
    }

    public LocalDate getPlayDate() {
        return playDate;
    }

    public void setPlayDate(LocalDate playDate) {
        this.playDate = playDate;
    }
}
