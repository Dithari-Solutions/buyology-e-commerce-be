package com.buyology.ecommerce.supplier.repository;

import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.domain.Supplier.SupplierStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Optional<Supplier> findByUserId(UUID userId);
    Optional<Supplier> findByApplicationId(UUID applicationId);
    List<Supplier> findByStatusAndDeletedAtBefore(SupplierStatus status, Instant cutoff);
    List<Supplier> findByStatus(SupplierStatus status);
}
