package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    List<PaymentWebhookEvent> findAllByTransaction(PaymentTransaction transaction);

    // Used for idempotency check before processing a webhook
    Optional<PaymentWebhookEvent> findFirstByProviderTxnIdAndProcessedTrue(String providerTxnId);

    List<PaymentWebhookEvent> findAllByProcessedFalseAndHmacValidTrue();
}
