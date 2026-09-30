package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.cart.service.CartLinePricing;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the ONE read-path method that produces the number a customer sees — {@code buildStoreOption}
 * — and asserts it equals what the cart will charge.
 *
 * <p>Why this exists alongside {@code PriceAgreementTest}. That one proves the ARITHMETIC agrees: it
 * calls {@code StoreProduct.effectivePrice} and {@code CartLinePricing.price} itself and compares the
 * results. Necessary and not sufficient — a test that calls the shared function twice cannot notice
 * that {@code buildStoreOption} reads the wrong projection index, forgets to pass the window, or
 * decides the strike-through on the store-currency pair while the card renders the converted one. Every
 * card, rail, category page, search result and store option gets its figure from this method, and
 * nothing executed it.
 *
 * <p>It is also where the second attempt at variant pricing went wrong, so the first test below is a
 * regression pin rather than a description: this method must quote the PARENT listing's price. Quoting
 * the cheapest variant here made the card say 900 for a listing the web storefront's basket charged
 * 1000 for — advertised below charged, on the busier client, which is worse than the bug it was fixing.
 *
 * <p>Run on a ProductService built without its constructor (twenty-six collaborators; this method
 * touches one) with only the FX service injected.
 */
class CardPricingTest {

    private static final Instant STARTS = Instant.parse("2026-03-25T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-31T20:00:00Z");
    private static final Instant DURING = Instant.parse("2026-03-26T10:00:00Z");
    private static final Instant AFTER = Instant.parse("2026-04-05T10:00:00Z");
    /** An hour before the window opens: the card is showing the PRE-sale price. */
    private static final Instant BEFORE = Instant.parse("2026-03-24T23:00:00Z");

    private static final BigDecimal PARENT = new BigDecimal("1000.00");

    private ProductService service;
    private CurrencyExchangeService fx;

    @BeforeEach
    void setUp() throws Exception {
        service = Mockito.mock(ProductService.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        fx = Mockito.mock(CurrencyExchangeService.class);
        identityFx();
        Field f = ProductService.class.getDeclaredField("currencyExchangeService");
        f.setAccessible(true);
        f.set(service, fx);
    }

    /** Same-currency card: convert is a no-op, so the asserted figures are the store's own. */
    private void identityFx() {
        Mockito.when(fx.convert(Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private StoreProduct listing(Product.DiscountType type, String value, Instant startsAt, Instant endsAt) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        Product product = new Product();
        product.setId(UUID.randomUUID());
        StoreProduct sp = new StoreProduct(store, product, PARENT);
        sp.setId(UUID.randomUUID());
        sp.setDiscountType(type);
        sp.setDiscountValue(value == null ? null : new BigDecimal(value));
        sp.setDiscountStartsAt(startsAt);
        sp.setDiscountEndsAt(endsAt);
        return sp;
    }

    /** The real method, reached by reflection and fed exactly what the projection row carries. */
    private ProductResponse.StoreOptionDto card(StoreProduct sp, Instant now, String from, String to)
            throws Exception {
        Method m = ProductService.class.getDeclaredMethod("buildStoreOption",
                UUID.class, BigDecimal.class, Object.class, BigDecimal.class,
                Instant.class, Instant.class, Instant.class,
                String.class, String.class, Boolean.class);
        m.setAccessible(true);
        return (ProductResponse.StoreOptionDto) m.invoke(service,
                sp.getStore().getId(), sp.getStorePrice(), sp.getDiscountType(), sp.getDiscountValue(),
                sp.getDiscountStartsAt(), sp.getDiscountEndsAt(), now, from, to, null);
    }

    // ── Advertised == charged ─────────────────────────────────────────────────

    @Test
    void theCardQuotesTheListingsDiscountedPriceAndTheCartChargesThat() throws Exception {
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        ProductResponse.StoreOptionDto card = card(sp, DURING, "AED", "AED");

        assertEquals(new BigDecimal("750.00"), card.getStorePrice());
        assertEquals(PARENT, card.getOriginalPrice(), "the struck-through 'was' price");
        assertEquals(ENDS, card.getFlashSaleEndsAt(), "and the countdown");
        assertEquals(CartLinePricing.price(sp, DURING).unitPrice(), card.getStorePrice(),
                "the figure on the card must be the figure the basket stamps");
    }

    @Test
    void theCardIsQuotedFromTheParentListingAndNeverFromAVariant() throws Exception {
        // The regression this method already caused once. Nothing about a variant reaches here — it takes
        // no variant argument at all, which is the structural form of the guarantee — so the only way to
        // reintroduce the divergence is to change this signature, and this test's compilation.
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        assertEquals(1, java.util.Arrays.stream(ProductService.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("buildStoreOption")).count(),
                "one buildStoreOption, so there is one number a card can quote");
        assertEquals(new BigDecimal("750.00"), card(sp, DURING, "AED", "AED").getStorePrice(),
                "25% off the PARENT's 1000, whatever any variant of it is priced at");
    }

    // ── The window ───────────────────────────────────────────────────────────

    @Test
    void outsideTheWindowTheCardQuotesTheListPriceWithNoSavingAndNoCountdown() throws Exception {
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        ProductResponse.StoreOptionDto card = card(sp, AFTER, "AED", "AED");

        assertEquals(PARENT, card.getStorePrice());
        assertNull(card.getOriginalPrice(), "an expired sale must not leave a fake saving on the page");
        assertNull(card.getFlashSaleEndsAt(), "nor a countdown that has already run out");
        assertEquals(CartLinePricing.price(sp, AFTER).unitPrice(), card.getStorePrice());
    }

    @Test
    void beforeTheWindowTheCardQuotesTheListPriceAndSaysWhenThatStopsBeingTrue() throws Exception {
        // A scheduled sale, an hour out. The price is the PRE-sale one and is correct right now, which is
        // exactly what makes the body perishable: it stops being correct when the window opens. Nothing
        // said so, so CatalogueCacheFilter had nothing to bound the entry — or the browser's copy — by,
        // and for up to six minutes after a sale began cards showed the old price while the cart charged
        // the new one. No countdown and no strike-through: the sale is not running.
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        ProductResponse.StoreOptionDto card = card(sp, BEFORE, "AED", "AED");

        assertEquals(PARENT, card.getStorePrice());
        assertEquals(CartLinePricing.price(sp, BEFORE).unitPrice(), card.getStorePrice(),
                "and the basket agrees: the sale has not started for either of them");
        assertNull(card.getOriginalPrice(), "there is no saving yet to strike through");
        assertNull(card.getFlashSaleEndsAt(), "and nothing to count down to");
        assertEquals(STARTS, card.getFlashSaleStartsAt(),
                "but the instant this price expires has to travel with it");
    }

    @Test
    void aPermanentMarkdownSaysNothingAboutAStartBecauseThereIsNothingToWaitFor() throws Exception {
        // A discount with two null dates prices identically at every instant, so its body never goes
        // stale and must not be given a shortened cache life.
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", null, null);

        assertNull(card(sp, DURING, "AED", "AED").getFlashSaleStartsAt());
    }

    @Test
    void aPermanentMarkdownGetsItsStruckThroughPriceButNoCountdown() throws Exception {
        // Every discount written before V60 is one of these. It is discounted, so the saving is real and
        // must show; it is not running out, so a countdown would promise an urgency that does not exist.
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", null, null);

        ProductResponse.StoreOptionDto card = card(sp, DURING, "AED", "AED");

        assertEquals(new BigDecimal("750.00"), card.getStorePrice());
        assertEquals(PARENT, card.getOriginalPrice());
        assertNull(card.getFlashSaleEndsAt());
    }

    // ── The display currency ─────────────────────────────────────────────────

    @Test
    void aSavingThatCollapsesInTheDisplayCurrencyLeavesNoBadgeAndNoCountdown() throws Exception {
        // The strike-through and the countdown are decided on the CONVERTED pair, because that is what
        // the card renders. Two figures a cent apart can round to the same number at many rates, and a
        // line drawn through a price identical to the one below it is a full-price card wearing a sale
        // badge — exactly what the rail must not show.
        Mockito.when(fx.convert(Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new BigDecimal("275.00"));
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "0.01", STARTS, ENDS);

        ProductResponse.StoreOptionDto card = card(sp, DURING, "AED", "AZN");

        assertEquals(new BigDecimal("275.00"), card.getStorePrice());
        assertNull(card.getOriginalPrice());
        assertNull(card.getFlashSaleEndsAt());
    }

    @Test
    void anUnknownExchangeRateLeavesNoPriceRatherThanABadgeOverNothing() throws Exception {
        // convert() answers null when there is no rate for the requested display currency. The discount
        // branch must not fire on a null price, or the rail gets a badge and a countdown over a blank.
        Mockito.when(fx.convert(Mockito.any(), Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        ProductResponse.StoreOptionDto card = card(sp, DURING, "AED", "XXX");

        assertNull(card.getStorePrice());
        assertNull(card.getOriginalPrice());
        assertNull(card.getFlashSaleEndsAt());
    }
}
