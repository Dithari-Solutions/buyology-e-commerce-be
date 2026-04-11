package com.buyology.ecommerce.game.domain;

import com.buyology.ecommerce.game.enums.GameType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_game_configs")
public class DailyGameConfig {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "game_date", nullable = false, unique = true)
    private LocalDate gameDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    public DailyGameConfig() {
    }

    public DailyGameConfig(LocalDate gameDate, GameType gameType) {
        this.gameDate = gameDate;
        this.gameType = gameType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getGameDate() {
        return gameDate;
    }

    public void setGameDate(LocalDate gameDate) {
        this.gameDate = gameDate;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }
}
