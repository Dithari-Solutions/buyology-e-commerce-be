package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.common.utils.CountryCodeUtil;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Which orders may be paid for in cash when they arrive.
 *
 * <p>Cash on delivery is the one payment method where the goods leave before the money arrives, so
 * every order taken on it is an unsecured advance of stock. The three levers below are what keep
 * that exposure bounded, and all three are configuration rather than constants, because they are
 * commercial decisions that change without a release:
 *
 * <ul>
 *   <li><b>{@code app.cod.enabled}</b> — the master switch, and it DEFAULTS TO OFF. Turning it on
 *       is a deliberate act by whoever owns the cash-reconciliation process, not something that
 *       happens by deploying this class.</li>
 *   <li><b>{@code app.cod.countries}</b> — cash only works in markets where someone actually
 *       collects and banks it. Empty means every market, which is only sane once that is true
 *       everywhere.</li>
 *   <li><b>{@code app.cod.max-order-total-aed}</b> — the ceiling on a single unsecured order.
 *       A courier carrying a 15,000 AED laptop to a customer who may not answer the door is a
 *       different proposition from a 200 AED accessory. 0 disables the ceiling.</li>
 * </ul>
 *
 * <p>One class so the cart, the checkout and the order pipeline cannot disagree about whether cash
 * is on offer — the same reason {@link DeliveryFeePolicy} exists. A storefront that offers the
 * option where the order pipeline will refuse it produces a customer who chose cash and is then
 * told no at the last step.
 *
 * <p>The ceiling is expressed in AED, the settlement currency, and the order's total is converted
 * before comparison. A cap written in the customer's display currency would mean a different real
 * limit per market and would move with the exchange rate.
 */
@Component
public class CashOnDeliveryPolicy {

    private static final String BASE_CURRENCY = "AED";

    private final boolean enabled;
    private final List<String> countries;
    private final BigDecimal maxOrderTotalAed;
    private final CurrencyExchangeService currencyExchangeService;

    public CashOnDeliveryPolicy(
            @Value("${app.cod.enabled:false}") boolean enabled,
            @Value("${app.cod.countries:}") String countriesCsv,
            @Value("${app.cod.max-order-total-aed:0}") BigDecimal maxOrderTotalAed,
            CurrencyExchangeService currencyExchangeService) {
        this.enabled = enabled;
        String csv = countriesCsv == null ? "" : countriesCsv;
        this.countries = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.maxOrderTotalAed = maxOrderTotalAed == null ? BigDecimal.ZERO : maxOrderTotalAed;
        this.currencyExchangeService = currencyExchangeService;
    }

    /** Whether cash on delivery is offered at all. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether cash may be offered in this market. An empty country list means every market.
     *
     * <p>Matching goes through {@link CountryCodeUtil} because our own data is alpha-3 ({@code UAE})
     * while plenty of callers carry alpha-2 ({@code AE}); a plain string comparison would silently
     * answer "not offered" for a UAE order.
     */
    public boolean allowsCountry(String countryCode) {
        if (!enabled) {
            return false;
        }
        if (countries.isEmpty()) {
            return true;
        }
        return countries.stream().anyMatch(c -> CountryCodeUtil.isSameCountry(c, countryCode));
    }

    /** The single-order ceiling in AED, or null when there is none. */
    public BigDecimal maxOrderTotalAed() {
        return maxOrderTotalAed.signum() > 0 ? maxOrderTotalAed : null;
    }

    /**
     * Why this order may not be paid in cash, or null when it may.
     *
     * <p>Returns the customer-facing reason rather than a boolean so the caller does not have to
     * re-derive which of the three rules failed, and so the shopper is told what to do instead of
     * being shown a bare refusal.
     */
    public String rejectionReason(String countryCode, BigDecimal orderTotal, String currency) {
        if (!enabled) {
            return "Cash on delivery is not available.";
        }
        if (!allowsCountry(countryCode)) {
            return "Cash on delivery is not available in your country.";
        }
        BigDecimal ceiling = maxOrderTotalAed();
        if (ceiling != null && orderTotal != null) {
            BigDecimal totalAed = toAed(orderTotal, currency);
            if (totalAed != null && totalAed.compareTo(ceiling) > 0) {
                return "Cash on delivery is only available for orders up to "
                        + ceiling.stripTrailingZeros().toPlainString()
                        + " AED. Please pay for this order online.";
            }
        }
        return null;
    }

    /**
     * The order total in AED, or null when it cannot be converted.
     *
     * <p>A failed conversion deliberately does NOT reject the order: the ceiling is a risk control,
     * and a currency service that is momentarily unavailable is not a reason to refuse a shopper a
     * payment method they are entitled to. The master switch and the country list still apply, and
     * both are answered without any conversion.
     */
    private BigDecimal toAed(BigDecimal amount, String currency) {
        if (currency == null || currency.isBlank() || BASE_CURRENCY.equalsIgnoreCase(currency)) {
            return amount;
        }
        try {
            return currencyExchangeService.convert(amount, currency, BASE_CURRENCY);
        } catch (Exception e) {
            return null;
        }
    }
}
