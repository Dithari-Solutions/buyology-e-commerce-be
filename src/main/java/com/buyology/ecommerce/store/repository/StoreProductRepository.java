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
     * B2C channel: distinct consumer-visible products available in stores of the given country.
     * Adds {@code sp.b2cEnabled = true} on top of {@link #findActiveProductsByCountryCode} so
     * B2B-only assignments (b2c off) never leak into the consumer catalog.
     */
    @Query("""
            SELECT DISTINCT sp.product FROM StoreProduct sp
            WHERE sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            """)
    List<Product> findB2cActiveProductsByCountryCode(@Param("countryCode") String countryCode);

    /**
     * B2C channel (no country): ids of every ACTIVE product that has at least one active,
     * consumer-visible (b2cEnabled) store assignment. Used to gate the global consumer
     * catalog so B2B-only products are excluded even when no country is selected.
     */
    @Query("""
            SELECT DISTINCT sp.product.id FROM StoreProduct sp
            WHERE sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            """)
    List<UUID> findB2cActiveProductIds();

    /**
     * B2B channel: distinct products offered for B2B (sp.b2bEnabled) in stores of the given
     * country, only when that country is B2B-enabled (country.b2bEnabled). Country-scoped B2B browse.
     */
    @Query("""
            SELECT DISTINCT sp.product FROM StoreProduct sp
            WHERE sp.store.country.code = :countryCode
              AND sp.store.country.b2bEnabled = true
              AND sp.isActive = true
              AND sp.b2bEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            """)
    List<Product> findB2bActiveProductsByCountryCode(@Param("countryCode") String countryCode);

    /**
     * B2B channel (no country): distinct products offered for B2B in ANY store whose country
     * is B2B-enabled. Used for the global B2B browse when no country is selected.
     */
    @Query("""
            SELECT DISTINCT sp.product FROM StoreProduct sp
            WHERE sp.store.country.b2bEnabled = true
              AND sp.isActive = true
              AND sp.b2bEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            """)
    List<Product> findB2bActiveProductsInB2bCountries();

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
     * B2C variant of {@link #findCheapestStoreByProductAndCountry}: only consumer-visible
     * (b2cEnabled) store rows are considered, so the consumer price is never derived from a
     * B2B-only assignment.
     */
    @Query("""
            SELECT sp.store.id, sp.storePrice, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id = :productId
              AND sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.storePrice ASC
            """)
    List<Object[]> findCheapestB2cStoreByProductAndCountry(
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
     * B2C variant of {@link #findCheapestStoreGlobally}: only consumer-visible (b2cEnabled)
     * store rows are considered for the global fallback price.
     */
    @Query("""
            SELECT sp.storePrice, sp.store.country.currency, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id = :productId
              AND sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.storePrice ASC
            LIMIT 1
            """)
    List<Object[]> findCheapestB2cStoreGlobally(@Param("productId") UUID productId);

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
            ORDER BY sp.product.id, sp.store.country.currency, sp.store.id
            """)
    List<Object[]> findCheapestPricesGloballyBatch(@Param("productIds") List<UUID> productIds);

    /**
     * B2C variant of {@link #findCheapestPricesGloballyBatch}: only consumer-visible
     * (b2cEnabled) store rows contribute to the global fallback price per product.
     */
    @Query("""
            SELECT sp.product.id, sp.storePrice, sp.store.country.currency, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id IN :productIds
              AND sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
              AND sp.storePrice = (
                SELECT MIN(sp2.storePrice) FROM StoreProduct sp2
                WHERE sp2.product.id = sp.product.id
                  AND sp2.isActive = true
                  AND sp2.b2cEnabled = true
                  AND sp2.deletedAt IS NULL
                  AND sp2.store.deletedAt IS NULL
              )
            ORDER BY sp.product.id, sp.store.country.currency, sp.store.id
            """)
    List<Object[]> findCheapestB2cPricesGloballyBatch(@Param("productIds") List<UUID> productIds);

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
     * B2C variant of {@link #findAllStoresPerProductBatch}: only consumer-visible (b2cEnabled)
     * store rows are returned, so consumer store options never include B2B-only assignments.
     */
    @Query("""
            SELECT sp.product.id, sp.store.id, sp.storePrice, sp.discountType, sp.discountValue FROM StoreProduct sp
            WHERE sp.product.id IN :productIds
              AND sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.storePrice ASC
            """)
    List<Object[]> findAllB2cStoresPerProductBatch(
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
}
