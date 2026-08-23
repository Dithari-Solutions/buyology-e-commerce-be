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
 * to a shopper and turns repeat loads from seconds into milliseconds. (Money is safe regardless —
 * order placement re-prices everything server-side.)
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

    private record Entry(byte[] body, String contentType, long storedAt) {}

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
        String query = request.getQueryString();
        if (query != null && (query.contains("lat=") || query.contains("lng="))) return true;
        return !(uri.startsWith("/api/product") || uri.startsWith("/api/category") || uri.equals("/api/brand"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = cacheKey(request);
        long now = System.currentTimeMillis();

        Entry hit = cache.get(key);
        if (hit != null) {
            long age = now - hit.storedAt();
            if (age <= TTL_MILLIS) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(hit.contentType());
                response.setContentLength(hit.body().length);
                response.setHeader("Cache-Control", CACHE_CONTROL_VALUE);
                response.setIntHeader("Age", (int) (age / 1000));
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
            store(key, new Entry(body, contentType, now));
        }
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
                if (now - e.getValue().storedAt() > TTL_MILLIS) {
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
