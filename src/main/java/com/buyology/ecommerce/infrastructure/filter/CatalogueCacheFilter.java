package com.buyology.ecommerce.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Short-TTL response cache + real Cache-Control for the public catalogue reads.
 *
 * The storefront's hottest endpoints (/api/product/search and friends) cost 1-2s of pure server
 * time per request and, until this filter, every response left with Spring Security's default
 * {@code no-store} — so neither browsers nor any edge could ever reuse a byte. Catalogue data
 * changes on admin timescales, not per-request: serving a briefly-stale copy is indistinguishable
 * to a shopper and turns repeat loads from seconds into milliseconds.
 *
 * <p>Money is safe because the cart re-prices every line against the live discount window on every
 * read and order placement refuses a checkout whose total has gone UP since — not because the numbers
 * in here are fresh. That claim used to be made unconditionally and was false: until V60 a cart
 * line's price was frozen at add-to-cart and copied onto the order verbatim.
 *
 * <p>A flash sale does not switch this cache off. Bodies quoting a sale ARE stored, with their entry
 * EXPIRED AT THE SALE'S OWN BOUNDARY ({@link #nextPriceChange}) instead of at the flat TTL — so the
 * server can never serve a sale price after the sale finished, nor a pre-sale price after it started,
 * and the hottest read path in the shop keeps its cache during the one week it is under the most load.
 * Refusing to store them (the first attempt) paid the whole cost of the guard for a fraction of its
 * benefit: one discounted product anywhere in a list page disabled caching for that page, and a
 * campaign covering the home rails disabled it for most of the catalogue.
 *
 * <p>The flash-sale rail itself stays excluded below: it is nothing but countdowns, and it is cheap
 * because it only prices what is actually on sale.
 *
 * <p>What is left is a browser's own copy of a card, and it is bounded by THE SAME INSTANT as the
 * server entry: any response whose body quotes a sale boundary leaves with {@code max-age} cut to that
 * boundary and with no {@code stale-while-revalidate} at all. Bounding it by the entry's remaining life
 * instead was not enough — an entry holding a sale that ends in two hours is bounded by the TTL, so it
 * looked ordinary, went out with the flat 60s plus a five-minute stale window, and let the browser
 * re-show a sale price for minutes after the sale had ended while the server never would.
 *
 * Two layers, both keyed by the full URI + query string (which carries lang/country/currency,
 * so market variants never mix):
 * <ul>
 *   <li>an in-memory micro-cache of the JSON bytes (TTL {@link #TTL_MILLIS}) absorbing the
 *       recompute cost;</li>
 *   <li>{@code Cache-Control: public, max-age=60, stale-while-revalidate=300} on the way out,
 *       which Spring Security's CacheControlHeadersWriter respects (it only writes its
 *       no-store trio when no Cache-Control is present), letting browsers and CDNs cache.
 *       Cache hits carry an {@code Age} header so downstream caches don't restart the clock.</li>
 * </ul>
 *
 * Memory is hard-bounded, because the cache key is attacker-influenced (any junk query param
 * mints a new key on a permitAll endpoint): bodies are tee-copied while STREAMING through (never
 * double-buffered), the copy is abandoned past {@link #MAX_BODY_BYTES}, and the whole cache is
 * capped by {@link #MAX_TOTAL_BYTES} as well as {@link #MAX_ENTRIES}, evicting expired-then-oldest.
 *
 * Only idempotent GETs on the public, user-independent catalogue paths are cached — the B2B
 * catalogue is excluded (matched on the DECODED path, so percent-encoding can't sneak past),
 * GPS-keyed requests (lat/lng query params, unique per caller) are passed through untouched,
 * and responses are only stored on synchronous HTTP 200 JSON.
 */
@Component
public class CatalogueCacheFilter extends OncePerRequestFilter {

    private static final long TTL_MILLIS = 60_000;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_BODY_BYTES = 768_000;
    private static final long MAX_TOTAL_BYTES = 48_000_000;
    private static final String CACHE_CONTROL_VALUE = "public, max-age=60, stale-while-revalidate=300";
    /**
     * The JSON fields that make a body time-sensitive — see {@link #nextPriceChange(byte[])}.
     *
     * <p>Both ENDS of a sale, because a body can be wrong in both directions. A body serialised while
     * the sale runs stops being true when it ends; a body serialised BEFORE a scheduled sale starts
     * stops being true when it starts, and carries no end date at all to be bounded by.
     */
    private static final String[] PRICE_CHANGE_MARKERS = {"\"flashSaleEndsAt\"", "\"flashSaleStartsAt\""};

    /**
     * @param expiresAt when this entry stops being servable: {@code storedAt + TTL}, or the next moment
     *                  a price in the body changes if that comes first.
     * @param priceChangesAt the next moment a price in this body changes ({@link Long#MAX_VALUE} when
     *                  it quotes no sale boundary at all). Kept SEPARATELY from {@code expiresAt},
     *                  which is also bounded by the TTL: the TTL is only how often the server
     *                  recomputes, while this is the instant the bytes stop being true — and it is
     *                  therefore the only honest bound on a BROWSER's copy, which the TTL knows
     *                  nothing about.
     */
    private record Entry(byte[] body, String contentType, long storedAt, long expiresAt,
                         long priceChangesAt) {}

    private final Object[] loadLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicLong totalBytes = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) return true;
        String uri;
        try {
            // Spring routes on the decoded path; matching the raw URI would let %62%32%62
            // ("b2b") slip past the exclusion below.
            uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return true;
        }
        if (uri.startsWith("/api/product/b2b")) return true;      // B2B pricing is its own world
        if (uri.startsWith("/api/product/quick-delivery")) return true; // GPS-keyed, never re-hit
        // The flash-sale rail carries countdowns. Cached for a minute, a countdown read from it starts
        // up to a minute late and the rail keeps listing a sale that has ended; and the endpoint is
        // cheap, since it prices only the items actually on sale.
        if (uri.startsWith("/api/product/flash-sale")) return true;
        String query = request.getQueryString();
        if (query != null && (query.contains("lat=") || query.contains("lng="))) return true;
        return !(uri.startsWith("/api/product") || uri.startsWith("/api/category") || uri.equals("/api/brand"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = cacheKey(request);
        // Fixed-size locks coalesce simultaneous cold requests without an unbounded key/lock map.
        synchronized (loadLocks[Math.floorMod(key.hashCode(), loadLocks.length)]) {
            serveCached(request, response, filterChain, key);
        }
    }

    private void serveCached(HttpServletRequest request, HttpServletResponse response,
                             FilterChain filterChain, String key) throws ServletException, IOException {
        long now = System.currentTimeMillis();

        Entry hit = cache.get(key);
        if (hit != null) {
            if (now < hit.expiresAt()) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(hit.contentType());
                response.setContentLength(hit.body().length);
                response.setHeader("Cache-Control", cacheControlFor(hit, now));
                response.setIntHeader("Age", (int) ((now - hit.storedAt()) / 1000));
                response.setHeader("X-Catalogue-Cache", "HIT");
                response.getOutputStream().write(hit.body());
                return;
            }
            // Expired: reclaim on the read path too, not only under write pressure.
            if (cache.remove(key, hit)) totalBytes.addAndGet(-hit.body().length);
        }

        TeeResponse tee = new TeeResponse(response, MAX_BODY_BYTES);
        filterChain.doFilter(request, tee);
        tee.flushWriter();

        byte[] body = tee.copiedBody();
        String contentType = tee.getContentType();
        if (!request.isAsyncStarted()
                && tee.getStatus() == HttpServletResponse.SC_OK
                && body != null && body.length > 0
                && contentType != null
                && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            long priceChangesAt = nextPriceChange(body);
            long expiresAt = Math.min(now + TTL_MILLIS, priceChangesAt);
            // A sale that ends within this request is not worth a cache entry; it would only be
            // served to nobody and then swept.
            if (expiresAt > now) {
                store(key, new Entry(body, contentType, now, expiresAt, priceChangesAt));
                // The header was stamped at the first body byte, before the body existed to be
                // inspected. Best effort: if nothing has been flushed to the client yet — which is
                // every catalogue response that fits Tomcat's buffer, i.e. nearly all of them — cut
                // the browser's licence down to the price change too. Past commit this is a silent
                // no-op and the flat directive stands.
                //
                // Bounded by the PRICE CHANGE, not by this entry's expiry, and that is the whole fix:
                // the old test was `expiresAt < now + TTL`, so a sale ending 70 seconds from now — just
                // outside the TTL — left the flat directive in place and handed the browser
                // max-age=60 plus stale-while-revalidate=300. That licenses it to re-show the sale
                // price past the end and then serve it stale for five minutes more. The card said 750
                // while the basket stamped 1000, with no 409 to catch it because the line was never
                // stamped at 750 — the same inversion this cache exists to avoid, relocated into the
                // browser.
                if (!response.isCommitted() && priceChangesAt < Long.MAX_VALUE) {
                    response.setHeader("Cache-Control",
                            cacheControl(Math.min(TTL_MILLIS, priceChangesAt - now)));
                }
            }
        }
    }

    /**
     * The soonest moment a price in this body changes — the nearest {@code flashSaleEndsAt} or
     * {@code flashSaleStartsAt} it quotes — or {@link Long#MAX_VALUE} when it quotes neither.
     *
     * <p>This is what lets a sale body be cached safely. There is no eviction hook — nothing in
     * StoreProductService can reach in here when a sale is created or ended — so the only honest
     * bound on an entry quoting a sale is the sale's own boundary, read back out of the bytes we just
     * served.
     *
     * <p>BOTH boundaries, because a body can be stale in both directions. The end was obvious: a
     * stored sale price outliving its sale. The start is the same bug walking backwards and was missed
     * — a body serialised a minute before a sale begins quotes the PRE-sale price and carries no
     * {@code flashSaleEndsAt} at all, so it used to get the full TTL and the flat directive. For up to
     * about six minutes after the sale began, cards showed the old price while the cart charged the
     * new one. The direction favours the customer, but the sale visibly fails to start on the busiest
     * surface in the shop and the card disagrees with the basket, which is the thing this whole
     * changeset exists to prevent.
     *
     * <p>A substring scan rather than parsing the JSON: it runs once per MISS on a body already
     * fully buffered, the fields are emitted only by the serializer (so they cannot appear in product
     * text), and an unparseable value is treated as "expires immediately" rather than as absent —
     * failing towards a shorter cache life is the only safe direction here.
     */
    static long nextPriceChange(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        long soonest = Long.MAX_VALUE;
        for (String marker : PRICE_CHANGE_MARKERS) {
            soonest = Math.min(soonest, nearest(json, marker));
        }
        return soonest;
    }

    /** The nearest instant one marker carries, or {@link Long#MAX_VALUE} when the body has none. */
    private static long nearest(String json, String fieldMarker) {
        long earliest = Long.MAX_VALUE;
        int from = 0;
        while (true) {
            int marker = json.indexOf(fieldMarker, from);
            if (marker < 0) return earliest;
            int cursor = marker + fieldMarker.length();
            while (cursor < json.length() && (json.charAt(cursor) == ':' || json.charAt(cursor) == ' ')) {
                cursor++;
            }
            // An ISO instant, which is what this API serializes
            // (ObjectMapperConfig disables WRITE_DATES_AS_TIMESTAMPS). Anything else — a numeric
            // epoch, a null, a truncated body — is treated as "expires now" rather than as absent, so
            // a change in date serialization cannot quietly turn this bound off.
            if (cursor >= json.length() || json.charAt(cursor) != '"') return 0;
            int valueEnd = json.indexOf('"', cursor + 1);
            if (valueEnd < 0) return 0;
            try {
                earliest = Math.min(earliest,
                        Instant.parse(json.substring(cursor + 1, valueEnd)).toEpochMilli());
            } catch (DateTimeParseException e) {
                return 0;
            }
            from = valueEnd + 1;
        }
    }

    /**
     * The header for a cache HIT, bounded by the same instant the entry is: the next price change,
     * never past it, and no stale window when there is one.
     *
     * <p>The previous form asked whether the ENTRY's life had been shortened, which is not the same
     * question. An entry storing a sale that ends in two hours is bounded by the TTL, not by the sale,
     * so it looked ordinary and was served with the flat {@code max-age=60,
     * stale-while-revalidate=300} — including on the hit that lands ten seconds before the sale ends.
     * The server never serves a stale price and the browser then does, for up to six minutes.
     */
    private static String cacheControlFor(Entry entry, long now) {
        return entry.priceChangesAt() == Long.MAX_VALUE
                ? CACHE_CONTROL_VALUE
                : cacheControl(Math.min(TTL_MILLIS, entry.priceChangesAt() - now));
    }

    /**
     * A sale-bounded header: max-age only as far as the prices in this body hold, and NO
     * {@code stale-while-revalidate} — the whole point is that this copy stops being showable when
     * those prices change, and a stale-serving window would hand it another five minutes.
     */
    private static String cacheControl(long remainingMillis) {
        return "public, max-age=" + Math.max(1, remainingMillis / 1000);
    }

    private static String cacheKey(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    /** Bounded put: expired entries first, then oldest, until both entry and byte budgets fit. */
    private void store(String key, Entry entry) {
        long now = entry.storedAt();
        if (cache.size() >= MAX_ENTRIES || totalBytes.get() + entry.body().length > MAX_TOTAL_BYTES) {
            cache.entrySet().removeIf(e -> {
                if (e.getValue().expiresAt() <= now) {
                    totalBytes.addAndGet(-e.getValue().body().length);
                    return true;
                }
                return false;
            });
        }
        while (cache.size() >= MAX_ENTRIES || totalBytes.get() + entry.body().length > MAX_TOTAL_BYTES) {
            var oldest = cache.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().storedAt()))
                    .orElse(null);
            if (oldest == null) break;
            if (cache.remove(oldest.getKey(), oldest.getValue())) {
                totalBytes.addAndGet(-oldest.getValue().body().length);
            }
        }
        Entry previous = cache.put(key, entry);
        totalBytes.addAndGet(entry.body().length - (previous == null ? 0 : previous.body().length));
    }

    /**
     * Streams the response through untouched while copying the first {@code limit} bytes.
     * Past the limit the copy is abandoned (the response itself is unaffected) — so heap cost
     * is min(body, limit) and huge responses keep streaming exactly as before this filter.
     */
    private static final class TeeResponse extends HttpServletResponseWrapper {
        private final int limit;
        private ByteArrayOutputStream copy = new ByteArrayOutputStream(16 * 1024);
        private ServletOutputStream stream;
        private PrintWriter writer;

        TeeResponse(HttpServletResponse response, int limit) {
            super(response);
            this.limit = limit;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (stream == null) {
                ServletOutputStream target = super.getOutputStream();
                stream = new ServletOutputStream() {
                    @Override public boolean isReady() { return target.isReady(); }
                    @Override public void setWriteListener(WriteListener listener) { target.setWriteListener(listener); }
                    @Override public void write(int b) throws IOException {
                        decideCacheHeader();
                        target.write(b);
                        tee(new byte[]{(byte) b}, 0, 1);
                    }
                    @Override public void write(byte[] b, int off, int len) throws IOException {
                        decideCacheHeader();
                        target.write(b, off, len);
                        tee(b, off, len);
                    }
                    @Override public void flush() throws IOException { target.flush(); }
                    @Override public void close() throws IOException { target.close(); }
                };
            }
            return stream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                String enc = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
                writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), enc));
            }
            return writer;
        }

        /**
         * Runs once, just before the first body byte reaches the wire: status and content type
         * are final by then but nothing is committed yet, so this is the only safe moment to
         * stamp the cacheable header (post-chain would be after commit, a silent no-op).
         * Non-200/non-JSON responses keep Spring Security's default no-store.
         */
        private boolean headerDecided;

        private void decideCacheHeader() {
            if (headerDecided) return;
            headerDecided = true;
            String ct = getContentType();
            if (getStatus() == HttpServletResponse.SC_OK && ct != null
                    && ct.contains(MediaType.APPLICATION_JSON_VALUE)) {
                setHeader("Cache-Control", CACHE_CONTROL_VALUE);
            }
        }

        private void tee(byte[] b, int off, int len) {
            if (copy == null) return;
            if (copy.size() + len > limit) {
                copy = null; // over budget: this response won't be cached
                return;
            }
            copy.write(b, off, len);
        }

        void flushWriter() {
            if (writer != null) writer.flush();
        }

        byte[] copiedBody() {
            return copy == null ? null : copy.toByteArray();
        }
    }
}
