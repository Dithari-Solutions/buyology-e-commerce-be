package com.buyology.ecommerce.supplier.repository;

import com.buyology.ecommerce.supplier.domain.SupplierStoreAssignment;
import com.buyology.ecommerce.supplier.domain.SupplierStoreAssignment.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SupplierStoreAssignmentRepository extends JpaRepository<SupplierStoreAssignment, Id> {
    List<SupplierStoreAssignment> findBySupplierId(UUID supplierId);
    boolean existsBySupplierIdAndStoreId(UUID supplierId, UUID storeId);

    @Query("SELECT a.id.storeId FROM SupplierStoreAssignment a WHERE a.id.supplierId = :supplierId")
    List<UUID> findStoreIdsBySupplierId(@Param("supplierId") UUID supplierId);
}
