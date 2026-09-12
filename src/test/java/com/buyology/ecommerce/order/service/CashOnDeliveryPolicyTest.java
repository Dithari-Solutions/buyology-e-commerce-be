package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pins the three limits on the one payment method where the goods leave before the money arrives.
 *
 * <p>Every cash order is an unsecured advance of stock, so the interesting assertions here are the
 * refusals. A policy that says yes on the happy path and forgets a limit does not fail loudly — it
 * quietly sends a 15,000 AED laptop out on trust, and nobody finds out until the courier comes back
 * empty-handed.
 */
class CashOnDeliveryPolicyTest {

    private final CurrencyExchangeService fx = mock(CurrencyExchangeService.class);

    private CashOnDeliveryPolicy policy(boolean enabled, String countries, String maxAed) {
        return new CashOnDeliveryPolicy(enabled, countries, new BigDecimal(maxAed), fx);
    }

    // ── Off unless someone turns it on ───────────────────────────────────────

    @Test
    void itIsOffUntilDeliberatelyEnabled() {
        // The default in application-prod.properties. Deploying the code that supports cash must
        // not be the same act as accepting cash.
        CashOnDeliveryPolicy off = policy(false, "", "0");

        assertFalse(off.isEnabled());
        assertFalse(off.allowsCountry("UAE"));
        assertEquals("Cash on delivery is not available.",
                off.rejectionReason("UAE", new BigDecimal("10.00"), "AED"));
    }

    // ── Only where someone banks the cash ────────────────────────────────────

    @Test
    void anEmptyCountryListMeansEveryMarket() {
        CashOnDeliveryPolicy all = policy(true, "", "0");

        assertTrue(all.allowsCountry("UAE"));
        assertTrue(all.allowsCountry("AZE"));
        assertNull(all.rejectionReason("AZE", new BigDecimal("10000.00"), "AED"));
    }

    @Test
    void aListedMarketIsMatchedAcrossCodeSpellings() {
        // Our own data is alpha-3 (UAE) while plenty of callers carry alpha-2 (AE). A plain string
        // comparison would answer "not available" for a UAE order and quietly kill the feature in
        // the one market it was switched on for.
        CashOnDeliveryPolicy uaeOnly = policy(true, "UAE", "0");

        assertTrue(uaeOnly.allowsCountry("UAE"));
        assertTrue(uaeOnly.allowsCountry("AE"));
        assertTrue(uaeOnly.allowsCountry("ARE"));
    }

    @Test
    void anUnlistedMarketIsRefused() {
        CashOnDeliveryPolicy uaeOnly = policy(true, "UAE", "0");

        assertFalse(uaeOnly.allowsCountry("AZE"));
        assertEquals("Cash on delivery is not available in your country.",
                uaeOnly.rejectionReason("AZE", new BigDecimal("10.00"), "AED"));
    }

    // ── And only up to what we are willing to advance ────────────────────────

    @Test
    void anOrderOverTheCeilingIsRefused() {
        CashOnDeliveryPolicy capped = policy(true, "", "1000");

        String reason = capped.rejectionReason("UAE", new BigDecimal("1000.01"), "AED");
        assertNotNull(reason, "an order above the ceiling must not go out unsecured");
        assertTrue(reason.contains("1000"), "the message must name the limit: " + reason);
    }

    @Test
    void anOrderExactlyAtTheCeilingIsAllowed() {
        // The boundary, explicitly: a cap written as 1000 means 1000 is acceptable.
        assertNull(policy(true, "", "1000").rejectionReason("UAE", new BigDecimal("1000.00"), "AED"));
    }

    @Test
    void aZeroCeilingMeansNoCeiling() {
        assertNull(policy(true, "", "0").rejectionReason("UAE", new BigDecimal("999999.00"), "AED"));
    }

    @Test
    void theCeilingIsJudgedInAedNotTheDisplayCurrency() {
        // The cap is one number for the whole platform, so a total in another currency has to be
        // converted before it is compared. Comparing 5000 AZN against a 1000 AED cap as if they
        // were the same unit would apply a wildly different real limit per market.
        when(fx.convert(any(BigDecimal.class), eq("AZN"), eq("AED")))
                .thenReturn(new BigDecimal("10800.00"));

        assertNotNull(policy(true, "", "1000").rejectionReason("AZE", new BigDecimal("5000.00"), "AZN"),
                "5000 AZN is far above a 1000 AED ceiling once converted");
    }

    @Test
    void anUnavailableExchangeRateDoesNotRefuseTheCustomer() {
        // The ceiling is a risk control, not an entitlement check. A currency service that is
        // momentarily down is not a reason to tell a shopper in an enabled market that they may
        // not pay cash — the switch and the country list decided that, and neither needs a rate.
        when(fx.convert(any(BigDecimal.class), anyString(), anyString()))
                .thenThrow(new IllegalStateException("rates unavailable"));

        assertNull(policy(true, "UAE", "1000").rejectionReason("UAE", new BigDecimal("50.00"), "AZN"));
    }

    @Test
    void theCeilingIsReportedForTheStorefrontToShow() {
        assertEquals(0, new BigDecimal("750").compareTo(policy(true, "", "750").maxOrderTotalAed()));
        assertNull(policy(true, "", "0").maxOrderTotalAed(), "no ceiling is null, not zero");
    }
}
