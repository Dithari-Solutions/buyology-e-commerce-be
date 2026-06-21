package com.buyology.ecommerce.game.repository;

import com.buyology.ecommerce.game.domain.GameRewardConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameRewardConfigRepository extends JpaRepository<GameRewardConfig, UUID> {
    Optional<GameRewardConfig> findTopByOrderByUpdatedAtAsc();
}
