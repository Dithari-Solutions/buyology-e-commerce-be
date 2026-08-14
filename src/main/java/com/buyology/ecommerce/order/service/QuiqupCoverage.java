package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.common.utils.CountryCodeUtil;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Which orders Quiqup delivers.
 *
 * <p>One answer, used by both the pricing and (once it exists) the dispatch path, so the fee charged
 * and the carrier actually used can never disagree — the same class of bug that {@link
 * DeliveryFeePolicy} exists to prevent between the cart and the order.
 *
 * <p>Two conditions, both required:
 * <ul>
 *   <li><b>Standard delivery.</b> The 30-minute service (EXPRESS) is our own couriers, never Quiqup.</li>
 *   <li><b>A country Quiqup operates in.</b> They are a UAE company; an order delivering to Baku or
 *       Riyadh is not theirs to carry and must not be billed their Dubai rate.</li>
 * </ul>
 *
 * <p>Only the delivery country is checked, and that is sufficient rather than lax: {@code
 * OrderService.resolveDeliveryMethod} throws unless every item's store is in the same country as the
 * delivery address, so a REGULAR order is always domestic and its pickup store is in the delivery
 * country by construction.
 *
 * <p>Country matching goes through {@link CountryCodeUtil}, which treats {@code AE}, {@code UAE} and
 * {@code ARE} as one country. Our own data is alpha-3 ({@code UAE}) while Quiqup echo alpha-2
 * ({@code AE}), so a plain string comparison would silently answer "not covered" for a UAE order.
 *
 * <p>The country list is configuration, not a constant: when Quiqup add a market, that is a variable
 * change, not a release.
 */
@Component
public class QuiqupCoverage {

    private final List<String> countries;

    public QuiqupCoverage(@Value("${delivery.quiqup-countries:UAE}") String countriesCsv) {
        String csv = countriesCsv == null ? "" : countriesCsv;
        this.countries = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Whether Quiqup operates in this country, tolerant of alpha-2 / alpha-3 forms. */
    public boolean servesCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return false;
        }
        return countries.stream().anyMatch(c -> CountryCodeUtil.isSameCountry(c, countryCode));
    }

    /**
     * Whether Quiqup would carry this delivery.
     *
     * @param method      the resolved delivery method; only REGULAR is Quiqup's
     * @param countryCode the delivery address country, alpha-2 or alpha-3
     */
    public boolean covers(DeliveryMethod method, String countryCode) {
        return method == DeliveryMethod.REGULAR && servesCountry(countryCode);
    }

    /** The configured markets, for logging and the admin config view. */
    public List<String> countries() {
        return countries;
    }
}
