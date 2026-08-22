package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentAnomaly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentAnomalyRepository extends JpaRepository<PaymentAnomaly, UUID> {

    /** The cheap pre-check; the unique index is the authority when two replicas race it. */
    boolean existsByPaymentTransactionId(UUID paymentTransactionId);

    /** Whether this order sits in the payment-review queue — used to keep automation's hands off it. */
    boolean existsByAppOrderIdAndResolutionNot(UUID appOrderId, String resolution);

    List<PaymentAnomaly> findByResolutionOrderByCreatedAtAsc(String resolution, Pageable pageable);

    List<PaymentAnomaly> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Wins the right to auto-refund this anomaly. Condition and write are one statement, so two
     * replicas cannot both pass it — the same claim shape the Quiqup dispatch and cancel use.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentAnomaly a SET a.resolution = 'AUTO_REFUNDING', a.attempts = a.attempts + 1 "
            + "WHERE a.id = :id AND a.resolution = 'OPEN' AND a.attempts < :maxAttempts")
    int claimForRefund(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts);
}
