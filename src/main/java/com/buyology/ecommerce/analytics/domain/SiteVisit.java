package com.buyology.ecommerce.analytics.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One storefront page view.
 *
 * <p>Three counts are derived from this table and they are deliberately different things:
 * <ul>
 *   <li><b>unique visitors</b> — distinct {@code visitorId} (a browser that keeps its
 *       localStorage, so returning tomorrow still counts once),</li>
 *   <li><b>visits</b> — distinct {@code sessionId} (one browsing session; the "general"
 *       visitor count, where the same person coming back next week counts twice),</li>
 *   <li><b>page views</b> — row count.</li>
 * </ul>
 *
 * <p>{@code visitDay} is stored rather than derived from {@code createdAt} so the daily buckets are
 * indexable and are cut at midnight <em>Asia/Dubai</em> — the business day the rest of the dashboard
 * reports on — instead of at midnight UTC, which would land at 04:00 local.
 *
 * <p>No IP address is kept. {@code ipHash} is a salted digest written only when
 * {@code app.analytics.ip-salt} is configured, and exists purely so a flood of fake beacons can be
 * traced to one source; it is never exposed by any endpoint.
 */
@Entity
@Table(name = "site_visits", indexes = {
        @Index(name = "idx_site_visits_created_at", columnList = "created_at"),
        @Index(name = "idx_site_visits_day_visitor", columnList = "visit_day, visitor_id"),
        @Index(name = "idx_site_visits_day_session", columnList = "visit_day, session_id")
})
public class SiteVisit {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Stable per-browser id minted by the storefront and kept in localStorage. */
    @Column(name = "visitor_id", nullable = false, updatable = false, length = 64)
    private String visitorId;

    /** Per-session id kept in sessionStorage — a new tab/visit gets a new one. */
    @Column(name = "session_id", nullable = false, updatable = false, length = 64)
    private String sessionId;

    /** Pathname only (never the query string, which can carry tokens or emails). */
    @Column(name = "path", nullable = false, updatable = false, length = 300)
    private String path;

    /** Host of the referring page, so traffic sources are visible without storing full URLs. */
    @Column(name = "referrer_host", updatable = false, length = 255)
    private String referrerHost;

    @Column(name = "language", updatable = false, length = 8)
    private String language;

    @Column(name = "ip_hash", updatable = false, length = 64)
    private String ipHash;

    @Column(name = "visit_day", nullable = false, updatable = false)
    private LocalDate visitDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }

    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getReferrerHost() { return referrerHost; }
    public void setReferrerHost(String referrerHost) { this.referrerHost = referrerHost; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }

    public LocalDate getVisitDay() { return visitDay; }
    public void setVisitDay(LocalDate visitDay) { this.visitDay = visitDay; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
