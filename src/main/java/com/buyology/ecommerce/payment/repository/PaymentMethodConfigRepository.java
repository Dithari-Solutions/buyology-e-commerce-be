package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentMethodConfig;
import com.buyology.ecommerce.payment.domain.PaymentProvider;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodConfigRepository extends JpaRepository<PaymentMethodConfig, UUID> {

    Optional<PaymentMethodConfig> findByProviderAndMethodTypeAndIsActiveTrue(
            PaymentProvider provider, PaymentMethodType methodType);

    Optional<PaymentMethodConfig> findByProviderAndMethodType(
            PaymentProvider provider, PaymentMethodType methodType);
}
