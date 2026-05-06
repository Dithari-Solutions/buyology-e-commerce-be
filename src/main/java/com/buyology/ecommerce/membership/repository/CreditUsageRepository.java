package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.CreditUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditUsageRepository extends JpaRepository<CreditUsage, UUID> {

    List<CreditUsage> findByUserIdOrderByUsedAtDesc(UUID userId);

    List<CreditUsage> findByUserIdAndStatus(UUID userId, CreditUsage.Status status);

    List<CreditUsage> findByStatusAndDueAtBefore(CreditUsage.Status status, Instant cutoff);

    boolean existsByUserIdAndStatusIn(UUID userId, List<CreditUsage.Status> statuses);

    Optional<CreditUsage> findByPaymobIntentionId(String paymobIntentionId);

    Optional<CreditUsage> findByOrderId(UUID orderId);
}
