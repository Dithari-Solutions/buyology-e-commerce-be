package com.buyology.ecommerce.currency.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
 * If a fresh fetch fails, the service falls back to the last successfully
 * fetched ("last known good") rates for that base, as long as they are not
 * older than {@code currency.max-stale-hours}. Only when there is no usable
 * snapshot at all does conversion throw, so that callers do not silently
 * charge the customer in the wrong currency.
 */
@Service
public class CurrencyExchangeService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyExchangeService.class);

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /**
     * How long a failed fetch suppresses the next attempt.
     *
     * <p>Without this the service degrades catastrophically rather than gracefully: a failure used
     * to write an already-expired entry back into the cache, so the very next conversion missed and
     * called out again. One provider blip turned into a blocking HTTP call PER CONVERSION — on the
     * request thread, against a free keyless per-IP-rate-limited endpoint, from both hosts at once.
     * That is a request storm that keeps itself rate-limited, and it recovers only when the
     * provider's window happens to reset.
     */
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final Map<String, CachedRates> rateCache = new ConcurrentHashMap<>();
    /** Last successfully fetched rates per base currency, kept beyond the cache TTL. */
    private final Map<String, CachedRates> lastKnownGood = new ConcurrentHashMap<>();
    /** One refresh at a time per base, so a cache miss under load is one call and not N. */
    private final Map<String, Object> refreshLocks = new ConcurrentHashMap<>();
    /** Earliest moment we may call the provider again for a base, after a failure. */
    private final Map<String, Instant> retryNotBefore = new ConcurrentHashMap<>();

    /** Maximum age (hours) of last-known-good rates that may be served when the provider is down. */
    private final long maxStaleHours;

    public CurrencyExchangeService(
            @Value("${currency.max-stale-hours:72}") long maxStaleHours,
            // Injectable so the failure behaviour can be tested against a local stub. It was
            // hardcoded, which meant the cache-poisoning bug below could not be covered by a test
            // and re-armed itself hourly in production unnoticed.
            @Value("${currency.api-base-url:https://open.er-api.com/v6}") String baseUrl) {
        this.maxStaleHours = maxStaleHours;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    /**
     * Converts an amount from one currency to another using live exchange rates.
     * Returns the original amount unchanged if currencies are the same or inputs are null.
     * Throws IllegalStateException only if no live rate can be obtained and no
     * sufficiently fresh last-known-good rate exists, to avoid silently charging
     * in the wrong currency.
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
            cached = refresh(from);
        }
        BigDecimal rate = cached.rates.get(to);
        if (rate == null) {
            throw new IllegalStateException(
                    "No FX rate available for " + from + "->" + to);
        }
        return rate;
    }

    /**
     * Produces a usable snapshot for {@code base}, calling the provider at most once at a time and
     * at most once per {@link #FAILURE_BACKOFF} after a failure.
     *
     * <p>Two guarantees matter here, and the absence of either is what turned a provider blip into
     * a site-wide outage. First, whatever this returns is CACHED with a real expiry, so the next
     * conversion is served from memory instead of repeating the call. Second, only one thread per
     * base is ever in the provider call; the rest wait for its result rather than adding to the
     * load that is already failing.
     */
    private CachedRates refresh(String base) {
        Object lock = refreshLocks.computeIfAbsent(base, k -> new Object());
        synchronized (lock) {
            // Another thread may have refreshed while we waited for the monitor.
            CachedRates current = rateCache.get(base);
            if (current != null && !current.isExpired()) return current;

            Instant blockedUntil = retryNotBefore.get(base);
            if (blockedUntil != null && Instant.now().isBefore(blockedUntil)) {
                // The provider failed moments ago. Serve what we have, or fail — but do not make
                // the same call again on this request's thread.
                CachedRates fallback = fallbackToLastKnownGood(base);
                rateCache.put(base, fallback);
                return fallback;
            }

            CachedRates next = fetchRates(base);
            rateCache.put(base, next);
            return next;
        }
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
                    CachedRates fresh = new CachedRates(parsed);
                    lastKnownGood.put(from, fresh);
                    retryNotBefore.remove(from);
                    return fresh;
                }
            }
            log.error("[FX] Unexpected response from FX provider for base={}: {}", from, response);
        } catch (Exception e) {
            log.error("[FX] Failed to fetch rates for base={}", from, e);
        }
        // Suppress further attempts for this base until the backoff elapses. Without this the
        // fallback below is served but never trusted, and every later conversion calls out again.
        retryNotBefore.put(from, Instant.now().plus(FAILURE_BACKOFF));
        return fallbackToLastKnownGood(from);
    }

    /**
     * Serves the last-known-good snapshot when a fresh fetch fails, provided it is
     * not older than {@link #maxStaleHours}. Throws if no usable snapshot exists.
     */
    private CachedRates fallbackToLastKnownGood(String from) {
        CachedRates snapshot = lastKnownGood.get(from);
        if (snapshot == null) {
            throw new IllegalStateException(
                    "Unable to fetch live FX rates for base " + from
                            + " and no last-known-good rates are available (cold start with provider down).");
        }
        long ageHours = ChronoUnit.HOURS.between(snapshot.fetchedAt, Instant.now());
        if (ageHours > maxStaleHours) {
            throw new IllegalStateException(
                    "Unable to fetch live FX rates for base " + from
                            + " and last-known-good rates are too stale (age=" + ageHours
                            + "h, max=" + maxStaleHours + "h).");
        }
        log.warn("[FX] Serving STALE last-known-good rates for base={} (age={}h, max={}h); FX provider unreachable.",
                from, ageHours, maxStaleHours);
        // A COPY stamped as cached now — the original is returned unchanged to lastKnownGood so the
        // staleness check above keeps measuring true age. Returning the original here was the bug:
        // its expiry is computed from fetchedAt, so the entry written to the cache was already
        // expired and the next conversion called the provider again.
        return snapshot.reCachedNow(FAILURE_BACKOFF);
    }

    /**
     * A rate snapshot, carrying two different timestamps that used to be one.
     *
     * <p>{@code fetchedAt} is when the provider actually gave us these numbers, and it is what
     * staleness is judged on — a snapshot does not become fresher by being re-cached.
     * {@code cachedAt} plus {@code ttl} is how long this particular cache entry is honoured.
     * Conflating them meant a re-cached stale snapshot was expired on arrival.
     */
    private static class CachedRates {
        final Map<String, BigDecimal> rates;
        final Instant fetchedAt;
        final Instant cachedAt;
        final Duration ttl;

        CachedRates(Map<String, BigDecimal> rates) {
            this(rates, Instant.now(), Instant.now(), CACHE_TTL);
        }

        private CachedRates(Map<String, BigDecimal> rates, Instant fetchedAt,
                            Instant cachedAt, Duration ttl) {
            this.rates = rates;
            this.fetchedAt = fetchedAt;
            this.cachedAt = cachedAt;
            this.ttl = ttl;
        }

        /** The same rates, honoured for {@code ttl} from now, still dated by the original fetch. */
        CachedRates reCachedNow(Duration ttl) {
            return new CachedRates(rates, fetchedAt, Instant.now(), ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(ttl));
        }
    }
}
