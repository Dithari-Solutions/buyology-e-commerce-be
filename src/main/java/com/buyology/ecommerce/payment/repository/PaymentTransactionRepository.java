package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentProviderOrder;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<PaymentTransaction> findByIntentionId(String intentionId);

    List<PaymentTransaction> findAllByStatus(PaymentStatus status);

    Optional<PaymentTransaction> findFirstByCartIdAndStatusIn(
            UUID cartId, List<PaymentStatus> statuses);

    Optional<PaymentTransaction> findFirstByAppOrderIdAndStatusIn(
            UUID appOrderId, List<PaymentStatus> statuses);
}
