package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Pins the lookup both re-pricing sites share: which rows it fetches, and how many queries that costs.
 *
 * <p>Two properties are in tension and both matter. A variant line must be VISIBLE to re-pricing —
 * {@code liveListings} used to filter variant lines out entirely, which is why a variant line's price
 * was frozen at add-to-cart forever and why neither the cart nor the checkout could ever correct it.
 * And the whole basket must still cost a bounded number of queries, because this runs on the hottest
 * customer endpoint in the shop, on the read that happens before anything can be displayed.
 *
 * <p>What a variant line is NOT is a second kind of price. It is priced from the same parent listing as
 * every other line, so the lookup reads {@code store_product_variants} not at all — the reasoning is on
 * {@link CartLinePricing}. The zero-interactions assertions below are that rule, expressed as a query
 * count: reintroducing a per-variant price would have to reintroduce the query first.
 */
class BasketLookupTest {

    private static final Instant DURING = Instant.parse("2026-03-26T10:00:00Z");

    private final StoreProductRepository listings = mock(StoreProductRepository.class);
    private final StoreProductVariantRepository variants = mock(StoreProductVariantRepository.class);

    private final Store store = new Store();
    private final Product product = new Product();
    private final StoreProduct listing = new StoreProduct();

    BasketLookupTest() {
        store.setId(UUID.randomUUID());
        product.setId(UUID.randomUUID());
        listing.setId(UUID.randomUUID());
        listing.setStore(store);
        listing.setProduct(product);
        listing.setStorePrice(new BigDecimal("1000.00"));
        listing.setDiscountType(Product.DiscountType.PERCENTAGE);
        listing.setDiscountValue(new BigDecimal("25"));
        listing.setDiscountEndsAt(Instant.parse("2026-03-31T20:00:00Z"));
        when(listings.findActiveByStoreIdsAndProductIds(anyList(), anyList()))
                .thenReturn(List.of(listing));
    }

    private CartItem line(ProductVariant variant, String stamped) {
        return new CartItem(new Cart(), product, variant, 1, new BigDecimal(stamped), store.getId());
    }

    private static ProductVariant someVariant() {
        ProductVariant pv = new ProductVariant();
        pv.setId(UUID.randomUUID());
        return pv;
    }

    // ── A variant line is no longer invisible ────────────────────────────────

    @Test
    void aVariantLineIsPricedFromItsParentListingLikeEveryOtherLine() {
        CartItem line = line(someVariant(), "1000.00");

        CartLinePricing.Priced priced =
                CartLinePricing.liveListings(listings, List.of(line)).priceFor(line, DURING);

        assertNotNull(priced, "the whole bug was that this returned nothing");
        assertEquals(new BigDecimal("750.00"), priced.unitPrice(), "25% off the PARENT's 1000");
        assertEquals(new BigDecimal("1000.00"), priced.originalUnitPrice(),
                "and the 'was' figure is the parent's too — the one number the card showed");
    }

    @Test
    void aVariantLineAndAVariantLessLineOfTheSameListingCostTheSame() {
        // The invariant option A rests on: a variantId picks the SKU and the stock ceiling, never the
        // price. If these two ever differ, the web storefront (which sends no variantId) and the app are
        // charging different money for the same basket.
        CartItem withVariant = line(someVariant(), "1000.00");
        CartItem without = line(null, "1000.00");
        CartLinePricing.Basket basket =
                CartLinePricing.liveListings(listings, List.of(withVariant, without));

        assertEquals(basket.priceFor(without, DURING).unitPrice(),
                basket.priceFor(withVariant, DURING).unitPrice());
    }

    @Test
    void theParentListingIsWhatAVariantLineIsPricedFrom() {
        // Not a lookup keyed by variant: the price and the discount both live on the parent, so the
        // parent row has to come back for a variant line as much as for a variant-less one.
        CartItem line = line(someVariant(), "1000.00");

        assertSame(listing, CartLinePricing.liveListings(listings, List.of(line)).listingFor(line));
    }

    // ── Query counts ─────────────────────────────────────────────────────────

    @Test
    void aWholeBasketCostsExactlyOneQueryNoMatterHowManyLines() {
        List<CartItem> basket = List.of(line(someVariant(), "1000.00"), line(someVariant(), "1000.00"),
                line(null, "1000.00"));

        CartLinePricing.liveListings(listings, basket);

        verify(listings, times(1)).findActiveByStoreIdsAndProductIds(anyList(), anyList());
        verifyNoInteractions(variants);
    }

    @Test
    void noBasketEverReadsTheVariantRowsToDecideAPrice() {
        // store_product_variants.store_price is not quoted by any card, rail, search result or product
        // page, so pricing from it would be a second notion of "the price". Not reading the table is how
        // that stays true.
        CartLinePricing.liveListings(listings, List.of(line(someVariant(), "1000.00")));

        verifyNoInteractions(variants);
    }

    @Test
    void anEmptyBasketAsksForNothing() {
        CartLinePricing.liveListings(listings, List.of());

        verifyNoInteractions(listings);
    }

    // ── Rows that cannot be priced ───────────────────────────────────────────

    @Test
    void aLineWhoseListingIsGoneIsLeftAloneRatherThanRePricedToNothing() {
        when(listings.findActiveByStoreIdsAndProductIds(anyList(), anyList())).thenReturn(List.of());
        CartItem line = line(null, "1000.00");

        assertNull(CartLinePricing.liveListings(listings, List.of(line)).priceFor(line, DURING),
                "an availability problem must stay an availability problem, not become a 409 about money");
    }

    @Test
    void aVariantLineWhoseListingIsGoneIsSkippedForTheSameReason() {
        when(listings.findActiveByStoreIdsAndProductIds(anyList(), anyList())).thenReturn(List.of());
        CartItem line = line(someVariant(), "1000.00");

        assertNull(CartLinePricing.liveListings(listings, List.of(line)).priceFor(line, DURING));
    }
}
