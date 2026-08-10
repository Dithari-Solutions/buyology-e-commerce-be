package com.buyology.ecommerce.analytics;

import com.buyology.ecommerce.analytics.domain.SiteVisit;
import com.buyology.ecommerce.analytics.repository.SiteVisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test against a real Postgres (Testcontainers) for the website visitor counters.
 *
 * <p>Pins the contract {@code VisitorAnalyticsService} depends on: {@code fetchTotals} returns one
 * row of sixteen aggregates in a fixed order, read positionally by the service. A reordered or
 * inserted column would otherwise mis-label every number on the dashboard home page — a silent
 * failure no compiler catches.
 *
 * <p>The seeded traffic makes the three counts genuinely different, which is the whole point of the
 * metric: one visitor with two sessions, one visitor with one session and three page views, and one
 * visitor old enough to fall outside every window.
 *
 * <p>Uses the same Postgres-backed slice as {@code WebhookIdempotencyConstraintIT} (Flyway off,
 * Hibernate owns the schema); {@code FlywayBaselineMigrationIT} covers the migration path.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        // Against a real (non-embedded) Postgres, @DataJpaTest leaves ddl-auto=none,
        // so the schema must be created explicitly for this slice.
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class SiteVisitMetricsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    SiteVisitRepository repository;

    private void save(String visitorId, String sessionId, LocalDate day, Instant at, String path) {
        SiteVisit visit = new SiteVisit();
        visit.setVisitorId(visitorId);
        visit.setSessionId(sessionId);
        visit.setPath(path);
        visit.setVisitDay(day);
        visit.setCreatedAt(at);
        repository.save(visit);
    }

    /** Seeds traffic where uniques, visits and page views all differ, then checks every aggregate. */
    private LocalDate seedTraffic() {
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        // Visitor A: two page views today in one session, plus a second session 8 days ago.
        save("visitorAAA", "sessionS111", today, now, "/en");
        save("visitorAAA", "sessionS111", today, now, "/en/product/1");
        save("visitorAAA", "sessionS222", today.minusDays(8), now.minus(8, ChronoUnit.DAYS), "/en");
        // Visitor B: three page views two days ago in a single session.
        save("visitorBBB", "sessionS333", today.minusDays(2), now.minus(2, ChronoUnit.DAYS), "/en");
        save("visitorBBB", "sessionS333", today.minusDays(2), now.minus(2, ChronoUnit.DAYS), "/en/cart");
        save("visitorBBB", "sessionS333", today.minusDays(2), now.minus(2, ChronoUnit.DAYS), "/en/checkout");
        // Visitor C: outside the 30-day window, so all-time only.
        save("visitorCCC", "sessionS444", today.minusDays(40), now.minus(40, ChronoUnit.DAYS), "/ar");
        repository.flush();
        return today;
    }

    @Test
    void fetchTotals_returnsTheSixteenAggregatesTheServiceReadsPositionally() {
        LocalDate today = seedTraffic();

        List<Object[]> rows = repository.fetchTotals(
                today,
                today.minusDays(6),   // weekFrom
                today.minusDays(13),  // prevWeekFrom
                today.minusDays(29),  // monthFrom
                Instant.now().minus(5, ChronoUnit.MINUTES));

        assertEquals(1, rows.size(), "the aggregate must always produce exactly one row");
        Object[] t = rows.get(0);
        assertEquals(16, t.length, "column count must match the positional read in VisitorAnalyticsService");

        long[] expected = {
                3, 4, 7,  // all time:   3 visitors, 4 sessions, 7 page views
                1, 1, 2,  // today:      visitor A only
                2, 2, 5,  // last 7d:    A (today) + B (2 days ago) — A's 8-day-old session is outside
                2, 3, 6,  // last 30d:   A twice (two sessions) + B; C is 40 days old
                1, 1, 1,  // previous 7d: only A's 8-day-old session
                1         // active now: A's page views are the only recent ones
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((Number) t[i]).longValue(), "aggregate column " + i);
        }
    }

    @Test
    void fetchDailySeries_bucketsByDayAndHonoursTheWindow() {
        LocalDate today = seedTraffic();

        List<Object[]> daily = repository.fetchDailySeries(today.minusDays(29));

        assertEquals(3, daily.size(), "one bucket per day with traffic; the 40-day-old row is excluded");
        // VisitorAnalyticsService.asLocalDate() handles either type Hibernate may return for DATE.
        Object day = daily.get(0)[0];
        assertTrue(day instanceof LocalDate || day instanceof java.sql.Date,
                "unexpected visit_day type: " + (day == null ? "null" : day.getClass()));
        // Newest bucket is today: one visitor, one session, two page views.
        Object[] latest = daily.get(daily.size() - 1);
        assertEquals(1L, ((Number) latest[1]).longValue());
        assertEquals(1L, ((Number) latest[2]).longValue());
        assertEquals(2L, ((Number) latest[3]).longValue());
    }

    @Test
    void deleteOlderThan_trimsOnlyRowsPastTheRetentionWindow() {
        LocalDate today = seedTraffic();

        assertEquals(1, repository.deleteOlderThan(today.minusDays(30)));
        assertEquals(6, repository.count(), "only visitor C's 40-day-old page view may be removed");
    }
}
