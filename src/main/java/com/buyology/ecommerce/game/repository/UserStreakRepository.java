package com.buyology.ecommerce.game.repository;

import com.buyology.ecommerce.game.domain.UserStreak;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStreakRepository extends JpaRepository<UserStreak, UUID> {
    Optional<UserStreak> findByUser(Users user);
    List<UserStreak> findTop10ByOrderByCurrentStreakDesc();
}
