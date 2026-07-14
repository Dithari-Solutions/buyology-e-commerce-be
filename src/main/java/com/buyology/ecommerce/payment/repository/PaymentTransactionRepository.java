package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentProviderOrder;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.PaymentPurpose;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findAllByAppOrderId(UUID appOrderId);

    Optional<PaymentTransaction> findByMerchantOrderId(String merchantOrderId);

    Optional<PaymentTransaction> findByPaymobOrderId(Long paymobOrderId);

    Optional<PaymentTransaction> findByPaymobTransactionId(Long paymobTransactionId);

    /**
     * Loads a transaction under a pessimistic write lock so concurrent
     * refund/charge attempts serialize on the row instead of racing the
     * read-check-write on refund state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findWithLockById(UUID id);

    Optional<PaymentTransaction> findByIntentionId(String intentionId);

    List<PaymentTransaction> findAllByStatus(PaymentStatus status);

    Optional<PaymentTransaction> findFirstByCartIdAndStatusIn(
            UUID cartId, List<PaymentStatus> statuses);

    Optional<PaymentTransaction> findFirstByAppOrderIdAndStatusIn(
            UUID appOrderId, List<PaymentStatus> statuses);

    /** Latest courier-fee charge for a refund in any of the given statuses (for resume/idempotency). */
    Optional<PaymentTransaction> findFirstByRefundRequestIdAndPurposeAndStatusInOrderByCreatedAtDesc(
            UUID refundRequestId, PaymentPurpose purpose, List<PaymentStatus> statuses);

    /** Latest courier-fee charge for a repair in any of the given statuses (for resume/idempotency). */
    Optional<PaymentTransaction> findFirstByRepairIdAndPurposeAndStatusInOrderByCreatedAtDesc(
            UUID repairId, PaymentPurpose purpose, List<PaymentStatus> statuses);

    /**
     * Delivery-fee revenue, bucketed by time. Sums successfully-charged courier
     * return-pickup fees (purpose = COURIER_RETURN_FEE) per period. Amounts are in
     * the settlement currency (AED), the same as {@code amount} is persisted in.
     * Returns rows of [period (timestamp), revenue].
     */
    @Query(value = """
            SELECT date_trunc(:bucket, pt.created_at) AS period,
                   COALESCE(SUM(pt.amount), 0) AS revenue
            FROM payment_transactions pt
            WHERE pt.purpose = 'COURIER_RETURN_FEE'
              AND pt.status = 'SUCCESS'
              AND pt.created_at >= :from
              AND pt.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> courierFeeRevenueBuckets(@Param("bucket") String bucket,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);
}
