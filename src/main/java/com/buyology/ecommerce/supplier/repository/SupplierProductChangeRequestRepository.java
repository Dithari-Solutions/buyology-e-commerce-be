package com.buyology.ecommerce.supplier.repository;

import com.buyology.ecommerce.supplier.domain.SupplierProductChangeRequest;
import com.buyology.ecommerce.supplier.domain.SupplierProductChangeRequest.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierProductChangeRequestRepository
        extends JpaRepository<SupplierProductChangeRequest, UUID> {

    Page<SupplierProductChangeRequest> findAllBySupplierId(UUID supplierId, Pageable pageable);

    Page<SupplierProductChangeRequest> findAllByStatus(Status status, Pageable pageable);

    boolean existsByProductIdAndStatus(UUID productId, Status status);

    Optional<SupplierProductChangeRequest> findByIdAndSupplierId(UUID id, UUID supplierId);
}
