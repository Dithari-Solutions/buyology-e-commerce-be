package com.buyology.ecommerce.game.repository;

import com.buyology.ecommerce.game.domain.UserStreak;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStreakRepository extends JpaRepository<UserStreak, UUID> {
    Optional<UserStreak> findByUser(Users user);
    List<UserStreak> findTop10ByOrderByCurrentStreakDesc();

    @Query("SELECT s FROM UserStreak s WHERE s.currentStreak > 0 AND s.lastPlayedDate < :today")
    List<UserStreak> findActiveStreaksNotPlayedToday(@Param("today") LocalDate today);
}
