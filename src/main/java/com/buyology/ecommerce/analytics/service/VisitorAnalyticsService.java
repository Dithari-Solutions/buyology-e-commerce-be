package com.buyology.ecommerce.analytics.service;

import com.buyology.ecommerce.analytics.domain.SiteVisit;
import com.buyology.ecommerce.analytics.dto.TrackVisitRequest;
import com.buyology.ecommerce.analytics.dto.VisitorMetricsResponse;
import com.buyology.ecommerce.analytics.dto.VisitorMetricsResponse.VisitorDailyPoint;
import com.buyology.ecommerce.analytics.dto.VisitorMetricsResponse.VisitorWindow;
import com.buyology.ecommerce.analytics.repository.SiteVisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * First-party visitor counting for the storefront.
 *
 * <p>Deliberately not read from Google Analytics: GA4 numbers need a service account plus the Data
 * API and are sampled/delayed, whereas the dashboard home page needs a number it can show next to
 * orders and revenue immediately. The GA4 tag on the web app stays as it is — this counts in
 * parallel, against our own database.
 *
 * <p>Write path is one insert per page view and is fire-and-forget: any failure is logged and
 * swallowed, because a broken counter must never break a page load. Read path is cached briefly so
 * repeated dashboard refreshes do not re-run the aggregate.
 */
@Service
public class VisitorAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(VisitorAnalyticsService.class);

    /** The day the rest of the dashboard reports on (matches CartService.BUSINESS_ZONE). */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Dubai");

    /** Ids are opaque to us; this only keeps the column clean and un-injectable. */
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    /**
     * Crawlers, previewers and uptime checks. They are traffic, but counting them as visitors makes
     * the metric useless — a single Ahrefs sweep can outnumber a day of real customers.
     */
    private static final Pattern BOT_USER_AGENT = Pattern.compile(
            "bot|crawler|crawl|spider|slurp|curl|wget|python-requests|okhttp|headless|phantomjs|"
            + "lighthouse|pagespeed|gtmetrix|pingdom|uptime|monitoring|semrush|ahrefs|mj12|dotbot|"
            + "facebookexternalhit|whatsapp|telegrambot|twitterbot|linkedinbot|slackbot|discordbot|"
            + "embedly|preview|feedfetcher|apis-google|adsbot",
            Pattern.CASE_INSENSITIVE);

    private static final int MAX_PATH_LENGTH = 300;
    private static final int MAX_DAILY_WINDOW = 180;

    /**
     * Control characters, stripped from every free-text field. A JSON body may legally carry an
     * escaped NUL, which Postgres rejects outright — the insert would fail and lose the page view
     * instead of storing a sanitised one. Also keeps newlines out of anything that reaches a log.
     */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}\\p{Cc}\\p{Cf}]");

    private final SiteVisitRepository repository;

    /** Kill switch: when false the beacon is accepted and dropped, so the storefront never errors. */
    private final boolean enabled;

    /** Retention for raw page-view rows; the purge job trims anything older. */
    private final int retentionDays;

    /** How long a computed metrics payload is reused before the aggregate re-runs. */
    private final Duration cacheTtl;

    /** Window for the "active now" count. */
    private final Duration activeWindow;

    /**
     * Salt for the stored IP digest. Blank (the default) means no IP-derived value is stored at all
     * — an unsalted hash of an IPv4 address is trivially reversible, so it would be personal data
     * dressed up as a hash.
     */
    private final String ipSalt;

    /** Only trust X-Forwarded-For behind a reverse proxy, exactly as the rate limiter does. */
    private final boolean trustForwardedHeaders;

    private record CachedMetrics(Instant computedAt, VisitorMetricsResponse payload) {}

    private final Map<Integer, CachedMetrics> cache = new ConcurrentHashMap<>();

    public VisitorAnalyticsService(
            SiteVisitRepository repository,
            @Value("${app.analytics.enabled:true}") boolean enabled,
            @Value("${app.analytics.retention-days:400}") int retentionDays,
            @Value("${app.analytics.metrics-cache-seconds:30}") long metricsCacheSeconds,
            @Value("${app.analytics.active-window-minutes:5}") long activeWindowMinutes,
            @Value("${app.analytics.ip-salt:}") String ipSalt,
            @Value("${app.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.repository = repository;
        this.enabled = enabled;
        this.retentionDays = Math.max(1, retentionDays);
        this.cacheTtl = Duration.ofSeconds(Math.max(0, metricsCacheSeconds));
        this.activeWindow = Duration.ofMinutes(Math.max(1, activeWindowMinutes));
        this.ipSalt = ipSalt == null ? "" : ipSalt.trim();
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    // ── Write path ───────────────────────────────────────────────────────────

    /**
     * Records a page view.
     *
     * @return false when the beacon was ignored (tracking disabled, or the caller is a bot). The
     *         controller answers 202 either way — the storefront has nothing to do with the answer.
     * @throws IllegalArgumentException if the visitor/session ids are missing or malformed
     */
    @Transactional
    public boolean record(TrackVisitRequest request, String userAgent, String forwardedFor, String remoteAddr) {
        String visitorId = requireId(request == null ? null : request.visitorId(), "visitorId");
        String sessionId = requireId(request == null ? null : request.sessionId(), "sessionId");

        if (!enabled) {
            return false;
        }
        if (userAgent != null && BOT_USER_AGENT.matcher(userAgent).find()) {
            return false;
        }

        Instant now = Instant.now();
        SiteVisit visit = new SiteVisit();
        visit.setVisitorId(visitorId);
        visit.setSessionId(sessionId);
        visit.setPath(normalisePath(request.path()));
        visit.setReferrerHost(referrerHost(request.referrer()));
        visit.setLanguage(truncate(blankToNull(request.language()), 8));
        visit.setIpHash(hashIp(resolveClientIp(forwardedFor, remoteAddr)));
        visit.setVisitDay(LocalDate.ofInstant(now, BUSINESS_ZONE));
        visit.setCreatedAt(now);
        repository.save(visit);
        return true;
    }

    private static String requireId(String raw, String field) {
        String value = raw == null ? "" : raw.trim();
        if (!ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is required and must be 8-64 URL-safe characters");
        }
        return value;
    }

    /** Keeps the pathname only, root-anchored, control-character-free and length-capped. */
    private static String normalisePath(String raw) {
        String value = raw == null ? "" : stripControlChars(raw).trim();
        int cut = value.indexOf('?');
        if (cut >= 0) value = value.substring(0, cut);
        cut = value.indexOf('#');
        if (cut >= 0) value = value.substring(0, cut);
        if (value.isEmpty()) return "/";
        if (!value.startsWith("/")) value = "/" + value;
        return truncate(value, MAX_PATH_LENGTH);
    }

    /** Host of the referrer, or null for direct traffic and anything unparseable. */
    private static String referrerHost(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        try {
            String host = URI.create(value).getHost();
            return truncate(blankToNull(host), 255);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String hashIp(String ip) {
        if (ipSalt.isEmpty() || ip == null || ip.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((ipSalt + '|' + ip).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(out.length * 2);
            for (byte b : out) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null; // SHA-256 is mandated by the JRE; unreachable in practice.
        }
    }

    private String resolveClientIp(String forwardedFor, String remoteAddr) {
        if (trustForwardedHeaders && forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].strip();
        }
        return remoteAddr;
    }

    // ── Read path ────────────────────────────────────────────────────────────

    /**
     * Headline counts plus a gap-filled daily series.
     *
     * @param days length of the daily series, clamped to 1..{@value #MAX_DAILY_WINDOW}
     */
    @Transactional(readOnly = true)
    public VisitorMetricsResponse metrics(int days) {
        int window = Math.min(Math.max(days, 1), MAX_DAILY_WINDOW);

        CachedMetrics cached = cache.get(window);
        if (cached != null && Instant.now().isBefore(cached.computedAt().plus(cacheTtl))) {
            return cached.payload();
        }

        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, BUSINESS_ZONE);
        LocalDate weekFrom = today.minusDays(6);
        LocalDate prevWeekFrom = weekFrom.minusDays(7);
        LocalDate monthFrom = today.minusDays(29);
        LocalDate seriesFrom = today.minusDays(window - 1L);

        List<Object[]> totalsRows = repository.fetchTotals(
                today, weekFrom, prevWeekFrom, monthFrom, now.minus(activeWindow));
        Object[] t = totalsRows.isEmpty() ? new Object[16] : totalsRows.get(0);

        VisitorMetricsResponse payload = new VisitorMetricsResponse(
                window(t, 0),
                window(t, 3),
                window(t, 6),
                window(t, 9),
                window(t, 12),
                asLong(t[15]),
                window,
                dailySeries(seriesFrom, today));

        // A zero TTL means "always recompute" — do not poison the cache with it.
        if (!cacheTtl.isZero()) {
            cache.put(window, new CachedMetrics(now, payload));
        }
        return payload;
    }

    /** Reads the (uniqueVisitors, visits, pageViews) triple starting at {@code offset}. */
    private static VisitorWindow window(Object[] row, int offset) {
        return new VisitorWindow(asLong(row[offset]), asLong(row[offset + 1]), asLong(row[offset + 2]));
    }

    /**
     * One point per day between {@code from} and {@code to} inclusive. Days the database has no rows
     * for are emitted as zeros so the chart's x-axis stays evenly spaced instead of skipping
     * quiet days.
     */
    private List<VisitorDailyPoint> dailySeries(LocalDate from, LocalDate to) {
        Map<LocalDate, Object[]> byDay = new HashMap<>();
        for (Object[] row : repository.fetchDailySeries(from)) {
            byDay.put(asLocalDate(row[0]), row);
        }

        List<VisitorDailyPoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            Object[] row = byDay.get(day);
            points.add(row == null
                    ? new VisitorDailyPoint(day, 0, 0, 0)
                    : new VisitorDailyPoint(day, asLong(row[1]), asLong(row[2]), asLong(row[3])));
        }
        return points;
    }

    // ── Retention ────────────────────────────────────────────────────────────

    /** Drops raw page-view rows past the retention window. Totals of trimmed days are lost. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteOlderThan(
                LocalDate.now(BUSINESS_ZONE).minusDays(retentionDays));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /** Hibernate may hand back {@link LocalDate} or {@link java.sql.Date} for a DATE column. */
    private static LocalDate asLocalDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.time.temporal.TemporalAccessor ta) return LocalDate.from(ta);
        log.warn("Unexpected visit_day type from the database: {}", value == null ? "null" : value.getClass());
        return LocalDate.EPOCH;
    }

    private static String stripControlChars(String value) {
        return value == null ? null : CONTROL_CHARS.matcher(value).replaceAll("");
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = stripControlChars(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
