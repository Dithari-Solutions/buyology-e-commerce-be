package com.buyology.ecommerce.product.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.buyology.ecommerce.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    List<Product> findByCategoryId(UUID categoryId);

    List<Product> findByStatus(String status);

    List<Product> findByStatusAndCategoryId(String status, UUID categoryId);

    List<Product> findByStatusNot(String status);

    List<Product> findByStatusNotAndCategoryId(String status, UUID categoryId);

    List<Product> findByStatusAndDeletedAtBefore(String status, Instant cutoff);

    boolean existsBySku(String sku);

    List<Product> findByStatusAndIsSuperDeal(String status, Boolean isSuperDeal);

    List<Product> findByStatusAndIsLimitedStock(String status, Boolean isLimitedStock);

    /** Distinct availability statuses present in active products. */
    @Query("SELECT DISTINCT p.availabilityStatus FROM Product p WHERE p.status = 'ACTIVE' AND p.availabilityStatus IS NOT NULL")
    List<Product.AvailabilityStatus> findDistinctAvailabilityStatuses();

    /** Whether any active product has isRefurbished = :value. */
    boolean existsByStatusAndIsRefurbished(String status, Boolean isRefurbished);

    // ── Supplier product queries ───────────────────────────────────────────────
    Page<Product> findBySupplierId(UUID supplierId, Pageable pageable);
    Page<Product> findBySupplierIdIsNotNull(Pageable pageable);
    Page<Product> findBySupplierIdAndSupplierStatus(UUID supplierId, Product.SupplierStatus supplierStatus, Pageable pageable);
    Page<Product> findBySupplierStatus(Product.SupplierStatus supplierStatus, Pageable pageable);
}
