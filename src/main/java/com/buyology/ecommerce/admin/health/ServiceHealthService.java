package com.buyology.ecommerce.admin.health;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an admin needs to see to catch the next incident, assembled from what the system already
 * knows about itself.
 *
 * <p>Written after an SMS-pumping attack ran the Twilio balance to -$385 unnoticed. Three things
 * failed together, and each panel here answers one of them: nobody could see send volume or spend;
 * the one real error line was buried under ~288 benign ERROR lines a day from a payment sweeper
 * retrying dead orders; and eight fraudulent accounts registered from shared IPs in two hours left
 * a trace in the database that nothing ever read.
 *
 * <p>The guiding rule is that a panel must be HONEST about what it can prove. Several signals in
 * this codebase look stronger than they are, and reporting them at face value would build exactly
 * the false confidence that let the attack run:
 * <ul>
 *   <li>{@code scheduled_task_runs} records a lock CLAIM, written before the work starts, so a job
 *       that crashed on its first item is indistinguishable from one that finished. It is never
 *       reported as "ran".</li>
 *   <li>The guard's Redis counter both over-counts (it increments before the quota test, and before
 *       Twilio is called at all) and under-counts (one send path does not go through the guard). It
 *       is labelled "guarded attempts", never "messages sent".</li>
 *   <li>{@code phone_otps} and {@code email_otps} are purged hourly against a ten-minute expiry, so
 *       they hold roughly seventy minutes of history. They are not used for daily volume — a chart
 *       drawn from them would under-report by a factor of twenty and look calm while burning.</li>
 * </ul>
 */
@Service
public class ServiceHealthService {

    private static final Logger log = LoggerFactory.getLogger(ServiceHealthService.class);

    /** Written by PhoneVerificationGuard#consume, one key per UTC day, 25h TTL. */
    private static final String OTP_GLOBAL_PREFIX = "otp:send:global:";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final int otpDailyCap;

    public ServiceHealthService(JdbcTemplate jdbc,
                                StringRedisTemplate redis,
                                MeterRegistry meters,
                                @Value("${verification.max-sends-per-day:500}") int otpDailyCap) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.meters = meters;
        this.otpDailyCap = otpDailyCap;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verification", verification());
        out.put("signupClusters", signupClusters());
        out.put("payments", payments());
        out.put("integrations", integrations());
        out.put("http", http());
        out.put("checkout", checkout());
        return out;
    }

    // ── Verification spend ────────────────────────────────────────────────────

    /**
     * Today's guarded attempts against the circuit breaker, plus the previous six days for shape.
     *
     * <p>"Attempts", not "sends": the counter is incremented once a number clears the format and
     * destination gates, which is before the quota is tested and before Twilio is called, so a
     * rejected or failed send still counts. It is a leading indicator of pressure on the endpoint,
     * which is exactly what was missing — not an invoice.
     */
    private Map<String, Object> verification() {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<Map<String, Object>> days = new ArrayList<>();
        long todayCount = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long n = readCounter(OTP_GLOBAL_PREFIX + day);
            if (i == 0) todayCount = n;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("day", day.toString());
            d.put("attempts", n);
            days.add(d);
        }

        m.put("guardedAttemptsToday", todayCount);
        m.put("dailyCap", otpDailyCap);
        m.put("history", days);
        // Deliberately explicit: the page must not present this as spend.
        m.put("caveat", "Guarded attempts, not messages sent. Counts attempts that passed the format "
                + "and country checks, including ones later blocked by quota or failed at the provider.");
        return m;
    }

    private long readCounter(String key) {
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("[HEALTH] Could not read {}: {}", key, e.getMessage());
            return -1L; // distinguishable from a genuine zero
        }
    }

    // ── Signup clusters — the panel that would have caught the attack ─────────

    /**
     * Registration IPs with three or more accounts in the last 24 hours.
     *
     * <p>Empty on a normal day; one row is the alarm. During the incident this would have shown
     * 66.102.125.237 with three accounts minutes apart on disposable domains.
     *
     * <p>Two limits belong on the panel itself, not hidden here: the IP comes from an unvalidated
     * X-Forwarded-For header so it is client-poisonable, and no OAuth provider sets it, so Google
     * and Apple signups are invisible to this query.
     */
    private List<Map<String, Object>> signupClusters() {
        String sql = """
                SELECT u.registration_ip                                        AS ip,
                       COUNT(DISTINCT u.id)                                     AS accounts,
                       MIN(u.created_at)                                        AS first_seen,
                       MAX(u.created_at)                                        AS last_seen,
                       COUNT(DISTINCT u.registration_device)                    AS devices,
                       STRING_AGG(DISTINCT SPLIT_PART(LOWER(c.email), '@', 2), ', ') AS domains
                FROM "users" u
                LEFT JOIN "auth_credentials" c ON c.user_id = u.id
                WHERE u.created_at >= NOW() - INTERVAL '24 hours'
                  AND u.registration_ip IS NOT NULL
                GROUP BY u.registration_ip
                HAVING COUNT(DISTINCT u.id) >= 3
                ORDER BY COUNT(DISTINCT u.id) DESC
                LIMIT 25
                """;
        return safeQuery(sql, "signup clusters");
    }

    // ── Payment plumbing ──────────────────────────────────────────────────────

    /**
     * The sweeper backlog, webhook freshness, and open anomalies.
     *
     * <p>"Stuck in settlement sweep: 2, oldest 9 days" is the same fact as 288 ERROR lines a day,
     * in a form that cannot be scrolled past. "Webhooks in the last hour: 0" is the loudest signal
     * available for a lost-webhook outage, and unlike a log line it cannot be drowned out.
     */
    private Map<String, Object> payments() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stuckInSweep", scalar(
                "SELECT COUNT(*) FROM payment_transactions "
                        + "WHERE status IN ('PENDING','PROCESSING') "
                        + "AND created_at >= NOW() - INTERVAL '14 days'", "stuck payments"));
        m.put("oldestStuckHours", scalar(
                "SELECT COALESCE(ROUND(EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) / 3600), 0) "
                        + "FROM payment_transactions WHERE status IN ('PENDING','PROCESSING') "
                        + "AND created_at >= NOW() - INTERVAL '14 days'", "oldest stuck payment"));
        m.put("webhooksLastHour", scalar(
                "SELECT COUNT(*) FROM payment_webhook_events "
                        + "WHERE created_at >= NOW() - INTERVAL '1 hour'", "recent webhooks"));
        m.put("minutesSinceLastWebhook", scalar(
                "SELECT COALESCE(ROUND(EXTRACT(EPOCH FROM (NOW() - MAX(created_at))) / 60), -1) "
                        + "FROM payment_webhook_events", "webhook freshness"));
        m.put("hmacInvalid24h", scalar(
                "SELECT COUNT(*) FROM payment_webhook_events "
                        + "WHERE hmac_valid = false AND created_at >= NOW() - INTERVAL '24 hours'",
                "invalid hmac"));
        m.put("unprocessedWebhooks", scalar(
                "SELECT COUNT(*) FROM payment_webhook_events WHERE processed = false", "unprocessed webhooks"));
        return m;
    }

    // ── Downstream integrations that fail silently into a column ─────────────

    /**
     * ERPNext and Quiqup both fail the way Twilio did: the failure is recorded in a column on the
     * order and nothing reads it. A paid order can sit unsynced or undispatched indefinitely while
     * every dashboard reports the order as fine.
     */
    private Map<String, Object> integrations() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("erpFailed24h", scalar(
                "SELECT COUNT(*) FROM orders WHERE erp_sync_error IS NOT NULL "
                        + "AND created_at >= NOW() - INTERVAL '24 hours'", "erp failures"));
        m.put("erpUnsyncedOver30m", scalar(
                "SELECT COUNT(*) FROM orders WHERE status = 'PAID' AND erp_synced_at IS NULL "
                        + "AND created_at < NOW() - INTERVAL '30 minutes' "
                        + "AND created_at >= NOW() - INTERVAL '7 days'", "erp backlog"));
        m.put("quiqupFailed24h", scalar(
                "SELECT COUNT(*) FROM orders WHERE quiqup_dispatch_error IS NOT NULL "
                        + "AND created_at >= NOW() - INTERVAL '24 hours'", "quiqup failures"));
        m.put("oldestUndispatchedMinutes", scalar(
                "SELECT COALESCE(ROUND(EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) / 60), -1) "
                        + "FROM orders WHERE status = 'PAID' AND quiqup_order_id IS NULL "
                        + "AND created_at >= NOW() - INTERVAL '7 days'", "undispatched orders"));
        return m;
    }


    // ── HTTP status codes ─────────────────────────────────────────────────────

    /**
     * What the application has actually been answering, by status code.
     *
     * <p>Read from Micrometer's {@code http.server.requests} rather than by scraping our own
     * /actuator/prometheus over HTTP — same numbers, no round trip, and it cannot be affected by the
     * thread starvation it is meant to reveal.
     *
     * <p>These are CUMULATIVE SINCE JVM START, which is why uptime is reported alongside them. A
     * count without a window is not a rate, and presenting it as one would be the same false
     * precision this page exists to avoid. The ratios are the part worth reading: 5xx as a share of
     * all traffic, and which endpoints produce them.
     *
     * <p>Three codes get called out by name because each is a different kind of warning. 5xx is us
     * breaking. 401 in bulk on one endpoint is credential stuffing or an integration with a stale
     * token. 429 is the rate limiter doing its job — which, spiking, is the earliest cheap signal of
     * exactly the abuse that drained the Twilio balance.
     */
    private Map<String, Object> http() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Collection<Timer> timers = meters.find("http.server.requests").timers();
            if (timers.isEmpty()) {
                m.put("available", false);
                m.put("note", "No request metrics yet — the application has served nothing since start.");
                return m;
            }

            long total = 0, c2xx = 0, c3xx = 0, c4xx = 0, c5xx = 0;
            long unauthorized = 0, forbidden = 0, throttled = 0;
            Map<String, Long> serverErrorsByUri = new LinkedHashMap<>();
            Map<String, Long> unauthorizedByUri = new LinkedHashMap<>();

            for (Timer t : timers) {
                long n = t.count();
                if (n == 0) continue;
                total += n;
                String status = t.getId().getTag("status");
                String uri = t.getId().getTag("uri");
                if (status == null) continue;

                switch (status.charAt(0)) {
                    case '2' -> c2xx += n;
                    case '3' -> c3xx += n;
                    case '4' -> c4xx += n;
                    case '5' -> c5xx += n;
                    default -> { }
                }
                if ("401".equals(status)) {
                    unauthorized += n;
                    if (uri != null) unauthorizedByUri.merge(uri, n, Long::sum);
                } else if ("403".equals(status)) {
                    forbidden += n;
                } else if ("429".equals(status)) {
                    throttled += n;
                }
                if (status.startsWith("5") && uri != null) {
                    serverErrorsByUri.merge(uri, n, Long::sum);
                }
            }

            m.put("available", true);
            m.put("uptimeHours", uptimeHours());
            m.put("total", total);
            m.put("status2xx", c2xx);
            m.put("status3xx", c3xx);
            m.put("status4xx", c4xx);
            m.put("status5xx", c5xx);
            m.put("serverErrorRatePercent", total == 0 ? 0 : Math.round(c5xx * 10000.0 / total) / 100.0);
            m.put("unauthorized401", unauthorized);
            m.put("forbidden403", forbidden);
            m.put("throttled429", throttled);
            m.put("topServerErrors", topN(serverErrorsByUri, 8));
            m.put("topUnauthorized", topN(unauthorizedByUri, 8));
            m.put("caveat", "Counts are cumulative since the application started, not a rate. "
                    + "Read them against uptime, and watch the ratios rather than the totals.");
        } catch (Exception e) {
            log.warn("[HEALTH] HTTP metrics unavailable: {}", e.getMessage());
            m.put("available", false);
        }
        return m;
    }

    private double uptimeHours() {
        try {
            var gauge = meters.find("process.uptime").gauge();
            return gauge == null ? -1 : Math.round(gauge.value() / 36.0) / 100.0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** The busiest offenders first — a long tail of one-offs is noise, a concentrated spike is not. */
    private List<Map<String, Object>> topN(Map<String, Long> counts, int n) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(n)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uri", e.getKey());
                    row.put("count", e.getValue());
                    return row;
                })
                .toList();
    }

    // ── Checkout that never completed ────────────────────────────────────────

    /**
     * Orders that reached checkout and stopped there.
     *
     * <p>Distinct from the settlement backlog above: that panel asks whether WE failed to record a
     * payment the gateway took, this one asks how many customers walked away mid-payment. Both
     * matter and they are not the same number — an order can be abandoned by the customer, or
     * stranded by a webhook we never processed, and only the pair distinguishes them.
     *
     * <p>Paid and failed counts sit alongside deliberately, because "18 pending" means nothing
     * without knowing whether 20 or 2000 orders were placed today.
     */
    private Map<String, Object> checkout() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingPaymentTotal", scalar(
                "SELECT COUNT(*) FROM orders WHERE status = 'PENDING_PAYMENT'", "pending orders"));
        m.put("pendingPayment24h", scalar(
                "SELECT COUNT(*) FROM orders WHERE status = 'PENDING_PAYMENT' "
                        + "AND created_at >= NOW() - INTERVAL '24 hours'", "pending orders 24h"));
        m.put("oldestPendingHours", scalar(
                "SELECT COALESCE(ROUND(EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) / 3600), -1) "
                        + "FROM orders WHERE status = 'PENDING_PAYMENT'", "oldest pending order"));
        m.put("paid24h", scalar(
                "SELECT COUNT(*) FROM orders WHERE status <> 'PENDING_PAYMENT' AND status <> 'CANCELLED' "
                        + "AND status <> 'FAILED' AND created_at >= NOW() - INTERVAL '24 hours'",
                "paid orders 24h"));
        m.put("failed24h", scalar(
                "SELECT COUNT(*) FROM orders WHERE status = 'FAILED' "
                        + "AND created_at >= NOW() - INTERVAL '24 hours'", "failed orders 24h"));
        return m;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A panel that cannot be computed must not take the whole page down with it, and must not
     * silently render as zero either — a health page reporting a false all-clear is the failure it
     * exists to prevent. Unavailable numbers come back as -1 for the UI to mark unknown.
     */
    private long scalar(String sql, String what) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0L : v;
        } catch (Exception e) {
            log.warn("[HEALTH] {} query failed: {}", what, e.getMessage());
            return -1L;
        }
    }

    private List<Map<String, Object>> safeQuery(String sql, String what) {
        try {
            return jdbc.queryForList(sql);
        } catch (Exception e) {
            log.warn("[HEALTH] {} query failed: {}", what, e.getMessage());
            return List.of();
        }
    }
}
