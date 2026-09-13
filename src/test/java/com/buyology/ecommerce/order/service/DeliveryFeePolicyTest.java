package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Delivery pricing:
 *
 * <ul>
 *   <li>free at or above 100 AED, whatever the method — the long-standing policy, kept,</li>
 *   <li>store pickup is free, because collecting it yourself is not a delivery,</li>
 *   <li>otherwise a flat 25 AED, the same for every method and every market.</li>
 * </ul>
 *
 * <p>These are live storefront prices, which is why they are pinned rather than left to a constant
 * someone can quietly edit.
 *
 * <p>It used to be tiered — 20 for the 30-minute service, a pass-through Quiqup rate for standard
 * deliveries inside their markets, a separate flat rate everywhere else — and each of those
 * distinctions was somewhere the cart's quote and the order's charge could diverge. The tests that
 * pinned those tiers are gone with them; what survives is the part that must not break, which is that
 * the free-delivery threshold still comes first.
 */
class DeliveryFeePolicyTest {

    private static final String UAE = "UAE";
    private static final String AZERBAIJAN = "AZE";

    /** Quiqup's markets, as configured: UAE only. No longer a pricing input — see below. */
    private final QuiqupCoverage coverage = new QuiqupCoverage("UAE");

    /** The shipped defaults: free at 100, otherwise 25. */
    private final DeliveryFeePolicy policy = new DeliveryFeePolicy(
            new BigDecimal("100.00"), new BigDecimal("25.00"), coverage);

    // ── The flat rate ────────────────────────────────────────────────────────

    @Test
    void everyDeliveryMethodCostsTwentyFiveBelowTheThreshold() {
        for (DeliveryMethod method : DeliveryMethod.values()) {
            if (method == DeliveryMethod.PICKUP) {
                continue;   // its own case below
            }
            assertEquals(new BigDecimal("25.00"),
                    policy.feeAed(method, UAE, new BigDecimal("99.99")),
                    method + " must be charged the flat rate");
        }
    }

    @Test
    void theRateDoesNotDependOnTheMarket() {
        // The whole point of one number: a fee that varies by country is a fee the cart can quote
        // before it knows the delivery address and then get wrong.
        assertEquals(policy.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("40.00")),
                policy.feeAed(DeliveryMethod.REGULAR, AZERBAIJAN, new BigDecimal("40.00")));
        assertEquals(new BigDecimal("25.00"),
                policy.feeAed(DeliveryMethod.REGULAR, null, new BigDecimal("40.00")),
                "an unknown country is still charged, not given free delivery");
    }

    @Test
    void theRateFollowsItsConfiguration() {
        DeliveryFeePolicy dearer = new DeliveryFeePolicy(
                new BigDecimal("100.00"), new BigDecimal("30.00"), coverage);
        assertEquals(new BigDecimal("30.00"),
                dearer.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("40.00")));
    }

    // ── Free delivery, which this change must not disturb ────────────────────

    @Test
    void everyMethodIsFreeAtOrAboveTheThreshold() {
        // The regression that matters. A flat fee returned before the threshold check would start
        // charging 25 AED on every order over 100 and silently delete free delivery.
        for (DeliveryMethod method : DeliveryMethod.values()) {
            assertEquals(BigDecimal.ZERO, policy.feeAed(method, UAE, new BigDecimal("100.00")),
                    method + " must be free at exactly the threshold");
            assertEquals(BigDecimal.ZERO, policy.feeAed(method, UAE, new BigDecimal("250.00")),
                    method + " must be free above the threshold");
        }
    }

    @Test
    void theThresholdIsInclusiveAndOneFilsBelowItIsNot() {
        assertTrue(policy.qualifiesForFreeDelivery(new BigDecimal("100.00")));
        assertFalse(policy.qualifiesForFreeDelivery(new BigDecimal("99.99")));
        assertEquals(new BigDecimal("25.00"),
                policy.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("99.99")));
    }

    @Test
    void theThresholdIsStillReportableForTheSpendMoreNudge() {
        assertEquals(new BigDecimal("100.00"), policy.freeShippingThresholdAed());
    }

    // ── Pickup ───────────────────────────────────────────────────────────────

    @Test
    void collectingItYourselfIsFree() {
        // OrderService.resolveFulfilment hardcodes zero for pickup and never calls this method, so
        // nothing in production depends on the answer today — but when two places decide the same
        // thing they should agree, or the next caller gets a surprise.
        assertEquals(BigDecimal.ZERO, policy.feeAed(DeliveryMethod.PICKUP, UAE, new BigDecimal("10.00")));
        assertEquals(BigDecimal.ZERO, policy.feeAed(DeliveryMethod.PICKUP, AZERBAIJAN, BigDecimal.ONE));
    }

    // ── Degenerate inputs ────────────────────────────────────────────────────

    @Test
    void anUnresolvedMethodIsChargedRatherThanGivenFreeDelivery() {
        assertEquals(new BigDecimal("25.00"), policy.feeAed(null, UAE, new BigDecimal("10.00")));
        assertEquals(BigDecimal.ZERO, policy.feeAed(null, UAE, new BigDecimal("500.00")));
    }

    @Test
    void aNullSubtotalNeverEarnsFreeDelivery() {
        // "We could not work out the subtotal" must not resolve to "so it ships free".
        assertFalse(policy.qualifiesForFreeDelivery(null));
        assertEquals(new BigDecimal("25.00"), policy.feeAed(DeliveryMethod.REGULAR, UAE, null));
    }

    // ── The cart preview and the express quote now agree with everything else ─

    @Test
    void theCartPreviewQuotesWhatTheOrderWillCharge() {
        // With one rate this is trivially true, which is the improvement: while pricing was tiered the
        // cart had to guess a method it could not know yet, and a customer whose address resolved
        // differently watched the total move between the basket and the card form.
        assertEquals(policy.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("40.00")),
                policy.cartPreviewFeeAed(UAE, new BigDecimal("40.00")));
        assertEquals(policy.feeAed(DeliveryMethod.EXPRESS, UAE, new BigDecimal("40.00")),
                policy.cartPreviewFeeAed(UAE, new BigDecimal("40.00")));
    }

    @Test
    void theExpressQuoteIsTheSameFlatRateAndStillRespectsFreeDelivery() {
        // 30-minute delivery is switched off (delivery.express-enabled), but these accessors are still
        // read by response fields that clients in the field consume, so they must answer honestly.
        assertEquals(new BigDecimal("25.00"), policy.expressFeeAed());
        assertEquals(new BigDecimal("25.00"), policy.expressFeeAedForSubtotal(new BigDecimal("40.00")));
        assertEquals(BigDecimal.ZERO, policy.expressFeeAedForSubtotal(new BigDecimal("100.00")));
    }

    // ── Quiqup coverage is no longer a price input, but is still the dispatch gate ──

    @Test
    void coverageNoLongerChangesThePrice() {
        // It used to select between two rates. It is kept as a bean because QuiqupDispatchService gates
        // on it to decide whether an order may be handed to Quiqup at all — so it is load-bearing
        // elsewhere, and deleting it would break dispatch rather than pricing.
        assertEquals(policy.feeAed(DeliveryMethod.REGULAR, UAE, new BigDecimal("40.00")),
                policy.feeAed(DeliveryMethod.REGULAR, AZERBAIJAN, new BigDecimal("40.00")));
    }

    @Test
    void theUaeIsRecognisedByBothItsAlphaTwoAndAlphaThreeCodes() {
        // Our own data is alpha-3 ("UAE") while Quiqup echo alpha-2 ("AE"); a plain string compare
        // would answer "not covered" and stop dispatching real UAE orders.
        for (String code : new String[]{"UAE", "AE", "ARE", "uae", "ae"}) {
            assertTrue(coverage.servesCountry(code), code + " must be recognised as the UAE");
        }
    }

    @Test
    void anUnknownOrMissingCountryIsNotCovered() {
        assertFalse(coverage.servesCountry(null));
        assertFalse(coverage.servesCountry(""));
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
