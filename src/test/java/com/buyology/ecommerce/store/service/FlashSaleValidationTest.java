package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.product.domain.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the refusals on the flash-sale admin path, and the timezone the dates are read in.
 *
 * <p>This is the one place in the feature where failing loudly is the correct behaviour. A refusal
 * costs an admin one message on one dashboard save; the alternative — storing a "sale" that raises the
 * price, or one that expired before it was saved — costs money on every order placed until somebody
 * notices, and nobody notices a price that is merely wrong.
 *
 * <p>The assertions are therefore mostly about what is NOT allowed, and about the exact instant a
 * calendar date becomes.
 */
class FlashSaleValidationTest {

    private static final Instant NOW = Instant.parse("2026-03-20T10:00:00Z");
    private static final BigDecimal STORE_PRICE = new BigDecimal("2000.00");

    // ── The discount ─────────────────────────────────────────────────────────

    @Test
    void aFixedPriceAtOrAboveTheStorePriceIsRefusedAsAPriceRise() {
        // The most dangerous typo available here: 2000 typed into "discounted amount" for a 2000 AED
        // product. Stored, it renders as a sale everywhere (the badge is built from discountValue) while
        // hasDiscount() is false, so the customer pays the same or more and the page claims a saving.
        IllegalArgumentException equal = assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.validateDiscount(Product.DiscountType.FIXED, STORE_PRICE, STORE_PRICE));
        assertTrue(equal.getMessage().contains("price rise"), equal.getMessage());

        assertThrows(IllegalArgumentException.class, () -> FlashSalePolicy.validateDiscount(
                Product.DiscountType.FIXED, new BigDecimal("2500.00"), STORE_PRICE));

        // And the legitimate one is allowed.
        assertDoesNotThrow(() -> FlashSalePolicy.validateDiscount(
                Product.DiscountType.FIXED, new BigDecimal("1499.00"), STORE_PRICE));
    }

    @Test
    void aPercentageOverOneHundredIsRefused() {
        // 120% off is a negative price, i.e. paying the customer to take it away.
        assertThrows(IllegalArgumentException.class, () -> FlashSalePolicy.validateDiscount(
                Product.DiscountType.PERCENTAGE, new BigDecimal("120"), STORE_PRICE));
        // Exactly 100 is free, not negative, and stays allowed — a giveaway is a deliberate campaign.
        assertDoesNotThrow(() -> FlashSalePolicy.validateDiscount(
                Product.DiscountType.PERCENTAGE, new BigDecimal("100"), STORE_PRICE));
    }

    @Test
    void zeroAndNegativeValuesAreRefusedForBothTypes() {
        // Zero is the empty form submitted by accident. It would store a "sale" that discounts nothing
        // and put a full-price product in the flash-sale rail with a countdown on it.
        for (Product.DiscountType type : Product.DiscountType.values()) {
            assertThrows(IllegalArgumentException.class, () ->
                    FlashSalePolicy.validateDiscount(type, BigDecimal.ZERO, STORE_PRICE));
            assertThrows(IllegalArgumentException.class, () ->
                    FlashSalePolicy.validateDiscount(type, new BigDecimal("-50"), STORE_PRICE));
        }
    }

    @Test
    void aTypeWithoutAValueAndAValueWithoutATypeAreBothRefused() {
        // Half a discount cannot be priced. The entity would ignore it, so the sale would appear in the
        // dashboard's list and do nothing at all.
        assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.validateDiscount(Product.DiscountType.PERCENTAGE, null, STORE_PRICE));
        assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.validateDiscount(null, new BigDecimal("25"), STORE_PRICE));
        assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.validateDiscount(null, null, STORE_PRICE));
    }

    // ── The window ───────────────────────────────────────────────────────────

    @Test
    void anEndBeforeItsStartIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> FlashSalePolicy.validateWindow(
                Instant.parse("2026-03-31T00:00:00Z"), Instant.parse("2026-03-25T00:00:00Z"), NOW));
    }

    @Test
    void anEndEqualToItsStartIsRefusedBecauseTheSaleWouldLastOneInstant() {
        Instant sameMoment = Instant.parse("2026-03-25T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> FlashSalePolicy.validateWindow(sameMoment, sameMoment, NOW));
    }

    @Test
    void anEndAlreadyInThePastIsRefusedRatherThanStored() {
        // The pricing code would handle it perfectly well — it would simply never discount. It is
        // refused because storing it leaves an admin looking at a row that says a product is on sale
        // while every customer pays full price, which is harder to spot than a 400.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.validateWindow(null, Instant.parse("2026-03-19T00:00:00Z"), NOW));
        assertTrue(ex.getMessage().contains("already in the past"), ex.getMessage());
    }

    @Test
    void aWindowThatEndsNowIsStillAcceptedBecauseTheEndIsInclusive() {
        // Consistent with the entity: the end instant itself is still on sale. Refusing it here while
        // the pricing code honours it would be two different definitions of "ends at".
        assertDoesNotThrow(() -> FlashSalePolicy.validateWindow(null, NOW, NOW));
    }

    @Test
    void aWindowWithNoEndIsAllowedByTheWindowRuleButNotByTheFlashSale() {
        // A discount with no end is legitimate — it is an ordinary permanent markdown, which is what
        // every discount written before V60 is. It is simply not a FLASH sale, so the flash-sale
        // endpoint insists on an end and the plain store-product endpoint does not.
        assertDoesNotThrow(() -> FlashSalePolicy.validateWindow(null, null, NOW));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FlashSalePolicy.requireEnd(null));
        assertTrue(ex.getMessage().contains("End date is required")
                || ex.getMessage().startsWith("An end date is required"), ex.getMessage());
    }

    @Test
    void aWindowIsOnlyOverWhenItsEndInstantHasPASSED() {
        // The same inclusive boundary as the pricing code and as PromoCodeService (a code is expired
        // only once expiresAt is strictly before now). A sale ending exactly now is still running, so
        // the "this window is dead" rule must not treat it as finished and wipe it.
        assertFalse(FlashSalePolicy.windowHasEnded(NOW, NOW), "the end instant itself is still on sale");
        assertFalse(FlashSalePolicy.windowHasEnded(NOW.plusSeconds(1), NOW));
        assertTrue(FlashSalePolicy.windowHasEnded(NOW.minusMillis(1), NOW));
        assertFalse(FlashSalePolicy.windowHasEnded(null, NOW), "no end means it never ends");
    }

    @Test
    void datesWithoutADiscountAreRefusedBecauseNothingWouldEverApplyThem() {
        // effectivePrice never looks at the window unless a type AND a value are set, so a lone window
        // is inert — which is exactly why it must not be storable. It shows on the admin screen as an
        // end date with no sale, survives every later edit, and is the state a re-set discount would
        // then inherit an expiry from.
        Instant end = Instant.parse("2026-03-31T20:00:00Z");

        assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.requireDiscountForWindow(null, null, null, end));
        assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.requireDiscountForWindow(Product.DiscountType.PERCENTAGE, null, null, end));
        assertThrows(IllegalArgumentException.class, () ->
                FlashSalePolicy.requireDiscountForWindow(null, new BigDecimal("25"), end, null));

        // A complete discount with a window, and a discount with no window at all, are both fine.
        assertDoesNotThrow(() -> FlashSalePolicy.requireDiscountForWindow(
                Product.DiscountType.PERCENTAGE, new BigDecimal("25"), null, end));
        assertDoesNotThrow(() -> FlashSalePolicy.requireDiscountForWindow(null, null, null, null));
    }

    // ── Variant-priced products ──────────────────────────────────────────────

    @Test
    void aProductPricedPerVariantIsNoLongerRefused() {
        // validateNoActiveVariants and validateNoTimedDiscount are GONE, and this test is their
        // obituary. They refused a sale on a variant-bearing listing for one reason: its variant lines
        // were charged the raw variant price while the card advertised the parent's discounted one. That
        // is fixed at the source — every cart line is priced from the parent listing this sale writes to,
        // so a variantId picks the SKU and the stock ceiling and never the price. The refusals were
        // blocking legitimate campaigns on a large part of the catalogue for a state that can no longer
        // be reached, and their messages, which stated as fact that "variant prices are not discounted",
        // had become false documentation.
        //
        // Nothing replaces them, because there is nothing left to guard: the only price a discount can
        // move is the listing's, which is the only price any customer surface quotes. PriceAgreementTest
        // pins that equality.
        assertEquals(0, java.util.Arrays.stream(FlashSalePolicy.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("validateNoActiveVariants")
                                || m.getName().equals("validateNoTimedDiscount"))
                        .count(),
                "these refusals were deleted with the pricing fix — do not reintroduce them without "
                        + "reintroducing the mispricing they existed for");
    }

    @Test
    void aFixedDiscountWithNothingToDiscountFromIsRefused() {
        // The one refusal ADDED in exchange. A FIXED value is an absolute SALE PRICE, so with no positive
        // store price to discount FROM the sale price is the only price the listing has, and whoever typed
        // it has not said what it is a sale on: nothing can render a saving, and the flash-sale screen
        // would show a row whose price never moved. A 400 on a half-filled row, not a restriction on a
        // legitimate campaign.
        for (BigDecimal noBase : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-10")}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FlashSalePolicy.validateDiscount(
                            Product.DiscountType.FIXED, new BigDecimal("750"), noBase),
                    "storePrice " + noBase + " gives nothing to discount from");
            assertTrue(ex.getMessage().contains("storePrice"), ex.getMessage());
        }
    }

    @Test
    void aPercentageDiscountNeedsNoStorePriceBecauseNoRateHasToBeDerived() {
        // The refusal above is specific to FIXED and must not spread: a percentage IS the rate, so it
        // applies to whatever the price turns out to be.
        assertDoesNotThrow(() -> FlashSalePolicy.validateDiscount(
                Product.DiscountType.PERCENTAGE, new BigDecimal("25"), null));
    }

    @Test
    void fixedBelowStorePriceIsNowLoadBearingAndNotMerelyASanityCheck() {
        // 0 < discountValue < storePrice is exactly the condition under which the retained fraction
        // discountValue/storePrice lands in (0,1) — i.e. under which a variant gets a real discount that
        // is not a price rise. Weakening this inequality silently breaks variant pricing, so it is pinned
        // here as well as in the price-rise test above.
        assertDoesNotThrow(() -> FlashSalePolicy.validateDiscount(
                Product.DiscountType.FIXED, new BigDecimal("999.99"), new BigDecimal("1000.00")));
        assertThrows(IllegalArgumentException.class, () -> FlashSalePolicy.validateDiscount(
                Product.DiscountType.FIXED, new BigDecimal("1000.00"), new BigDecimal("1000.00")));
    }

    // ── The timezone, converted at the boundary and nowhere else ─────────────

    @Test
    void endsOnIsReadAsTheEndOfThatDayInDubaiNotInUtc() {
        // An admin typing "ends 31 March" means the end of 31 March where the shop is. Read as UTC it
        // would cut the sale four hours early — at 04:00 on 1 April Dubai time, in the middle of the
        // night for everyone involved, and on the wrong calendar day for the customer.
        Instant end = FlashSalePolicy.endOfDayInBusinessZone(LocalDate.of(2026, 3, 31));

        assertEquals(Instant.parse("2026-03-31T20:00:00Z"), end);
        ZonedDateTime inDubai = end.atZone(ZoneId.of("Asia/Dubai"));
        assertEquals(LocalDate.of(2026, 4, 1), inDubai.toLocalDate());
        assertEquals(0, inDubai.getHour());
    }

    @Test
    void startsOnIsTheFirstInstantOfThatDayInDubai() {
        Instant start = FlashSalePolicy.startOfDayInBusinessZone(LocalDate.of(2026, 3, 25));

        assertEquals(Instant.parse("2026-03-24T20:00:00Z"), start);
        assertEquals(LocalDate.of(2026, 3, 25), start.atZone(ZoneId.of("Asia/Dubai")).toLocalDate());
    }

    @Test
    void aSaleGivenAsCalendarDatesCoversEveryHourOfBothDaysInDubai() {
        // The pair, checked end to end: 25–31 March in the dashboard must discount at one minute past
        // midnight on the 25th and at 23:59 on the 31st, Dubai time. This is the assertion that would
        // catch an off-by-one-day in either conversion.
        Instant start = FlashSalePolicy.startOfDayInBusinessZone(LocalDate.of(2026, 3, 25));
        Instant end = FlashSalePolicy.endOfDayInBusinessZone(LocalDate.of(2026, 3, 31));
        ZoneId dubai = ZoneId.of("Asia/Dubai");

        Instant justAfterMidnightOn25th = ZonedDateTime.of(2026, 3, 25, 0, 1, 0, 0, dubai).toInstant();
        Instant lateOn31st = ZonedDateTime.of(2026, 3, 31, 23, 59, 0, 0, dubai).toInstant();
        Instant justBeforeMidnightOn24th = ZonedDateTime.of(2026, 3, 24, 23, 59, 0, 0, dubai).toInstant();

        assertFalse(justBeforeMidnightOn24th.isAfter(start) && justBeforeMidnightOn24th.isBefore(end),
                "the day before the sale must not be inside the window");
        assertTrue(!justAfterMidnightOn25th.isBefore(start) && !end.isBefore(justAfterMidnightOn25th));
        assertTrue(!lateOn31st.isBefore(start) && !end.isBefore(lateOn31st));
    }
}
