package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentProviderOrder;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

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
}
