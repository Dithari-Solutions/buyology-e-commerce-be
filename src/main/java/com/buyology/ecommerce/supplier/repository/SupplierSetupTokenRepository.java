package com.buyology.ecommerce.supplier.repository;

import com.buyology.ecommerce.supplier.domain.SupplierSetupToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierSetupTokenRepository extends JpaRepository<SupplierSetupToken, UUID> {
    Optional<SupplierSetupToken> findByTokenAndUsedFalse(String token);
    List<SupplierSetupToken> findBySupplierIdAndUsedFalse(UUID supplierId);
}
