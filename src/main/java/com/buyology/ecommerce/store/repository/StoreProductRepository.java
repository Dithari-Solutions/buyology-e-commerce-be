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
    Optional<StoreProduct> findByProduct_Id(UUID productId);

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
     * Returns [storeId (UUID), storePrice (BigDecimal)] for the store with the lowest price
     * for the given product in the given country. Results are ordered by price ASC so the
     * first element is the cheapest. If multiple stores share the same minimum price the
     * first one by insertion order is returned.
     */
    @Query("""
            SELECT sp.store.id, sp.storePrice, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id = :productId
              AND sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.storePrice ASC
            """)
    List<Object[]> findCheapestStoreByProductAndCountry(
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

    /**
     * Returns [storePrice (BigDecimal), currency (String)] for the globally cheapest store for a product.
     */
    @Query("""
            SELECT sp.storePrice, sp.store.country.currency, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id = :productId
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.storePrice ASC
            LIMIT 1
            """)
    List<Object[]> findCheapestStoreGlobally(@Param("productId") UUID productId);

    /**
     * Batch version of findCheapestStoreGlobally.
     * Returns [productId (UUID), storePrice (BigDecimal), currency (String)] for the globally cheapest store per product.
     */
    @Query("""
            SELECT sp.product.id, sp.storePrice, sp.store.country.currency, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id IN :productIds
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
              AND sp.storePrice = (
                SELECT MIN(sp2.storePrice) FROM StoreProduct sp2
                WHERE sp2.product.id = sp.product.id
                  AND sp2.isActive = true
                  AND sp2.deletedAt IS NULL
                  AND sp2.store.deletedAt IS NULL
              )
            """)
    List<Object[]> findCheapestPricesGloballyBatch(@Param("productIds") List<UUID> productIds);

    /**
     * Batch: returns [productId (UUID), storeId (UUID), storePrice (BigDecimal)] for ALL
     * active stores per product in the given country, ordered by price ASC.
     * Use this when you need to show every store option with its own delivery badge.
     */
    @Query("""
            SELECT sp.product.id, sp.store.id, sp.storePrice, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id IN :productIds
              AND sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.storePrice ASC
            """)
    List<Object[]> findAllStoresPerProductBatch(
            @Param("productIds") List<UUID> productIds,
            @Param("countryCode") String countryCode);

    /**
     * Batch: returns [productId (UUID), storeId (UUID), storePrice (BigDecimal)] for the
     * cheapest store per product in the given country. When multiple stores tie on price
     * the first one encountered is used — callers should take the first row per productId.
     */
    @Query("""
            SELECT sp.product.id, sp.store.id, sp.storePrice FROM StoreProduct sp
            WHERE sp.product.id IN :productIds
              AND sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
              AND sp.storePrice = (
                SELECT MIN(sp2.storePrice) FROM StoreProduct sp2
                WHERE sp2.product.id = sp.product.id
                  AND sp2.store.country.code = :countryCode
                  AND sp2.isActive = true
                  AND sp2.deletedAt IS NULL
                  AND sp2.store.deletedAt IS NULL
              )
            """)
    List<Object[]> findCheapestStorePerProductBatch(
            @Param("productIds") List<UUID> productIds,
            @Param("countryCode") String countryCode);

    /**
     * Returns [MIN(storePrice), MAX(storePrice)] across all active store listings.
     * When countryCode is provided, scope to that country's stores only.
     */
    @Query("""
            SELECT MIN(sp.storePrice), MAX(sp.storePrice) FROM StoreProduct sp
            WHERE sp.isActive = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND (:countryCode IS NULL OR sp.store.country.code = :countryCode)
            """)
    List<Object[]> findPriceRange(@Param("countryCode") String countryCode);
}
