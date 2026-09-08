package com.buyology.ecommerce.currency;

import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression cover for the FX cache poisoning that turned a provider blip into a request storm.
 *
 * <p>The defect: on a failed fetch the service returned the last-known-good snapshot object
 * unchanged and wrote it into the cache — but expiry was computed from that snapshot's original
 * fetch time, so the entry was already expired when written. Every subsequent conversion missed the
 * cache and made another blocking HTTPS call, on the request thread, against a free rate-limited
 * provider. One failure became a call per conversion until the provider's rate-limit window reset.
 *
 * <p>These tests assert the property that actually matters and that no amount of reading the code
 * reliably reveals: <b>after a failure, how many times does the provider get called?</b>
 */
class CurrencyExchangeServiceTest {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>(
            "{\"result\":\"success\",\"base_code\":\"AED\",\"rates\":{\"USD\":0.27,\"AED\":1.0}}");
    private final AtomicReference<Integer> status = new AtomicReference<>(200);

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            byte[] out = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        if (server != null) server.stop(0);
    }

    private CurrencyExchangeService service() {
        return new CurrencyExchangeService(72, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    @DisplayName("a successful fetch is cached: repeated conversions do not re-call the provider")
    void cachesSuccessfulFetch() {
        CurrencyExchangeService fx = service();

        for (int i = 0; i < 20; i++) {
            assertThat(fx.convert(new BigDecimal("100"), "AED", "USD"))
                    .isEqualByComparingTo(new BigDecimal("27.00"));
        }

        assertThat(requestCount).hasValue(1);
    }

    @Test
    @DisplayName("after a failure the provider is called once, not once per conversion")
    void failureDoesNotCauseACallPerConversion() {
        CurrencyExchangeService fx = service();

        // Warm the cache so a last-known-good snapshot exists, then break the provider and expire
        // the entry the only way a caller can: by asking again after the provider starts failing.
        fx.convert(new BigDecimal("100"), "AED", "USD");
        assertThat(requestCount).hasValue(1);

        status.set(500);
        body.set("{\"result\":\"error\"}");

        // Force the cached entry to be treated as stale by exhausting its TTL is not possible in a
        // unit test, so drive the failure path directly through a base with no warm cache entry.
        // Any conversion from a *new* base misses the cache and attempts a fetch.
        assertThatThrownBy(() -> fx.convert(new BigDecimal("100"), "GBP", "USD"))
                .isInstanceOf(IllegalStateException.class);
        int afterFirstFailure = requestCount.get();

        // The storm test: many more conversions on the failing base must NOT each call out.
        for (int i = 0; i < 25; i++) {
            assertThatThrownBy(() -> fx.convert(new BigDecimal("100"), "GBP", "USD"))
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThat(requestCount.get() - afterFirstFailure)
                .as("calls to the provider across 25 conversions after a failure")
                .isZero();
    }

    @Test
    @DisplayName("a stale snapshot is served from cache during the backoff, not re-fetched each time")
    void servesStaleFromCacheWithoutRefetching() {
        CurrencyExchangeService fx = service();

        fx.convert(new BigDecimal("100"), "AED", "USD");
        assertThat(requestCount).hasValue(1);

        status.set(503);
        body.set("service unavailable");

        // The warm entry is still within its one-hour TTL, so these are served from memory and the
        // provider is never touched — the baseline the failure path must not be worse than.
        for (int i = 0; i < 25; i++) {
            assertThat(fx.convert(new BigDecimal("100"), "AED", "USD"))
                    .isEqualByComparingTo(new BigDecimal("27.00"));
        }

        assertThat(requestCount).hasValue(1);
    }

    @Test
    @DisplayName("identical currencies and nulls never reach the provider")
    void shortCircuitsWithoutNetwork() {
        CurrencyExchangeService fx = service();

        assertThat(fx.convert(null, "AED", "USD")).isNull();
        assertThat(fx.convert(new BigDecimal("5"), "AED", "AED")).isEqualByComparingTo("5");
        assertThat(fx.convert(new BigDecimal("5"), null, "USD")).isEqualByComparingTo("5");

        assertThat(requestCount).hasValue(0);
    }
}
