package com.buyology.ecommerce.supplier.repository;

import com.buyology.ecommerce.supplier.domain.SupplierOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierOtpRepository extends JpaRepository<SupplierOtp, UUID> {
    Optional<SupplierOtp> findTopByApplicationIdAndUsedFalseOrderByCreatedAtDesc(UUID applicationId);
}
