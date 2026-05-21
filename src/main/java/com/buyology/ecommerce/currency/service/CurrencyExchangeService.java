package com.buyology.ecommerce.currency.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches live exchange rates from open.er-api.com (free, no API key,
 * 160+ currencies including AED and AZN). Rates are cached per base
 * currency for 1 hour.
 *
 * Example call: GET https://open.er-api.com/v6/latest/AZN
 * Response: {"result":"success","base_code":"AZN","rates":{"AED":2.16,...}}
 *
 * If the live rate is unavailable, conversion throws so that callers
 * do not silently charge the customer in the wrong currency.
 */
@Service
public class CurrencyExchangeService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyExchangeService.class);

    private static final String FX_BASE_URL = "https://open.er-api.com/v6";
    private static final int CACHE_TTL_HOURS = 1;

    private final RestClient restClient;
    private final Map<String, CachedRates> rateCache = new ConcurrentHashMap<>();

    public CurrencyExchangeService() {
        this.restClient = RestClient.builder()
                .baseUrl(FX_BASE_URL)
                .build();
    }

    /**
     * Converts an amount from one currency to another using live exchange rates.
     * Returns the original amount unchanged if currencies are the same or inputs are null.
     * Throws IllegalStateException if a live rate cannot be obtained, to avoid
     * silently charging in the wrong currency.
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return null;
        if (fromCurrency == null || toCurrency == null) return amount;
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return amount;

        BigDecimal rate = getRate(fromCurrency.toUpperCase(), toCurrency.toUpperCase());
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getRate(String from, String to) {
        CachedRates cached = rateCache.get(from);
        if (cached == null || cached.isExpired()) {
            cached = fetchRates(from);
            rateCache.put(from, cached);
        }
        BigDecimal rate = cached.rates.get(to);
        if (rate == null) {
            throw new IllegalStateException(
                    "No FX rate available for " + from + "->" + to);
        }
        return rate;
    }

    @SuppressWarnings("unchecked")
    private CachedRates fetchRates(String from) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/latest/{from}", from)
                    .retrieve()
                    .body(Map.class);

            if (response != null && "success".equals(response.get("result"))) {
                Map<String, Object> rates = (Map<String, Object>) response.get("rates");
                if (rates != null && !rates.isEmpty()) {
                    Map<String, BigDecimal> parsed = new ConcurrentHashMap<>();
                    for (Map.Entry<String, Object> e : rates.entrySet()) {
                        if (e.getValue() instanceof Number) {
                            parsed.put(e.getKey().toUpperCase(), new BigDecimal(e.getValue().toString()));
                        }
                    }
                    return new CachedRates(parsed);
                }
            }
            log.error("[FX] Unexpected response from FX provider for base={}: {}", from, response);
        } catch (Exception e) {
            log.error("[FX] Failed to fetch rates for base={}", from, e);
        }
        throw new IllegalStateException("Unable to fetch live FX rates for base " + from);
    }

    private static class CachedRates {
        final Map<String, BigDecimal> rates;
        final Instant fetchedAt;

        CachedRates(Map<String, BigDecimal> rates) {
            this.rates = rates;
            this.fetchedAt = Instant.now();
        }

        boolean isExpired() {
            return Instant.now().isAfter(fetchedAt.plus(CACHE_TTL_HOURS, ChronoUnit.HOURS));
        }
    }
}
