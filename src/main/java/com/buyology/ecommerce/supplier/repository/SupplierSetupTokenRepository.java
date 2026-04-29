package com.buyology.ecommerce.supplier.repository;

import com.buyology.ecommerce.supplier.domain.SupplierSetupToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierSetupTokenRepository extends JpaRepository<SupplierSetupToken, UUID> {
    Optional<SupplierSetupToken> findByTokenAndUsedFalse(String token);
}
