package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Delivery pricing, as decided by the owner on 2026-08-12:
 *
 * <ul>
 *   <li>free at or above 100 AED, whatever the method — the existing policy, kept,</li>
 *   <li>below it, 30-minute delivery (EXPRESS, our own couriers) is 20 AED,</li>
 *   <li>below it, standard delivery (REGULAR, dispatched to Quiqup) is billed at Quiqup's rate so
 *       the cost is passed through,</li>
 *   <li>PICKUP and INTERNATIONAL keep the previous flat rate, deliberately untouched.</li>
 * </ul>
 *
 * <p>These are live storefront prices, which is why they are pinned rather than left to a constant
 * someone can quietly edit.
 */
class DeliveryFeePolicyTest {

    private static final String UAE = "UAE";
    private static final String AZERBAIJAN = "AZE";

    /** Quiqup's markets, as configured: UAE only. */
    private final QuiqupCoverage coverage = new QuiqupCoverage("UAE");

    /** The shipped defaults: 100 threshold, 20 express, Quiqup rate not yet supplied (still 15). */
    private final DeliveryFeePolicy policy = new DeliveryFeePolicy(
            new BigDecimal("100.00"), new BigDecimal("20.00"),
            new BigDecimal("15.00"), new BigDecimal("15.00"), coverage);

    /** A configured Quiqup rate, to prove the pass-through actually follows the config. */
    private final DeliveryFeePolicy withQuiqupRate = new DeliveryFeePolicy(
            new BigDecimal("100.00"), new BigDecimal("20.00"),
            new BigDecimal("23.50"), new BigDecimal("15.00"), coverage);

    @Test
    void thirtyMinuteDeliveryCostsTwentyBelowTheThreshold() {
        assertEquals(new BigDecimal("20.00"),
                policy.feeAed(DeliveryMethod.EXPRESS, UAE, new BigDecimal("99.99")));
    }

    @Test
    void standardDeliveryIsBilledAtTheConfiguredQuiqupRate() {
        assertEquals(new BigDecimal("23.50"),
                withQuiqupRate.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("40.00")));
    }

    @Test
    void everyMethodIsFreeAtOrAboveTheThreshold() {
        for (DeliveryMethod method : DeliveryMethod.values()) {
            assertEquals(BigDecimal.ZERO, withQuiqupRate.feeAed(method, UAE, new BigDecimal("100.00")),
                    method + " must be free at exactly the threshold");
            assertEquals(BigDecimal.ZERO, withQuiqupRate.feeAed(method, UAE, new BigDecimal("250.00")),
                    method + " must be free above the threshold");
        }
    }

    @Test
    void pickupAndInternationalKeepTheOldFlatRate() {
        assertEquals(new BigDecimal("15.00"),
                withQuiqupRate.feeAed(DeliveryMethod.PICKUP, UAE, new BigDecimal("50.00")));
        assertEquals(new BigDecimal("15.00"),
                withQuiqupRate.feeAed(DeliveryMethod.INTERNATIONAL, UAE, new BigDecimal("50.00")));
    }

    @Test
    void theCartPreviewShowsTheStandardRateNotTheExpressOne() {
        // The cart has no address yet, so it cannot know the method. It must not advertise the
        // express price, or a customer whose address resolves to REGULAR sees the total move.
        assertEquals(withQuiqupRate.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("40.00")),
                withQuiqupRate.cartPreviewFeeAed(UAE, new BigDecimal("40.00")));
        assertNotEquals(withQuiqupRate.feeAed(DeliveryMethod.EXPRESS, UAE, new BigDecimal("40.00")),
                withQuiqupRate.cartPreviewFeeAed(UAE, new BigDecimal("40.00")));
    }

    @Test
    void anUnknownMethodIsChargedRatherThanGivenFreeDelivery() {
        // Defensive: a caller that has not resolved the method yet must not fall into the free branch.
        assertEquals(new BigDecimal("15.00"), policy.feeAed(null, UAE, new BigDecimal("10.00")));
        assertEquals(BigDecimal.ZERO, policy.feeAed(null, UAE, new BigDecimal("500.00")));
    }

    @Test
    void aNullSubtotalNeverEarnsFreeDelivery() {
        assertFalse(policy.qualifiesForFreeDelivery(null));
        assertEquals(new BigDecimal("20.00"), policy.feeAed(DeliveryMethod.EXPRESS, UAE, null));
    }

    // ── Quiqup is a UAE operation ────────────────────────────────────────────

    @Test
    void standardDeliveryOutsideTheUaeIsNotBilledAtQuiqupsRate() {
        // Quiqup do not carry a Baku delivery, so charging their Dubai rate for it would be wrong.
        assertEquals(new BigDecimal("15.00"),
                withQuiqupRate.feeAed(DeliveryMethod.REGULAR, AZERBAIJAN, new BigDecimal("40.00")));
        assertEquals(new BigDecimal("23.50"),
                withQuiqupRate.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("40.00")),
                "the same order inside the UAE is billed Quiqup's rate");
    }

    @Test
    void theUaeIsRecognisedByBothItsAlphaTwoAndAlphaThreeCodes() {
        // Our own data is alpha-3 ("UAE") while Quiqup echo alpha-2 ("AE"); a plain string compare
        // would answer "not covered" for a UAE order and quietly undercharge it.
        for (String code : new String[]{"UAE", "AE", "ARE", "uae", "ae"}) {
            assertTrue(coverage.servesCountry(code), code + " must be recognised as the UAE");
            assertEquals(new BigDecimal("23.50"),
                    withQuiqupRate.feeAed(DeliveryMethod.REGULAR, code, new BigDecimal("40.00")),
                    "fee must not depend on which form of the UAE code was stored: " + code);
        }
    }

    @Test
    void thirtyMinuteDeliveryIsPricedTheSameEverywhereBecauseItIsNeverQuiqups() {
        assertEquals(withQuiqupRate.feeAed(DeliveryMethod.EXPRESS, UAE, new BigDecimal("40.00")),
                withQuiqupRate.feeAed(DeliveryMethod.EXPRESS, AZERBAIJAN, new BigDecimal("40.00")));
    }

    @Test
    void anUnknownOrMissingCountryFallsBackToTheFlatRate() {
        assertFalse(coverage.servesCountry(null));
        assertFalse(coverage.servesCountry(""));
        assertEquals(new BigDecimal("15.00"),
                withQuiqupRate.feeAed(DeliveryMethod.REGULAR, null, new BigDecimal("40.00")));
    }

    @Test
    void coverageIsConfigurableForWhenQuiqupAddsAMarket() {
        QuiqupCoverage twoMarkets = new QuiqupCoverage("UAE, SAU");
        assertTrue(twoMarkets.servesCountry("SAU"));
        assertTrue(twoMarkets.servesCountry("UAE"));
        assertFalse(twoMarkets.servesCountry("AZE"));
        assertEquals(List.of("UAE", "SAU"), twoMarkets.countries());
    }

    @Test
    void onlyStandardDeliveryIsEverQuiqups() {
        assertTrue(coverage.covers(DeliveryMethod.REGULAR, UAE));
        assertFalse(coverage.covers(DeliveryMethod.EXPRESS, UAE), "30-minute is our own couriers");
        assertFalse(coverage.covers(DeliveryMethod.PICKUP, UAE));
        assertFalse(coverage.covers(DeliveryMethod.INTERNATIONAL, UAE));
    }
}
