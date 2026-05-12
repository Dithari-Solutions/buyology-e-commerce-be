package com.buyology.ecommerce.payout.repository;

import com.buyology.ecommerce.payout.domain.SupplierPayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierPayoutAccountRepository extends JpaRepository<SupplierPayoutAccount, UUID> {
    Optional<SupplierPayoutAccount> findBySupplierId(UUID supplierId);
}
