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

    /**
     * Settled order payments that no order claims and that nobody has reviewed yet.
     *
     * <p>Driven from the TRANSACTION side on purpose: the cases this must find include a payment
     * whose order was never created at all, which no scan over orders can see. A transaction whose
     * order points back at it is healthy and excluded in SQL, so the healthy back catalogue can
     * never fill the page and starve the sweep.
     */
    @Query(value = """
            SELECT pt.* FROM payment_transactions pt
            LEFT JOIN orders o ON o.id = pt.app_order_id
            WHERE pt.purpose = 'ORDER'
              AND pt.status = 'SUCCESS'
              AND pt.created_at < :cutoff
              AND (o.id IS NULL OR o.payment_transaction_id IS DISTINCT FROM pt.id)
              AND NOT EXISTS (SELECT 1 FROM payment_anomalies a
                              WHERE a.payment_transaction_id = pt.id)
            ORDER BY pt.created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PaymentTransaction> findUnreviewedSettledOrderPayments(@Param("cutoff") Instant cutoff,
                                                                @Param("limit") int limit);

    /**
     * Order payments the gateway acknowledged but never finished telling us about.
     *
     * <p>Instalment providers (Tabby, Tamara) settle in two steps: Paymob reports the transaction
     * as {@code pending} the moment the shopper is approved, then sends a second {@code success}
     * webhook once the provider confirms. If that second webhook is lost — dropped in transit,
     * rejected while we were deploying, or never sent — the customer has paid and the order sits
     * in PENDING_PAYMENT with nobody watching. Nothing else in the system asks the gateway about
     * a payment in this state, so this query is what makes the recovery sweep possible.
     *
     * <p>The floor keeps the sweep off ancient abandoned checkouts: a shopper who closed the tab
     * two months ago is not a lost payment, and re-querying every one of them forever would be a
     * standing load on Paymob for no benefit.
     */
    @Query(value = """
            SELECT pt.* FROM payment_transactions pt
            JOIN orders o ON o.id = pt.app_order_id
            WHERE pt.purpose = 'ORDER'
              AND pt.status IN ('PENDING', 'PROCESSING')
              AND o.status = 'PENDING_PAYMENT'
              AND o.deleted_at IS NULL
              AND pt.created_at < :cutoff
              AND pt.created_at > :floor
              AND NOT EXISTS (SELECT 1 FROM payment_transactions s
                              WHERE s.app_order_id = pt.app_order_id
                                AND s.status = 'SUCCESS')
            ORDER BY pt.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PaymentTransaction> findUnsettledOrderPayments(@Param("cutoff") Instant cutoff,
                                                        @Param("floor") Instant floor,
                                                        @Param("limit") int limit);

    /** Latest courier-fee charge for a refund in any of the given statuses (for resume/idempotency). */
    Optional<PaymentTransaction> findFirstByRefundRequestIdAndPurposeAndStatusInOrderByCreatedAtDesc(
            UUID refundRequestId, PaymentPurpose purpose, List<PaymentStatus> statuses);

    /** Latest courier-fee charge for a repair in any of the given statuses (for resume/idempotency). */
    Optional<PaymentTransaction> findFirstByRepairIdAndPurposeAndStatusInOrderByCreatedAtDesc(
            UUID repairId, PaymentPurpose purpose, List<PaymentStatus> statuses);

    /** Latest courier-fee charge for a sell request in any of the given statuses (resume/idempotency). */
    Optional<PaymentTransaction> findFirstBySellRequestIdAndPurposeAndStatusInOrderByCreatedAtDesc(
            UUID sellRequestId, PaymentPurpose purpose, List<PaymentStatus> statuses);

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
