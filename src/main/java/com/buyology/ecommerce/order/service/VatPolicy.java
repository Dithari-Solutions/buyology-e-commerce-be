package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.common.utils.CountryCodeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * The one place that decides how much of an order's total is tax.
 *
 * <p><b>Catalogue prices INCLUDE VAT.</b> A price of 105.00 is 100.00 of goods and 5.00 of tax, and
 * the customer pays 105.00. Nothing is added at checkout — this class only says how much of a figure
 * somebody is already paying happens to BE tax, so it can be named on the basket and the invoice.
 *
 * <p>It used to work the other way: the displayed price was treated as net and 5% was added on top.
 * That is the normal arrangement in plenty of markets, but it is not this one's — UAE retail quotes
 * VAT-inclusive — so it charged the tax a second time and every customer paid 5% over the price they
 * were shown. Hence {@link #vatIncludedIn} rather than a method that adds: the arithmetic is
 * {@code gross × rate / (100 + rate)}, not {@code net × rate / 100}. On 105.00 that is 5.00, where
 * adding would give 5.25 — the two are not interchangeable, and the difference IS the bug.
 *
 * <p>One class, for the same reason {@link DeliveryFeePolicy} is one class. The cart preview, the
 * order pipeline and the amount handed to the gateway must agree to the fils; a tax computed in
 * two places is a total that changes between the basket and the card form. The storefront used to do
 * this arithmetic itself on the checkout page, in floating point, which is the other half of the same
 * problem — the server sends the figure now.
 *
 * <p><b>What it is contained in.</b> Goods plus delivery, MINUS any discount — the amount actually
 * paid. A discount reduces what changes hands and therefore the tax inside it, and extracting from the
 * pre-discount figure would report tax on a sum nobody was charged.
 *
 * <p><b>Rounding</b> is HALF_UP to two decimals — the ordinary commercial convention, and the same
 * scale every other money figure on the order uses. Per-line extractions will not generally sum to the
 * order-level figure (three lines of 33.33 do not), so the order's own number is the authoritative
 * one; a per-line amount annotates that line rather than being something to add up.
 */
@Component
public class VatPolicy {

    private final BigDecimal ratePercent;
    private final List<String> countries;

    public VatPolicy(
            @Value("${app.vat.rate-percent:5}") BigDecimal ratePercent,
            @Value("${app.vat.countries:}") String countriesCsv) {
        this.ratePercent = ratePercent == null ? BigDecimal.ZERO : ratePercent;
        String csv = countriesCsv == null ? "" : countriesCsv;
        this.countries = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** The headline rate as a percentage — 5 means 5%. What the storefront prints on the "VAT included" line. */
    public BigDecimal ratePercent() {
        return ratePercent;
    }

    /**
     * Whether this market is taxed at all.
     *
     * <p>An empty country list means every market, which is the simple case and the default. Naming
     * markets matters the moment the platform sells somewhere with a different regime: charging UAE
     * VAT on an order delivered to Baku is not a rounding error, it is billing a customer for a tax
     * that does not apply to them.
     */
    public boolean appliesTo(String countryCode) {
        if (ratePercent.signum() <= 0) {
            return false;
        }
        if (countries.isEmpty()) {
            return true;
        }
        return countries.stream().anyMatch(c -> CountryCodeUtil.isSameCountry(c, countryCode));
    }

    /**
     * How much of a VAT-inclusive amount IS the tax, in the amount's own currency.
     *
     * <p>{@code gross × rate / (100 + rate)}. The divisor is the whole point: the gross already
     * contains the tax, so the tax is a fraction of 105, not of 100. Dividing by 100 instead — which is
     * what a method that ADDS tax does — overstates it by the rate again, and a total built from that
     * charges the customer 5% more than the price on the page.
     *
     * <p>No currency conversion happens or is needed: a rate is a proportion, so 5% of 105 AED and 5%
     * of 105 AZN are each 5 of their own unit.
     *
     * <p>There is deliberately no method here that adds VAT to a net amount. Prices are inclusive, so
     * adding is always wrong, and keeping the old adding version around "just in case" is exactly how a
     * caller quietly reintroduces the overcharge.
     *
     * @param grossAmount goods + delivery − discount, VAT included; negative or null is treated as zero
     */
    public BigDecimal vatIncludedIn(BigDecimal grossAmount, String countryCode) {
        if (grossAmount == null || grossAmount.signum() <= 0 || !appliesTo(countryCode)) {
            return BigDecimal.ZERO;
        }
        return grossAmount
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(100).add(ratePercent), 2, RoundingMode.HALF_UP);
    }
}
