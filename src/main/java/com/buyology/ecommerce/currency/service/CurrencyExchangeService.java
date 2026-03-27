package com.buyology.ecommerce.currency.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches live exchange rates from api.frankfurter.app (free, no API key, ECB data).
 * Rates are cached per currency pair for 1 hour.
 *
 * Example call: GET https://api.frankfurter.app/latest?from=AED&to=AZN
 * Response: {"amount":1.0,"base":"AED","date":"...","rates":{"AZN":0.46}}
 */
@Service
public class CurrencyExchangeService {

    private static final String FRANKFURTER_BASE_URL = "https://api.frankfurter.app";
    private static final int CACHE_TTL_HOURS = 1;

    private final RestClient restClient;
    private final Map<String, CachedRate> rateCache = new ConcurrentHashMap<>();

    public CurrencyExchangeService() {
        this.restClient = RestClient.builder()
                .baseUrl(FRANKFURTER_BASE_URL)
                .build();
    }

    /**
     * Converts an amount from one currency to another using live exchange rates.
     * Returns the original amount unchanged if currencies are the same or inputs are null.
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return null;
        if (fromCurrency == null || toCurrency == null) return amount;
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return amount;

        BigDecimal rate = getRate(fromCurrency.toUpperCase(), toCurrency.toUpperCase());
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getRate(String from, String to) {
        String key = from + "->" + to;
        CachedRate cached = rateCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.rate;
        }
        BigDecimal rate = fetchRate(from, to);
        rateCache.put(key, new CachedRate(rate));
        return rate;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal fetchRate(String from, String to) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/latest?from={from}&to={to}", from, to)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                Map<String, Object> rates = (Map<String, Object>) response.get("rates");
                if (rates != null && rates.containsKey(to)) {
                    Object rateObj = rates.get(to);
                    if (rateObj instanceof Number) {
                        return new BigDecimal(rateObj.toString());
                    }
                }
            }
        } catch (Exception ignored) {
            // Network error or unsupported pair — fall through to 1:1
        }
        return BigDecimal.ONE;
    }

    private static class CachedRate {
        final BigDecimal rate;
        final Instant fetchedAt;

        CachedRate(BigDecimal rate) {
            this.rate = rate;
            this.fetchedAt = Instant.now();
        }

        boolean isExpired() {
            return Instant.now().isAfter(fetchedAt.plus(CACHE_TTL_HOURS, ChronoUnit.HOURS));
        }
    }
}
