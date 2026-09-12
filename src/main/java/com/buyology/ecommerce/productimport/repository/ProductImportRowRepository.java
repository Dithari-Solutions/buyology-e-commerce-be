package com.buyology.ecommerce.productimport.repository;

import com.buyology.ecommerce.productimport.domain.ProductImportRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductImportRowRepository extends JpaRepository<ProductImportRow, UUID> {

    List<ProductImportRow> findByJobIdOrderByRowNumberAsc(UUID jobId);

    List<ProductImportRow> findByJobIdAndStatusOrderByRowNumberAsc(UUID jobId, ProductImportRow.Status status);

    long countByJobIdAndStatus(UUID jobId, ProductImportRow.Status status);
}
