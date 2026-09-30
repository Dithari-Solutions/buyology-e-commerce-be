package com.buyology.ecommerce.store.domain;

import com.buyology.ecommerce.product.domain.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the one function that decides what a customer pays.
 *
 * <p>{@code StoreProduct.effectivePrice} is not a display helper — CartService stamps its result
 * onto the cart row, createBuyNowOrder charges it, and every product card and search result is priced
 * through its static twin. Before V60 it had no notion of time and, remarkably, no test at all: a grep
 * for {@code effectivePrice} across src/test returned nothing, so the FIXED/PERCENTAGE arithmetic and
 * the "genuinely lower" rule behind the struck-through price were both uncovered.
 *
 * <p>The assertions below are therefore about money, and each one names what breaks if it fails.
 */
class DiscountWindowPricingTest {

    private static final Instant MARCH_20 = Instant.parse("2026-03-20T00:00:00Z");
    private static final Instant MARCH_25 = Instant.parse("2026-03-25T00:00:00Z");
    private static final Instant MARCH_31 = Instant.parse("2026-03-31T00:00:00Z");
    private static final Instant APRIL_05 = Instant.parse("2026-04-05T00:00:00Z");

    private StoreProduct listing(String storePrice, Product.DiscountType type, String value,
                                 Instant startsAt, Instant endsAt) {
        StoreProduct sp = new StoreProduct();
        sp.setStorePrice(new BigDecimal(storePrice));
        sp.setDiscountType(type);
        sp.setDiscountValue(value == null ? null : new BigDecimal(value));
        sp.setDiscountStartsAt(startsAt);
        sp.setDiscountEndsAt(endsAt);
        return sp;
    }

    // ── The compatibility contract ────────────────────────────────────────────

    @Test
    void aDiscountWithNoDatesPricesExactlyAsItAlwaysDid() {
        // THE contract of V60. Every discount in the production table today has two NULL dates, so if
        // this assertion breaks, adding the window silently re-prices the entire existing catalogue —
        // in either direction, on every card, cart and order at once.
        StoreProduct percentage = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", null, null);
        StoreProduct fixed = listing("2000.00", Product.DiscountType.FIXED, "999.00", null, null);

        assertEquals(new BigDecimal("1500.00"), percentage.effectivePrice(MARCH_25));
        assertEquals(new BigDecimal("999.00"), fixed.effectivePrice(MARCH_25));
        assertTrue(percentage.hasDiscount(MARCH_25));
        assertTrue(fixed.hasDiscount(MARCH_25));
    }

    @Test
    void noDiscountAtAllIsStillJustTheStorePrice() {
        StoreProduct plain = listing("2000.00", null, null, null, null);

        assertEquals(new BigDecimal("2000.00"), plain.effectivePrice(MARCH_25));
        assertFalse(plain.hasDiscount(MARCH_25));
        assertFalse(plain.onFlashSale(MARCH_25));
    }

    @Test
    void aHalfConfiguredDiscountIsIgnoredRatherThanGuessedAt() {
        // A type with no value, or a value with no type, is bad data — and the admin API refuses
        // both. If it ever reaches the entity, NOT discounting is the only safe reading: the
        // alternative is charging discountValue as a price, or zero.
        assertEquals(new BigDecimal("2000.00"),
                listing("2000.00", Product.DiscountType.PERCENTAGE, null, null, null).effectivePrice(MARCH_25));
        assertEquals(new BigDecimal("2000.00"),
                listing("2000.00", null, "25", null, null).effectivePrice(MARCH_25));
    }

    // ── Before, during, after ─────────────────────────────────────────────────

    @Test
    void beforeTheSaleStartsTheCustomerPaysFullPrice() {
        // A sale scheduled for next week must not discount today. If it does, an admin preparing a
        // campaign has already given the discount away, to everyone, silently.
        StoreProduct scheduled = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", MARCH_25, MARCH_31);

        assertEquals(new BigDecimal("2000.00"), scheduled.effectivePrice(MARCH_20));
        assertFalse(scheduled.hasDiscount(MARCH_20));
        // And no strike-through: a "was 2000" above a 2000 price is a lie the UI would render happily.
        assertFalse(scheduled.onFlashSale(MARCH_20));
    }

    @Test
    void duringTheSaleTheCustomerPaysTheSalePrice() {
        StoreProduct live = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", MARCH_25, MARCH_31);

        assertEquals(new BigDecimal("1500.00"), live.effectivePrice(MARCH_25.plusSeconds(3600)));
        assertTrue(live.hasDiscount(MARCH_25.plusSeconds(3600)));
        assertTrue(live.onFlashSale(MARCH_25.plusSeconds(3600)));
    }

    @Test
    void afterTheSaleEndsTheCustomerPaysFullPriceAgainWithNothingToClearUp() {
        // The whole reason expiry is a property of the data. No job runs, nothing is swept, and the
        // very next read prices at 2000 again — so a failed cron cannot keep a sale alive.
        StoreProduct ended = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", MARCH_25, MARCH_31);

        assertEquals(new BigDecimal("2000.00"), ended.effectivePrice(APRIL_05));
        assertFalse(ended.hasDiscount(APRIL_05));
        assertFalse(ended.onFlashSale(APRIL_05));
        assertNull(ended.flashSaleEndsAt(APRIL_05));
    }

    @Test
    void aStartWithNoEndMeansAlreadyRunningAndNeverStopping() {
        StoreProduct permanent = listing("2000.00", Product.DiscountType.FIXED, "999.00", MARCH_25, null);

        assertEquals(new BigDecimal("2000.00"), permanent.effectivePrice(MARCH_20));
        assertEquals(new BigDecimal("999.00"), permanent.effectivePrice(APRIL_05));
    }

    // ── The boundaries, decided on purpose ────────────────────────────────────

    @Test
    void theStartInstantItselfIsAlreadyOnSale() {
        // Inclusive start. A sale that says it starts at 20:00 and does not discount at 20:00:00.000
        // is a support ticket, and the shop has no other kind of "starts at".
        StoreProduct sp = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", MARCH_25, MARCH_31);

        assertEquals(new BigDecimal("1500.00"), sp.effectivePrice(MARCH_25));
        assertEquals(new BigDecimal("2000.00"), sp.effectivePrice(MARCH_25.minusMillis(1)));
    }

    @Test
    void theEndInstantItselfIsStillOnSaleAndTheNextMillisecondIsNot() {
        // Inclusive end, matching PromoCodeService (a code is expired only once expiresAt is strictly
        // before now). One rule for both, so "valid until" means the same thing everywhere in the shop.
        StoreProduct sp = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", MARCH_25, MARCH_31);

        assertEquals(new BigDecimal("1500.00"), sp.effectivePrice(MARCH_31));
        assertEquals(new BigDecimal("2000.00"), sp.effectivePrice(MARCH_31.plusMillis(1)));
    }

    // ── FIXED and PERCENTAGE arithmetic ───────────────────────────────────────

    @Test
    void fixedMeansTheValueIsTheNewPriceAndPercentageMeansPercentOff() {
        assertEquals(new BigDecimal("999.00"),
                listing("2000.00", Product.DiscountType.FIXED, "999.00", null, MARCH_31).effectivePrice(MARCH_25));
        // Rounded to 2dp HALF_UP: 1999.99 * 0.90 = 1799.991, and a third decimal in a charged price
        // would fail the payment gateway's own amount validation.
        assertEquals(new BigDecimal("1799.99"),
                listing("1999.99", Product.DiscountType.PERCENTAGE, "10", null, MARCH_31).effectivePrice(MARCH_25));
    }

    @Test
    void aFixedValueAboveTheStorePriceIsNotTreatedAsADiscount() {
        // The API refuses this (see FlashSaleValidationTest) but data can arrive from anywhere, and
        // hasDiscount() is what decides whether a struck-through "was" price is rendered. A price RISE
        // must never render as a saving.
        StoreProduct rise = listing("999.00", Product.DiscountType.FIXED, "2000.00", null, MARCH_31);

        assertFalse(rise.hasDiscount(MARCH_25));
        assertFalse(rise.onFlashSale(MARCH_25));
    }

    @Test
    void aZeroPercentDiscountIsNotADiscount() {
        assertFalse(listing("2000.00", Product.DiscountType.PERCENTAGE, "0", null, MARCH_31)
                .hasDiscount(MARCH_25));
    }

    // ── Flash sale vs permanent markdown ──────────────────────────────────────

    @Test
    void aPermanentMarkdownIsDiscountedButIsNotAFlashSale() {
        // The distinction the rail and the countdown are built on. Call a permanent markdown a flash
        // sale and every clearance line in the shop sprouts a countdown to nothing.
        StoreProduct permanent = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", null, null);

        assertTrue(permanent.hasDiscount(MARCH_25));
        assertFalse(permanent.onFlashSale(MARCH_25));
        assertNull(permanent.flashSaleEndsAt(MARCH_25));
    }

    @Test
    void onFlashSaleIsTrueOnlyForALiveDiscountWithAFutureEnd() {
        StoreProduct sale = listing("2000.00", Product.DiscountType.PERCENTAGE, "25", MARCH_25, MARCH_31);

        assertFalse(sale.onFlashSale(MARCH_20), "not started yet");
        assertTrue(sale.onFlashSale(MARCH_25), "live");
        assertEquals(MARCH_31, sale.flashSaleEndsAt(MARCH_25));
        assertFalse(sale.onFlashSale(APRIL_05), "over");
    }

    @Test
    void anEndDateOnAProductWithNoActualSavingIsNotAFlashSale() {
        // A 0% "sale" with a countdown would draw shoppers to a rail of full-price products.
        StoreProduct nothing = listing("2000.00", Product.DiscountType.PERCENTAGE, "0", MARCH_25, MARCH_31);

        assertFalse(nothing.onFlashSale(MARCH_25));
    }

    // ── The static overload the projection queries use ────────────────────────

    @Test
    void theStaticOverloadAppliesTheSameWindowAsTheEntity() {
        // Product lists and search do not have an entity — they price straight off projection rows.
        // If this drifted from the entity, the catalogue would advertise one price and the cart would
        // charge another, which is the single failure this whole design exists to make impossible.
        BigDecimal price = new BigDecimal("2000.00");
        BigDecimal value = new BigDecimal("25");

        assertEquals(new BigDecimal("2000.00"), StoreProduct.effectivePrice(
                price, Product.DiscountType.PERCENTAGE, value, MARCH_25, MARCH_31, MARCH_20));
        assertEquals(new BigDecimal("1500.00"), StoreProduct.effectivePrice(
                price, Product.DiscountType.PERCENTAGE, value, MARCH_25, MARCH_31, MARCH_25));
        assertEquals(new BigDecimal("2000.00"), StoreProduct.effectivePrice(
                price, Product.DiscountType.PERCENTAGE, value, MARCH_25, MARCH_31, APRIL_05));
        // And with both dates null it is the pre-V60 answer, which is what the existing rows need.
        assertEquals(new BigDecimal("1500.00"), StoreProduct.effectivePrice(
                price, Product.DiscountType.PERCENTAGE, value, null, null, APRIL_05));
    }

    @Test
    void aNullNowFallsBackToTheRealClockRatherThanRefusingToPrice() {
        // Defensive: a caller that forgets to thread `now` must still get a price, not a null or a
        // crash on a product page. It reads the clock, which is exactly the behaviour the no-arg
        // instance overload has.
        assertEquals(new BigDecimal("1500.00"), StoreProduct.effectivePrice(
                new BigDecimal("2000.00"), Product.DiscountType.PERCENTAGE, new BigDecimal("25"),
                null, null, null));
    }

    @Test
    void aNullStorePriceStaysNullInsteadOfBecomingTheDiscountValue() {
        assertNull(StoreProduct.effectivePrice(
                null, Product.DiscountType.FIXED, new BigDecimal("999.00"), null, null, MARCH_25));
    }
}
