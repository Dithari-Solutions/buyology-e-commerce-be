package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.StoreProduct;
import org.springframework.data.domain.Pageable;
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

    /**
     * Every row for this store/product pair, INCLUDING a soft-deleted one.
     *
     * <p>The variant above filters {@code isActive}, which makes it the wrong tool for deciding whether
     * an assignment may be created: removing an assignment soft-deletes the row rather than deleting
     * it, so the filtered lookup answers "no such assignment" while the row is still sitting in the
     * table — and the unconditional {@code UNIQUE (store_id, product_id)} constraint then rejects the
     * insert with "A record with the same unique value already exists". A product could be removed from
     * a store exactly once and never added back.
     *
     * <p>So assignment resolves through this one and revives the tombstone instead of inserting.
     */
    Optional<StoreProduct> findByStore_IdAndProduct_Id(UUID storeId, UUID productId);
    Optional<StoreProduct> findByProduct_Id(UUID productId);

    List<StoreProduct> findByStore_IdAndDeletedAtIsNull(UUID storeId);

    /**
     * The same rows as {@link #findByStore_IdAndDeletedAtIsNull}, with the product, brand and category
     * loaded in the one query — an export reads all three for every row.
     */
    @Query("""
            SELECT sp FROM StoreProduct sp
            JOIN FETCH sp.product p
            LEFT JOIN FETCH p.brand
            LEFT JOIN FETCH p.category
            WHERE sp.store.id = :storeId
              AND sp.deletedAt IS NULL
            """)
    List<StoreProduct> findForExportByStoreId(@Param("storeId") UUID storeId);

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
     * B2C channel: whether a product has at least one active, consumer-visible (b2cEnabled)
     * store assignment. Used to gate the consumer product-detail endpoints so a B2B-only
     * product (b2c off) is never reachable by direct id/slug on the consumer channel.
     */
    @Query("""
            SELECT CASE WHEN COUNT(sp) > 0 THEN true ELSE false END FROM StoreProduct sp
            WHERE sp.product.id = :productId
              AND sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            """)
    boolean existsB2cActiveByProductId(@Param("productId") UUID productId);

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
     * B2B channel: the actual b2bEnabled StoreProduct assignments (not just the products) for the
     * given B2B-enabled country. Ordered by id so callers can deterministically pick one row per
     * product (the storeProductId the storefront needs to add the product to the RFQ quote cart).
     */
    @Query("""
            SELECT sp FROM StoreProduct sp
            WHERE sp.store.country.code = :countryCode
              AND sp.store.country.b2bEnabled = true
              AND sp.isActive = true
              AND sp.b2bEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.id ASC
            """)
    List<StoreProduct> findB2bActiveAssignmentsByCountryCode(@Param("countryCode") String countryCode);

    /**
     * B2B channel (no country): b2bEnabled StoreProduct assignments across ANY B2B-enabled country.
     * Ordered by id for deterministic one-row-per-product selection.
     */
    @Query("""
            SELECT sp FROM StoreProduct sp
            WHERE sp.store.country.b2bEnabled = true
              AND sp.isActive = true
              AND sp.b2bEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.id ASC
            """)
    List<StoreProduct> findB2bActiveAssignmentsInB2bCountries();

    /**
     * Returns the lowest store price for a product in the given country.
     * The price is in the country's native currency (Country.currency).
     *
     * <p>PRE-DISCOUNT. This answers in raw {@code storePrice} and knows nothing about
     * discount_type/discount_value or the V60 window, so it is the wrong query to build anything
     * price-facing on — a product flash-sold from 2000 to 999 still answers 2000 here. It has no
     * callers today; keep it that way unless the caller genuinely wants the list price.
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
     * Returns [storeId, storePrice, discountType, discountValue, discountStartsAt, discountEndsAt]
     * for every consumer-visible (b2cEnabled) store carrying the product in the given country,
     * ordered by price ASC so the first element is the cheapest. B2B-only assignments are excluded,
     * so the consumer price is never derived from one.
     *
     * <p>The two window columns are part of the select list and not an optional extra: the price is
     * resolved by {@code StoreProduct.effectivePrice(...)}, which cannot tell whether a discount is
     * live without them. Drop them and this query happily reports a sale price for a sale that
     * ended last month — on the product detail page and every country-scoped card.
     */
    @Query("""
            SELECT sp.store.id, sp.storePrice, sp.discountType, sp.discountValue,
                   sp.discountStartsAt, sp.discountEndsAt FROM StoreProduct sp
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
     *
     * <p>PRE-DISCOUNT. This answers in raw {@code storePrice} and knows nothing about
     * discount_type/discount_value or the V60 window, so it is the wrong query to build anything
     * price-facing on — a product flash-sold from 2000 to 999 still answers 2000 here. It has no
     * callers today; keep it that way unless the caller genuinely wants the list price.
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
     * Returns [storePrice, currency, discountType, discountValue, discountStartsAt, discountEndsAt]
     * for the globally cheapest consumer-visible store carrying the product. This is the
     * no-country / not-available-in-country fallback — what an anonymous first-time visitor with no
     * market selected is shown — so the window columns matter here for exactly the same reason.
     */
    @Query("""
            SELECT sp.storePrice, sp.store.country.currency, sp.discountType, sp.discountValue,
                   sp.discountStartsAt, sp.discountEndsAt FROM StoreProduct sp
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
     * Batch global fallback: [productId, storePrice, currency, discountType, discountValue,
     * discountStartsAt, discountEndsAt] for the globally cheapest consumer-visible store per product.
     * The window columns are on the OUTER select only — the correlated MIN() subquery is a
     * store-PICKER, not a price, and adding columns to it would change which store is chosen.
     */
    @Query("""
            SELECT sp.product.id, sp.storePrice, sp.store.country.currency, sp.discountType, sp.discountValue,
                   sp.discountStartsAt, sp.discountEndsAt FROM StoreProduct sp
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
     * Batch: [productId, storeId, storePrice, discountType, discountValue, discountStartsAt,
     * discountEndsAt] for every consumer-visible store per product in the given country, ordered by
     * price ASC. B2B-only assignments are excluded.
     *
     * <p>The highest-traffic pricing query in the shop — it feeds applyBatchCountryPricing, and so
     * every list, rail, search result and the price-filter slider bounds. If the window columns ever
     * go missing from this select list, the entire catalogue goes back to advertising expired sales.
     */
    @Query("""
            SELECT sp.product.id, sp.store.id, sp.storePrice, sp.discountType, sp.discountValue,
                   sp.discountStartsAt, sp.discountEndsAt FROM StoreProduct sp
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
     *
     * <p>PRE-DISCOUNT. This answers in raw {@code storePrice} and knows nothing about
     * discount_type/discount_value or the V60 window, so it is the wrong query to build anything
     * price-facing on — a product flash-sold from 2000 to 999 still answers 2000 here. It has no
     * callers today; keep it that way unless the caller genuinely wants the list price.
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
     * The active listings behind a whole basket, in ONE query.
     *
     * <p>Exists because re-pricing a cart line needs its live listing, and asking for them one line
     * at a time turned the hottest customer endpoint into N+1: a cart GET issued one query per line
     * before it could tell the shopper what anything costs, and checkout issued the same set again.
     *
     * <p>Deliberately the same predicate as
     * {@link #findByStore_IdAndProduct_IdAndIsActiveTrue} — {@code isActive} only, NOT
     * {@code deletedAt} — because it replaces exactly that call in a loop. Adding a filter here
     * would quietly change which lines get re-priced.
     *
     * <p>The pairs are matched by the CALLER: this returns every combination of the two id lists
     * that exists, which may include a store/product pair no line actually asked for. Keying the
     * result on (storeId, productId) and looking each line up is what makes that harmless.
     */
    @Query("""
            SELECT sp FROM StoreProduct sp
            WHERE sp.store.id IN :storeIds
              AND sp.product.id IN :productIds
              AND sp.isActive = true
            """)
    List<StoreProduct> findActiveByStoreIdsAndProductIds(@Param("storeIds") List<UUID> storeIds,
                                                         @Param("productIds") List<UUID> productIds);

    // ── Flash sale ────────────────────────────────────────────────────────────
    //
    // "On the flash sale" is DERIVED, never stored: a discount that is configured, inside its
    // window, and has an END. There is no boolean to fall out of step with the dates, so a sale
    // cannot finish while a flag still claims it is running. Every query below therefore takes the
    // caller's `now` rather than reading the clock itself — one instant per request, so a rail and
    // the prices inside it cannot straddle the moment a sale ends.
    //
    // The un-windowed MIN(storePrice) helpers that used to live above were deleted rather than
    // widened: they had no callers, and a correctly-shaped projection that silently ignores the
    // window is exactly what the next person writing one of these would have copied.

    /**
     * Every assignment on the flash sale, for the admin screen: LIVE now or SCHEDULED to start.
     * An ended sale is excluded — it is over, the price has already reverted, and listing it would
     * invite an admin to "manage" a row that no longer affects anything.
     *
     * <p>Soft-deleted rows are excluded; inactive ones are NOT, because an admin needs to see a sale
     * they have scheduled on a listing they have temporarily switched off.
     */
    @Query("""
            SELECT sp FROM StoreProduct sp
            WHERE sp.discountType IS NOT NULL
              AND sp.discountValue IS NOT NULL
              AND sp.discountEndsAt IS NOT NULL
              AND sp.discountEndsAt >= :now
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.discountEndsAt ASC, sp.id ASC
            """)
    List<StoreProduct> findFlashSaleAssignments(@Param("now") java.time.Instant now, Pageable pageable);

    /** How many assignments {@link #findFlashSaleAssignments} would return, for the page envelope. */
    @Query("""
            SELECT COUNT(sp) FROM StoreProduct sp
            WHERE sp.discountType IS NOT NULL
              AND sp.discountValue IS NOT NULL
              AND sp.discountEndsAt IS NOT NULL
              AND sp.discountEndsAt >= :now
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            """)
    long countFlashSaleAssignments(@Param("now") java.time.Instant now);

    /** As {@link #findFlashSaleAssignments} but scoped to one store. */
    @Query("""
            SELECT sp FROM StoreProduct sp
            WHERE sp.store.id = :storeId
              AND sp.discountType IS NOT NULL
              AND sp.discountValue IS NOT NULL
              AND sp.discountEndsAt IS NOT NULL
              AND sp.discountEndsAt >= :now
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            ORDER BY sp.discountEndsAt ASC, sp.id ASC
            """)
    List<StoreProduct> findFlashSaleAssignmentsByStore(@Param("storeId") UUID storeId,
                                                      @Param("now") java.time.Instant now,
                                                      Pageable pageable);

    /** The one-store count, for the page envelope. */
    @Query("""
            SELECT COUNT(sp) FROM StoreProduct sp
            WHERE sp.store.id = :storeId
              AND sp.discountType IS NOT NULL
              AND sp.discountValue IS NOT NULL
              AND sp.discountEndsAt IS NOT NULL
              AND sp.discountEndsAt >= :now
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
            """)
    long countFlashSaleAssignmentsByStore(@Param("storeId") UUID storeId,
                                          @Param("now") java.time.Instant now);

    /**
     * The storefront/app rail: ACTIVE products with a LIVE flash sale in the given country, as
     * [productId, soonest discountEndsAt], soonest first.
     *
     * <p>Only sales that have actually STARTED, unlike the admin query above — a shopper must never
     * be shown a rail entry whose price has not dropped yet.
     *
     * <p>The end instant is selected and ordered on rather than left to the caller for one reason:
     * the rail is paged, and "soonest ending first" has to be decided by the DATABASE or page 2
     * overlaps page 1. MIN() because a product can be on sale in several stores with different end
     * dates, and the rail's promise is the earliest of them.
     *
     * <p>{@code Pageable} for the same reason the admin listing has one. Without it this returned EVERY
     * matching product id in the shop and the caller sliced the page out in Java, so a rail asking for
     * twelve items paid for a GROUP BY over the whole discounted catalogue — on the storefront home
     * screen, the most-hit endpoint there is. Pass an UNSORTED page: the ORDER BY above is the rail's
     * promise, and a sort on the Pageable would be appended to it and could reorder the countdown.
     */
    @Query("""
            SELECT sp.product.id, MIN(sp.discountEndsAt) FROM StoreProduct sp
            WHERE sp.store.country.code = :countryCode
              AND sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.discountType IS NOT NULL
              AND sp.discountValue IS NOT NULL
              AND sp.discountEndsAt IS NOT NULL
              AND sp.discountEndsAt >= :now
              AND (sp.discountStartsAt IS NULL OR sp.discountStartsAt <= :now)
            GROUP BY sp.product.id
            ORDER BY MIN(sp.discountEndsAt) ASC, sp.product.id ASC
            """)
    List<Object[]> findB2cFlashSaleProductIdsByCountryCode(@Param("countryCode") String countryCode,
                                                          @Param("now") java.time.Instant now,
                                                          Pageable pageable);

    /** The same rail with no market selected — every country's live flash sales. Paged in SQL too. */
    @Query("""
            SELECT sp.product.id, MIN(sp.discountEndsAt) FROM StoreProduct sp
            WHERE sp.isActive = true
              AND sp.b2cEnabled = true
              AND sp.deletedAt IS NULL
              AND sp.store.deletedAt IS NULL
              AND sp.product.status = 'ACTIVE'
              AND sp.discountType IS NOT NULL
              AND sp.discountValue IS NOT NULL
              AND sp.discountEndsAt IS NOT NULL
              AND sp.discountEndsAt >= :now
              AND (sp.discountStartsAt IS NULL OR sp.discountStartsAt <= :now)
            GROUP BY sp.product.id
            ORDER BY MIN(sp.discountEndsAt) ASC, sp.product.id ASC
            """)
    List<Object[]> findB2cFlashSaleProductIds(@Param("now") java.time.Instant now, Pageable pageable);
}
