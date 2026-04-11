package com.buyology.ecommerce.game.repository;

import com.buyology.ecommerce.game.domain.GameResult;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {
    
    @Query("SELECT gr FROM GameResult gr WHERE gr.user = :user AND gr.playedAt >= :startOfDay AND gr.playedAt <= :endOfDay")
    Optional<GameResult> findByUserAndPlayedDate(@Param("user") Users user, 
                                                @Param("startOfDay") LocalDateTime startOfDay, 
                                                @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT gr FROM GameResult gr WHERE gr.playedAt >= :startOfDay AND gr.playedAt <= :endOfDay ORDER BY gr.score DESC")
    List<GameResult> findDailyLeaderboard(@Param("startOfDay") LocalDateTime startOfDay, 
                                          @Param("endOfDay") LocalDateTime endOfDay);
}
