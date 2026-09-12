package com.buyology.ecommerce.productimport.repository;

import com.buyology.ecommerce.productimport.domain.ProductImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductImportJobRepository extends JpaRepository<ProductImportJob, UUID> {

    List<ProductImportJob> findTop20ByOrderByCreatedAtDesc();
}
