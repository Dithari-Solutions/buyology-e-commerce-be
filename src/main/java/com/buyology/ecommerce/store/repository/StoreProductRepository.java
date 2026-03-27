package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.StoreProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreProductRepository extends JpaRepository<StoreProduct, UUID> {

    Optional<StoreProduct> findByStore_IdAndProduct_IdAndIsActiveTrue(UUID storeId, UUID productId);

    List<StoreProduct> findByStore_IdAndDeletedAtIsNull(UUID storeId);

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

    /**
     * Returns distinct active products available in stores belonging to the given country code.
     */
    @Query("""
            SELECT DISTINCT sp.product FROM StoreProduct sp
            WHERE sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            """)
    List<Product> findActiveProductsByCountryCode(@Param("countryCode") String countryCode);

    /**
     * Returns the lowest store price for a product in the given country.
     * The price is in the country's native currency (Country.currency).
     */
    @Query("""
            SELECT MIN(sp.storePrice) FROM StoreProduct sp
            WHERE sp.product.id = :productId
              AND sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            """)
    java.math.BigDecimal findMinPriceByProductAndCountry(
            @Param("productId") UUID productId,
            @Param("countryCode") String countryCode);

    /**
     * Batch: returns the minimum price per product for a given country.
     * Result rows are [productId (UUID), minPrice (BigDecimal)].
     */
    @Query("""
            SELECT sp.product.id, MIN(sp.storePrice) FROM StoreProduct sp
            WHERE sp.product.id IN :productIds
              AND sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            GROUP BY sp.product.id
            """)
    List<Object[]> findMinPricesByProductsAndCountry(
            @Param("productIds") List<UUID> productIds,
            @Param("countryCode") String countryCode);
}
