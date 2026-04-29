package com.buyology.ecommerce.supplier.repository;

import com.buyology.ecommerce.supplier.domain.SupplierApplication;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierApplicationRepository extends JpaRepository<SupplierApplication, UUID> {
    boolean existsByEmailAndStatusNot(String email, ApplicationStatus status);
    Page<SupplierApplication> findByStatus(ApplicationStatus status, Pageable pageable);
    Optional<SupplierApplication> findByIdAndDeletedAtIsNull(UUID id);
}
