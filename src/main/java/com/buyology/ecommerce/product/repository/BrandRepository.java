package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    List<Brand> findByStatus(String status);

    /** Brands that have at least one active (non-deleted) product assigned to them. */
    @Query("""
            SELECT DISTINCT p.brand FROM Product p
            WHERE p.status = 'ACTIVE' AND p.brand IS NOT NULL
            """)
    List<Brand> findBrandsWithActiveProducts();
}
