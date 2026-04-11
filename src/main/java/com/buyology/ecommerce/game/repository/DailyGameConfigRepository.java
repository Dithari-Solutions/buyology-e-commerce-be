package com.buyology.ecommerce.game.repository;

import com.buyology.ecommerce.game.domain.DailyGameConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyGameConfigRepository extends JpaRepository<DailyGameConfig, UUID> {
    Optional<DailyGameConfig> findByGameDate(LocalDate gameDate);
}
