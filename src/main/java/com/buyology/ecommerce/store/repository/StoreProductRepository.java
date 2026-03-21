package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.StoreProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoreProductRepository extends JpaRepository<StoreProduct, UUID> {

    /**
     * Returns distinct active products that belong to any of the given stores.
     * Only products with status ACTIVE and store-product rows that are active
     * and not soft-deleted are included.
     */
    @Query("""
            SELECT DISTINCT sp.product FROM StoreProduct sp
            WHERE sp.store.id IN :storeIds
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
            """)
    List<Product> findActiveProductsByStoreIds(@Param("storeIds") List<UUID> storeIds);
}
