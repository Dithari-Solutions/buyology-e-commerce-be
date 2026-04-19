package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    /** Categories that have at least one active (non-deleted) product. */
    @Query("""
            SELECT DISTINCT p.category FROM Product p
            WHERE p.status = 'ACTIVE'
            """)
    List<ProductCategory> findCategoriesWithActiveProducts();
}
