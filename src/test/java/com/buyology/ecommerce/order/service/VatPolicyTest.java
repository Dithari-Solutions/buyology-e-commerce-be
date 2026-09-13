package com.buyology.ecommerce.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how much of an order's total is tax.
 *
 * <p>Catalogue prices INCLUDE VAT, so this is an extraction, not an addition: the figure is a portion
 * of what the customer pays, and adding it to the total would charge them the tax twice. The assertions
 * are about exact figures rather than approximate behaviour, because a tax a fils out on a 3,000 AED
 * laptop is an invoice that does not add up, and a customer who checks the arithmetic will find it
 * before we do.
 */
class VatPolicyTest {

    private VatPolicy policy(String ratePercent, String countries) {
        return new VatPolicy(new BigDecimal(ratePercent), countries);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    // ── Extracted from the gross, not added to a net ─────────────────────────

    @Test
    void fivePercentInclusiveOfARoundHundredAndFive() {
        // 105.00 is 100.00 of goods plus 5.00 of tax. This is the test that separates the two
        // arithmetics: extraction gives 5.00, adding-on-top would give 5.25.
        assertMoney("5.00", policy("5", "").vatIncludedIn(new BigDecimal("105.00"), "UAE"));
    }

    @Test
    void aRoundHundredAlreadyContainsItsTax() {
        // 100.00 inclusive of 5% is 95.24 of goods and 4.76 of tax — NOT 5.00. If this ever reads
        // 5.00 again, somebody has divided by 100 instead of by 105 and every total is 5% high.
        assertMoney("4.76", policy("5", "").vatIncludedIn(new BigDecimal("100.00"), "UAE"));
    }

    @Test
    void theTaxPlusTheNetIsExactlyTheGross() {
        // The property that actually matters on an invoice: the two figures printed must reconstruct
        // the amount charged. Any rounding rule that breaks this is wrong however defensible it looks.
        BigDecimal gross = new BigDecimal("105.00");
        BigDecimal vat = policy("5", "").vatIncludedIn(gross, "UAE");
        assertMoney("105.00", gross.subtract(vat).add(vat));
        assertMoney("100.00", gross.subtract(vat));
    }

    @Test
    void theBaseIncludesDelivery() {
        // Delivery is a supply like any other and carries tax with the goods. Extracting from the goods
        // alone would understate the tax inside every order that paid for delivery.
        BigDecimal goodsPlusDelivery = new BigDecimal("200.00").add(new BigDecimal("25.00"));
        assertMoney("10.71", policy("5", "").vatIncludedIn(goodsPlusDelivery, "UAE"));
    }

    @Test
    void aHalfFilsRoundsUp() {
        // 10.50 inclusive of 5% is 0.5 exactly. HALF_UP is the ordinary commercial convention, and the
        // order stores money at two decimals, so the third has to go somewhere deliberate.
        assertMoney("0.50", policy("5", "").vatIncludedIn(new BigDecimal("10.50"), "UAE"));
    }

    @Test
    void anAwkwardGrossStillLandsOnTwoDecimals() {
        BigDecimal vat = policy("5", "").vatIncludedIn(new BigDecimal("333.33"), "UAE");
        assertMoney("15.87", vat);
        assertTrue(vat.scale() <= 2, "money must not carry a third decimal: " + vat);
    }

    @Test
    void theContainedTaxIsAlwaysLessThanTheAmountItIsInsideOf() {
        // A sanity property rather than a fixture: tax contained in a figure cannot exceed it, and a
        // sign error or an inverted divisor is exactly what this catches.
        VatPolicy vat = policy("5", "");
        for (String gross : new String[]{"0.01", "1.00", "99.99", "1000.00", "312456.78"}) {
            BigDecimal g = new BigDecimal(gross);
            assertTrue(vat.vatIncludedIn(g, "UAE").compareTo(g) < 0,
                    "the tax inside " + gross + " must be less than " + gross);
        }
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
        // comparison would answer "not taxed" for a UAE order and print no VAT line on its invoice.
        VatPolicy uaeOnly = policy("5", "UAE");
        assertTrue(uaeOnly.appliesTo("UAE"));
        assertTrue(uaeOnly.appliesTo("AE"));
        assertTrue(uaeOnly.appliesTo("ARE"));
    }

    @Test
    void anUntaxedMarketReportsNothing() {
        // Naming UAE VAT on an order delivered under another regime is a false statement on an invoice.
        VatPolicy uaeOnly = policy("5", "UAE");
        assertFalse(uaeOnly.appliesTo("AZE"));
        assertMoney("0", uaeOnly.vatIncludedIn(new BigDecimal("100.00"), "AZE"));
    }

    @Test
    void aZeroRateReportsNoTaxWithoutChangingAnyTotal() {
        // Worth being precise about, because the old comment here said a zero rate was "the escape
        // hatch if catalogue prices turn out to be VAT-inclusive already". They are, and the escape
        // hatch is no longer needed: nothing is added to a total now, so a zero rate does not LOWER
        // anything. It only stops the VAT line being shown and stops orders recording a rate.
        VatPolicy off = policy("0", "");
        assertFalse(off.appliesTo("UAE"));
        assertMoney("0", off.vatIncludedIn(new BigDecimal("100.00"), "UAE"));
    }

    // ── Degenerate amounts ───────────────────────────────────────────────────

    @Test
    void nothingIsReportedOnAnEmptyOrFullyDiscountedOrder() {
        // A promo can take the total to zero. There is no money, so there is no tax inside it.
        VatPolicy vat = policy("5", "");
        assertMoney("0", vat.vatIncludedIn(BigDecimal.ZERO, "UAE"));
        assertMoney("0", vat.vatIncludedIn(null, "UAE"));
        assertMoney("0", vat.vatIncludedIn(new BigDecimal("-10.00"), "UAE"));
    }
}
