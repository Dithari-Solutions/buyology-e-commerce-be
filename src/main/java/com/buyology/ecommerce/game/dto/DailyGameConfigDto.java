package com.buyology.ecommerce.game.dto;

import com.buyology.ecommerce.game.enums.GameType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class DailyGameConfigDto {
    
    @NotNull
    private LocalDate gameDate;
    
    @NotNull
    private GameType gameType;

    public DailyGameConfigDto() {
    }

    public DailyGameConfigDto(LocalDate gameDate, GameType gameType) {
        this.gameDate = gameDate;
        this.gameType = gameType;
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
