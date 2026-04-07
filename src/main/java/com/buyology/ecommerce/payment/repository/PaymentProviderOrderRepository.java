package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentProviderOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentProviderOrderRepository extends JpaRepository<PaymentProviderOrder, UUID> {

    Optional<PaymentProviderOrder> findByAppOrderId(UUID appOrderId);

    Optional<PaymentProviderOrder> findByProviderOrderId(String providerOrderId);

    boolean existsByProviderOrderId(String providerOrderId);
}
