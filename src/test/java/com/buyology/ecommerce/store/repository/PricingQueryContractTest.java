package com.buyology.ecommerce.store.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the SELECT LISTS and the paging of the pricing queries, because nothing else can.
 *
 * <p>These are the queries the whole catalogue is priced from, and their callers read the rows by
 * INDEX: {@code row[5]} and {@code row[6]} are the discount window, without which every sale prices
 * forever. A column added, removed or reordered in the JPQL compiles fine, passes every other test,
 * and silently mis-prices the shop — a {@code ClassCastException} in production if you are lucky, the
 * wrong number on the card if you are not.
 *
 * <p>This repo has no in-memory database (Testcontainers cannot run in this environment and there is no
 * H2 on the test classpath), so a {@code @DataJpaTest} that actually EXECUTED these was not available.
 * Asserting on the annotation text is the strongest check that is, and it catches the exact regression
 * the select-list comments warn about. If a database ever joins the test setup, replace these with
 * executing versions rather than keeping both.
 */
class PricingQueryContractTest {

    private String jpql(String methodName) {
        return Arrays.stream(StoreProductRepository.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .map(m -> m.getAnnotation(Query.class))
                .map(Query::value)
                .orElseThrow(() -> new AssertionError("no @Query on " + methodName));
    }

    private Method method(Class<?> repository, String methodName) {
        return Arrays.stream(repository.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no such method: " + methodName));
    }

    /** The select list, flattened to a comparable single line. */
    private String selectList(String methodName) {
        String q = jpql(methodName).replaceAll("\\s+", " ");
        return q.substring(q.indexOf("SELECT") + 6, q.indexOf(" FROM ")).trim();
    }

    // ── The window columns, without which every sale is immortal ──────────────

    @Test
    void everyPricingProjectionCarriesBothWindowColumns() {
        // effectivePrice cannot tell whether a discount is live without them. Drop them and the whole
        // catalogue goes back to advertising sales that ended last month.
        for (String q : new String[]{"findCheapestB2cStoreByProductAndCountry",
                "findAllB2cStoresPerProductBatch", "findCheapestB2cStoreGlobally",
                "findCheapestB2cPricesGloballyBatch"}) {
            assertTrue(selectList(q).contains("sp.discountStartsAt"), q + " lost discountStartsAt");
            assertTrue(selectList(q).contains("sp.discountEndsAt"), q + " lost discountEndsAt");
        }
    }

    // ── The exact select lists the callers index into ─────────────────────────
    //
    // Pinned WHOLE rather than column by column, because the failure is positional: buildStoreOption is
    // handed row[1]..row[5] by position, so inserting a column anywhere before the end silently feeds a
    // date where a price belongs. These four also carry NO listing id: one was added to look variant rows
    // up by, and it is gone with the read-path variant quoting it existed for — a card quotes the parent
    // listing's price, which is the only price any cart line is charged. Re-adding a column here means
    // re-reading the indices in ProductService, which is exactly the review these assertions force.

    @Test
    void theStoreOptionProjectionIsExactlyWhatApplyCountryPricingIndexesInto() {
        assertEquals("sp.store.id, sp.storePrice, sp.discountType, sp.discountValue, "
                        + "sp.discountStartsAt, sp.discountEndsAt",
                selectList("findCheapestB2cStoreByProductAndCountry"));
    }

    @Test
    void theBatchStoreOptionProjectionIsExactlyWhatApplyBatchCountryPricingIndexesInto() {
        // The highest-traffic pricing query in the shop — every list, rail, search result and the
        // price-filter slider bounds resolve through it.
        assertEquals("sp.product.id, sp.store.id, sp.storePrice, sp.discountType, sp.discountValue, "
                        + "sp.discountStartsAt, sp.discountEndsAt",
                selectList("findAllB2cStoresPerProductBatch"));
    }

    @Test
    void bothGlobalFallbackProjectionsMatchWhatTheNoMarketPathIndexesInto() {
        assertEquals("sp.storePrice, sp.store.country.currency, sp.discountType, sp.discountValue, "
                        + "sp.discountStartsAt, sp.discountEndsAt",
                selectList("findCheapestB2cStoreGlobally"));
        assertEquals("sp.product.id, sp.storePrice, sp.store.country.currency, sp.discountType, "
                        + "sp.discountValue, sp.discountStartsAt, sp.discountEndsAt",
                selectList("findCheapestB2cPricesGloballyBatch"));
    }

    @Test
    void theGlobalStorePickerSubqueryStillPicksOnPriceAloneAndNothingElse() {
        // The correlated MIN() is a store-PICKER, not a price. Adding a column to it changes WHICH store
        // a no-market visitor is quoted, which is a fulfilment change disguised as a pricing one.
        String q = jpql("findCheapestB2cPricesGloballyBatch").replaceAll("\\s+", " ");
        assertTrue(q.contains("SELECT MIN(sp2.storePrice) FROM StoreProduct sp2"), q);
    }

    // ── The rail is paged in SQL, and ordered there ───────────────────────────

    @Test
    void bothRailQueriesTakeAPageableSoTheyCannotBeMaterialisedInFull() {
        // They used to return every matching product id in the shop, and the caller sliced the page out in
        // Java — a GROUP BY over the whole discounted catalogue to render twelve cards on the home screen.
        for (String q : new String[]{"findB2cFlashSaleProductIds", "findB2cFlashSaleProductIdsByCountryCode"}) {
            assertTrue(Arrays.asList(method(StoreProductRepository.class, q).getParameterTypes())
                            .contains(Pageable.class),
                    q + " must be paged in SQL, not sliced in Java");
        }
    }

    @Test
    void bothRailQueriesOrderInTheDatabaseOrPageTwoOverlapsPageOne() {
        // Soonest-ending first is the rail's promise AND its paging key. Ordered anywhere but the database,
        // page 2 can repeat and omit rows.
        for (String q : new String[]{"findB2cFlashSaleProductIds", "findB2cFlashSaleProductIdsByCountryCode"}) {
            String jpql = jpql(q).replaceAll("\\s+", " ");
            assertTrue(jpql.contains("ORDER BY MIN(sp.discountEndsAt) ASC, sp.product.id ASC"),
                    q + " lost its stable ordering: " + jpql);
            assertTrue(jpql.contains("GROUP BY sp.product.id"), q);
        }
    }

    @Test
    void theRailOnlyOffersSalesThatHaveActuallyStartedAndHaveNotEnded() {
        // A shopper must never see a rail entry whose price has not dropped yet, nor one whose has already
        // gone back up. Both bounds are in the SQL because the rail is paged on them.
        for (String q : new String[]{"findB2cFlashSaleProductIds", "findB2cFlashSaleProductIdsByCountryCode"}) {
            String jpql = jpql(q).replaceAll("\\s+", " ");
            assertTrue(jpql.contains("sp.discountEndsAt >= :now"), q);
            assertTrue(jpql.contains("(sp.discountStartsAt IS NULL OR sp.discountStartsAt <= :now)"), q);
            assertTrue(jpql.contains("sp.product.status = 'ACTIVE'"), q);
            assertTrue(jpql.contains("sp.b2cEnabled = true"), q + " must not rail a B2B-only assignment");
        }
    }

    // ── The basket's one lookup ──────────────────────────────────────────────

    @Test
    void theBasketListingLookupStillFiltersOnIsActiveOnlyAndNotOnDeletedAt() {
        // It replaced findByStore_IdAndProduct_IdAndIsActiveTrue called in a loop. Adding a filter here
        // would quietly change which cart lines get re-priced.
        String jpql = jpql("findActiveByStoreIdsAndProductIds").replaceAll("\\s+", " ");
        assertTrue(jpql.contains("sp.isActive = true"), jpql);
        assertFalse(jpql.contains("deletedAt"), "adding deletedAt here changes which lines re-price");
    }

    @Test
    void noPricingQueryReadsTheVariantTableAtAll() {
        // A variantId identifies a line and caps its stock; it never decides a price, so
        // store_product_variants contributes nothing to any figure a customer sees or is charged. The one
        // batch lookup that remains on that repository feeds the ADMIN listing, which is why it is
        // unfiltered on isActive — an admin has to see a variant they switched off.
        //
        // Asserted as the ABSENCE of an active-only counterpart: the read path's
        // findActiveByStoreProductIdIn existed only to price cards from the cheapest active variant, and
        // its return is the first step back to advertising one number and charging another.
        assertTrue(Arrays.stream(StoreProductVariantRepository.class.getDeclaredMethods())
                        .noneMatch(m -> m.getName().equals("findActiveByStoreProductIdIn")),
                "a catalogue-facing variant lookup is a second notion of 'the price'");

        assertNull(method(StoreProductVariantRepository.class, "findByStoreProduct_IdIn")
                        .getAnnotation(Query.class),
                "the admin's lookup is a derived query with no isActive filter, on purpose");
    }
}
