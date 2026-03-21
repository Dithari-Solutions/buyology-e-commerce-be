package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentProviderRepository extends JpaRepository<PaymentProvider, UUID> {

    Optional<PaymentProvider> findByName(String name);

    Optional<PaymentProvider> findByNameAndIsActiveTrue(String name);

    Optional<PaymentProvider> findFirstByIsActiveTrue();
}
