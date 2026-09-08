package com.buyology.ecommerce.admin.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private final int otpDailyCap;

    public ServiceHealthService(JdbcTemplate jdbc,
                                StringRedisTemplate redis,
                                @Value("${verification.max-sends-per-day:500}") int otpDailyCap) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.otpDailyCap = otpDailyCap;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verification", verification());
        out.put("signupClusters", signupClusters());
        out.put("payments", payments());
        out.put("integrations", integrations());
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
