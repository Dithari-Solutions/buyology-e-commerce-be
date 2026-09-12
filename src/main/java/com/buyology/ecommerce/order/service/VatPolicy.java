package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.common.utils.CountryCodeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * The one place that decides how much tax an order carries.
 *
 * <p>VAT is charged ON TOP of the prices shown on the catalogue — the displayed price is the
 * net amount, and 5% is added at checkout. That is a choice, not a law of nature, and it is the
 * single most important thing to be sure of before turning this on: if catalogue prices are
 * already VAT-inclusive (which is the norm for UAE retail), adding it here charges the tax twice
 * and every customer is overcharged by 5%.
 *
 * <p>One class, for the same reason {@link DeliveryFeePolicy} is one class. The cart preview, the
 * order pipeline and the amount handed to the gateway must agree to the fils; a tax computed in
 * two places is a total that changes between the basket and the card form.
 *
 * <p><b>What it is charged on.</b> The taxable base is the goods plus delivery, MINUS any discount:
 * a discount reduces what the customer actually pays, and tax follows the money. Computing VAT on
 * the pre-discount figure would tax a sum nobody was charged.
 *
 * <p><b>Rounding</b> is HALF_UP to two decimals — the ordinary commercial convention, and the same
 * scale every other money figure on the order uses.
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

    /** The headline rate as a percentage — 5 means 5%. What the storefront prints on the line. */
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
     * The tax on a taxable base, in the base's own currency.
     *
     * <p>The rate is a percentage of the amount, so no currency conversion happens or is needed —
     * 5% of 100 AED and 5% of 100 AZN are each 5 of their own unit.
     *
     * @param taxableBase goods + delivery − discount; negative or null is treated as zero
     */
    public BigDecimal vatOn(BigDecimal taxableBase, String countryCode) {
        if (taxableBase == null || taxableBase.signum() <= 0 || !appliesTo(countryCode)) {
            return BigDecimal.ZERO;
        }
        return taxableBase
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
