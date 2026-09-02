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

    /**
     * Streaks that are STILL ALIVE and at risk today — last played yesterday, not yet today.
     *
     * <p>The previous version asked for {@code lastPlayedDate < today}, which also matched people
     * who last played weeks ago. Because a broken streak was never reset, those users were emailed
     * "keep your 12-day streak alive" about a streak that had ended long before.
     */
    @Query("SELECT s FROM UserStreak s WHERE s.currentStreak > 0 AND s.lastPlayedDate = :yesterday")
    List<UserStreak> findStreaksAtRiskToday(@Param("yesterday") LocalDate yesterday);

    /**
     * Streaks that have already been broken but still hold their old number.
     *
     * <p>Nothing reset these: {@code updateStreak} only recalculates when the customer next plays,
     * so until then the account, the reminder and the email all read a number that is no longer
     * true — and the moment they do play it drops to 1 with no explanation.
     */
    @Query("SELECT s FROM UserStreak s WHERE s.currentStreak > 0 AND s.lastPlayedDate < :yesterday")
    List<UserStreak> findBrokenStreaks(@Param("yesterday") LocalDate yesterday);
}
