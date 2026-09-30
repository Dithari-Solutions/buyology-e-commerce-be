package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what may appear in the flash-sale rail.
 *
 * <p>The query behind the rail asks whether a product is on sale in ANY consumer-visible store. The
 * CARD answers a different question: its price comes from one store option — the express one, else the
 * cheapest — and with no market selected from the globally cheapest store instead. Either can easily
 * be a store that is not on the sale, and with no country at all that was the usual case. A rail
 * listing full-price products with no badge and no countdown is the feature visibly not working, so
 * the rail is filtered by what the response itself ended up saying.
 */
class FlashSaleRailTest {

    private static final Instant NOW = Instant.parse("2026-03-26T10:00:00Z");
    private static final Instant ENDS = NOW.plus(2, ChronoUnit.HOURS);

    private static ProductResponse onFlashSale() {
        ProductResponse r = new ProductResponse();
        r.setStorePrice(new BigDecimal("1500.00"));
        r.setOriginalPrice(new BigDecimal("2000.00"));
        r.setOnFlashSale(Boolean.TRUE);
        r.setFlashSaleEndsAt(ENDS);
        return r;
    }

    /** Discounted, but with no end date — a permanent markdown, which is not a flash sale. */
    private static ProductResponse permanentMarkdown() {
        ProductResponse r = new ProductResponse();
        r.setStorePrice(new BigDecimal("1500.00"));
        r.setOriginalPrice(new BigDecimal("2000.00"));
        return r;
    }

    /** On sale in some other store; priced here from a store that is not. */
    private static ProductResponse fullPrice() {
        ProductResponse r = new ProductResponse();
        r.setStorePrice(new BigDecimal("2000.00"));
        return r;
    }

    @Test
    void onlyProductsWhoseResolvedPriceIsOnSaleSurvive() {
        List<ProductResponse> rail = ProductService.onlyOnFlashSale(
                List.of(fullPrice(), onFlashSale(), permanentMarkdown()), NOW);

        assertEquals(1, rail.size(), "a rail entry must be a product whose OWN price is discounted now");
        assertEquals(ENDS, rail.get(0).getFlashSaleEndsAt());
    }

    @Test
    void aPermanentMarkdownIsNotPromotedIntoTheRail() {
        // It is discounted, so it is tempting. But it is not running out, and a countdown over it would
        // promise an urgency that does not exist.
        assertTrue(ProductService.onlyOnFlashSale(List.of(permanentMarkdown()), NOW).isEmpty());
    }

    @Test
    void aProductWithNoPriceAtAllIsNotInTheRail() {
        // applyBatchCountryPricing leaves everything null when it finds no store option — no price, no
        // badge, nothing to count down. Before the filter, that was a rail entry.
        assertTrue(ProductService.onlyOnFlashSale(List.of(new ProductResponse()), NOW).isEmpty());
    }

    // ── The three ways a FULL-PRICE card still got through the flag ──────────

    @Test
    void aBadgeOverNoPriceAtAllIsNotARailEntry() {
        // onFlashSale is set inside the discount branch; storePrice is set outside it. A currency
        // conversion that yields null (no FX rate for the requested display currency) left the flag and
        // the countdown standing over a blank price.
        ProductResponse priceless = onFlashSale();
        priceless.setStorePrice(null);

        assertTrue(ProductService.onlyOnFlashSale(List.of(priceless), NOW).isEmpty());
    }

    @Test
    void aSavingThatVanishesInTheDisplayCurrencyIsNotARailEntry() {
        // The discount test used to run on the store's own figures while the card shows converted ones.
        // Two prices a cent apart round to the same number at many rates, and the result is a
        // strike-through and a countdown drawn over an identical price — a full-price card wearing a
        // sale badge, which is exactly what the rail must not contain.
        ProductResponse collapsed = onFlashSale();
        collapsed.setOriginalPrice(collapsed.getStorePrice());

        assertTrue(ProductService.onlyOnFlashSale(List.of(collapsed), NOW).isEmpty());

        ProductResponse noWasPrice = onFlashSale();
        noWasPrice.setOriginalPrice(null);
        assertTrue(ProductService.onlyOnFlashSale(List.of(noWasPrice), NOW).isEmpty());
    }

    @Test
    void aCountdownThatHasAlreadyRunOutIsNotARailEntry() {
        // The rail query and the pricing pass each used to read their own clock, so a sale ending
        // between the two produced an entry whose countdown started at zero. The filter is judged
        // against the caller's single instant, which is the one its query selected products with.
        ProductResponse expired = onFlashSale();
        expired.setFlashSaleEndsAt(NOW.minus(1, ChronoUnit.HOURS));

        assertTrue(ProductService.onlyOnFlashSale(List.of(expired), NOW).isEmpty());

        // The end instant itself is still INSIDE the window — one definition of "over" across the
        // codebase, matching StoreProduct.discountWindowContains and PromoCodeService.
        ProductResponse endingExactlyNow = onFlashSale();
        endingExactlyNow.setFlashSaleEndsAt(NOW);
        assertEquals(1, ProductService.onlyOnFlashSale(List.of(endingExactlyNow), NOW).size());
    }

    @Test
    void aPageMayComeBackShorterThanItWasAskedForRatherThanPaddedWithFullPriceItems() {
        List<ProductResponse> requested = List.of(
                fullPrice(), fullPrice(), onFlashSale(), fullPrice(), onFlashSale());

        assertEquals(2, ProductService.onlyOnFlashSale(requested, NOW).size(),
                "a short honest rail beats a full one quoting prices the shop is not discounting");
    }

    // ── The rail's own query, and that it is PAGED in SQL ─────────────────────
    //
    // PricingQueryContractTest pins that the repository methods TAKE a Pageable. Nothing pinned that
    // getFlashSaleProducts passes one — and it used to not: it selected every matching product id in the
    // shop and sliced the page out in Java, so twelve cards on the home screen cost a GROUP BY over the
    // whole discounted catalogue on the most-hit endpoint there is. That regression is invisible to a
    // test of the query text and to a test of the filter. So these two drive the real method, on an
    // empty result so it short-circuits before the collaborators it does not have.

    private static ProductService railService(StoreProductRepository repository) throws Exception {
        ProductService service = Mockito.mock(ProductService.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        Field f = ProductService.class.getDeclaredField("storeProductRepository");
        f.setAccessible(true);
        f.set(service, repository);
        return service;
    }

    @Test
    void theRailAsksTheDatabaseForOnePageAndNotForEveryDiscountedProductInTheShop() throws Exception {
        StoreProductRepository repository = Mockito.mock(StoreProductRepository.class);
        when(repository.findB2cFlashSaleProductIdsByCountryCode(anyString(), any(Instant.class), any()))
                .thenReturn(List.of());

        ApiResponse<List<ProductResponse>> body = railService(repository)
                .getFlashSaleProducts("EN", "uae", "AED", null, null, 2, 12).getBody();

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repository).findB2cFlashSaleProductIdsByCountryCode(
                eq("UAE"), any(Instant.class), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber(), "the page has to reach the database");
        assertEquals(12, pageable.getValue().getPageSize(), "and so does the size");
        assertTrue(pageable.getValue().getSort().isUnsorted(),
                "an unsorted page: the query's own ORDER BY is the rail's promise, and a Pageable sort "
                        + "would be appended to it and could reorder the countdown");
        assertNotNull(body);
        assertTrue(body.getData().isEmpty());
    }

    @Test
    void withNoMarketSelectedTheRailUsesTheGlobalQueryAndPagesThatToo() throws Exception {
        StoreProductRepository repository = Mockito.mock(StoreProductRepository.class);
        when(repository.findB2cFlashSaleProductIds(any(Instant.class), any())).thenReturn(List.of());

        railService(repository).getFlashSaleProducts("EN", null, null, null, null, 0, 500);

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repository).findB2cFlashSaleProductIds(any(Instant.class), pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize(),
                "and the size is capped, so one request cannot ask the database for the whole catalogue");
    }
}
