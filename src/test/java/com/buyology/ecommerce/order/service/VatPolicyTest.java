package com.buyology.ecommerce.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the tax added on top of every order.
 *
 * <p>VAT is charged on a base the customer can see and check, so the assertions here are about
 * exact figures rather than approximate behaviour: a tax that is a fils out on a 3,000 AED laptop
 * is a total that does not add up on the invoice, and a customer who does the arithmetic will find
 * it before we do.
 */
class VatPolicyTest {

    private VatPolicy policy(String ratePercent, String countries) {
        return new VatPolicy(new BigDecimal(ratePercent), countries);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    // ── The rate, applied to the right base ──────────────────────────────────

    @Test
    void fivePercentOfARoundHundred() {
        assertMoney("5.00", policy("5", "").vatOn(new BigDecimal("100.00"), "UAE"));
    }

    @Test
    void theBaseIncludesDelivery() {
        // Delivery is a supply like any other and is taxed with the goods. Charging VAT on the
        // goods alone would under-collect on every order that pays for delivery.
        BigDecimal goodsPlusDelivery = new BigDecimal("200.00").add(new BigDecimal("15.00"));
        assertMoney("10.75", policy("5", "").vatOn(goodsPlusDelivery, "UAE"));
    }

    @Test
    void aHalfFilsRoundsUp() {
        // 5% of 100.10 is 5.005. HALF_UP is the ordinary commercial convention, and the order
        // stores money at two decimals, so the third has to go somewhere deliberate.
        assertMoney("5.01", policy("5", "").vatOn(new BigDecimal("100.10"), "UAE"));
    }

    @Test
    void anAwkwardBaseStillLandsOnTwoDecimals() {
        BigDecimal vat = policy("5", "").vatOn(new BigDecimal("333.33"), "UAE");
        assertMoney("16.67", vat);
        assertTrue(vat.scale() <= 2, "money must not carry a third decimal: " + vat);
    }

    // ── Where it does and does not apply ─────────────────────────────────────

    @Test
    void anEmptyCountryListTaxesEveryMarket() {
        VatPolicy all = policy("5", "");
        assertTrue(all.appliesTo("UAE"));
        assertTrue(all.appliesTo("AZE"));
    }

    @Test
    void aNamedMarketIsMatchedAcrossCodeSpellings() {
        // Our own data is alpha-3 (UAE); plenty of callers carry alpha-2 (AE). A plain string
        // comparison would answer "not taxed" for a UAE order and under-collect on all of them.
        VatPolicy uaeOnly = policy("5", "UAE");
        assertTrue(uaeOnly.appliesTo("UAE"));
        assertTrue(uaeOnly.appliesTo("AE"));
        assertTrue(uaeOnly.appliesTo("ARE"));
    }

    @Test
    void anUntaxedMarketIsChargedNothing() {
        // Billing a customer in another regime for UAE VAT is not a rounding error; it is charging
        // them a tax that does not apply to them.
        VatPolicy uaeOnly = policy("5", "UAE");
        assertFalse(uaeOnly.appliesTo("AZE"));
        assertMoney("0", uaeOnly.vatOn(new BigDecimal("100.00"), "AZE"));
    }

    @Test
    void aZeroRateTurnsItOffEntirely() {
        // The escape hatch if catalogue prices turn out to be VAT-inclusive already: setting the
        // rate to 0 restores exactly the pre-VAT totals.
        VatPolicy off = policy("0", "");
        assertFalse(off.appliesTo("UAE"));
        assertMoney("0", off.vatOn(new BigDecimal("100.00"), "UAE"));
    }

    // ── Degenerate bases ─────────────────────────────────────────────────────

    @Test
    void nothingIsTaxedOnAnEmptyOrFullyDiscountedOrder() {
        // A promo can take the total to zero. Tax follows the money, and there is none.
        VatPolicy vat = policy("5", "");
        assertMoney("0", vat.vatOn(BigDecimal.ZERO, "UAE"));
        assertMoney("0", vat.vatOn(null, "UAE"));
        assertMoney("0", vat.vatOn(new BigDecimal("-10.00"), "UAE"));
    }
}
