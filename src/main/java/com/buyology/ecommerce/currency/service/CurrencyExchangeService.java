package com.buyology.ecommerce.currency.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches live exchange rates from api.frankfurter.app (free, no API key required).
 * Rates are cached per base currency for 1 hour to avoid hammering the external API.
 *
 * Example call: GET https://api.frankfurter.app/latest?from=AED&to=AZN
 * Response: {"amount":1.0,"base":"AED","date":"...","rates":{"AZN":0.46}}
 */
@Service
public class CurrencyExchangeService {

    private static final String FRANKFURTER_BASE_URL = "https://api.frankfurter.app";
    private static final int CACHE_TTL_HOURS = 1;

    private final WebClient webClient;

    // Cache: "AED->AZN" -> (rate, fetchedAt)
    private final Map<String, CachedRate> rateCache = new ConcurrentHashMap<>();

    public CurrencyExchangeService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(FRANKFURTER_BASE_URL).build();
    }

    /**
     * Converts an amount from one currency to another using live exchange rates.
     * Returns the original amount unchanged if currencies are the same.
     *
     * @param amount       the price to convert
     * @param fromCurrency ISO 4217 source currency (e.g. "AED")
     * @param toCurrency   ISO 4217 target currency (e.g. "AZN")
     * @return converted amount rounded to 2 decimal places
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return null;
        if (fromCurrency == null || toCurrency == null) return amount;
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return amount;

        BigDecimal rate = getRate(fromCurrency.toUpperCase(), toCurrency.toUpperCase());
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Resolves the exchange rate from the cache or fetches fresh data.
     */
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

    /**
     * Calls the Frankfurter API to get the current exchange rate.
     * Falls back to 1.0 if the API is unreachable or the currency pair is not found.
     */
    @SuppressWarnings("unchecked")
    private BigDecimal fetchRate(String from, String to) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/latest")
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

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
            // Network error or unsupported currency — fall through to default
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
