package com.buyology.ecommerce.notification.repository;

import com.buyology.ecommerce.notification.domain.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {
    List<NotificationHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndIsReadFalse(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(
            "UPDATE NotificationHistory n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllRead(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
