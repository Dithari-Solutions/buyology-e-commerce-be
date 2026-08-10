package com.buyology.ecommerce.analytics.repository;

import com.buyology.ecommerce.analytics.domain.SiteVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, UUID> {

    /**
     * Every headline number in one round trip.
     *
     * <p>Aggregate {@code FILTER} is Postgres-specific, which is fine — the schema is already
     * Postgres-only (see the {@code DO $$} / {@code to_regclass} migrations).
     *
     * <p>The windows cannot be summed from the daily series: a visitor who browses on Monday and
     * Thursday is one unique visitor for the week but appears in two daily buckets, so each window
     * needs its own {@code COUNT(DISTINCT …)}.
     *
     * <p>Returns a single row; column order is fixed and read positionally by
     * {@code VisitorAnalyticsService} — keep the two in step.
     *
     * <pre>
     *  0 uniqueVisitors all time      1 visits all time      2 pageViews all time
     *  3 uniqueVisitors today         4 visits today         5 pageViews today
     *  6 uniqueVisitors 7d            7 visits 7d            8 pageViews 7d
     *  9 uniqueVisitors 30d          10 visits 30d          11 pageViews 30d
     * 12 uniqueVisitors prev 7d      13 visits prev 7d      14 pageViews prev 7d
     * 15 visitors seen since :liveFrom (the "active now" badge)
     * </pre>
     */
    @Query(value = """
            SELECT
                COUNT(DISTINCT visitor_id),
                COUNT(DISTINCT session_id),
                COUNT(*),
                COUNT(DISTINCT visitor_id) FILTER (WHERE visit_day = :today),
                COUNT(DISTINCT session_id) FILTER (WHERE visit_day = :today),
                COUNT(*)                   FILTER (WHERE visit_day = :today),
                COUNT(DISTINCT visitor_id) FILTER (WHERE visit_day >= :weekFrom),
                COUNT(DISTINCT session_id) FILTER (WHERE visit_day >= :weekFrom),
                COUNT(*)                   FILTER (WHERE visit_day >= :weekFrom),
                COUNT(DISTINCT visitor_id) FILTER (WHERE visit_day >= :monthFrom),
                COUNT(DISTINCT session_id) FILTER (WHERE visit_day >= :monthFrom),
                COUNT(*)                   FILTER (WHERE visit_day >= :monthFrom),
                COUNT(DISTINCT visitor_id) FILTER (WHERE visit_day >= :prevWeekFrom AND visit_day < :weekFrom),
                COUNT(DISTINCT session_id) FILTER (WHERE visit_day >= :prevWeekFrom AND visit_day < :weekFrom),
                COUNT(*)                   FILTER (WHERE visit_day >= :prevWeekFrom AND visit_day < :weekFrom),
                COUNT(DISTINCT visitor_id) FILTER (WHERE created_at >= :liveFrom)
            FROM site_visits
            """, nativeQuery = true)
    List<Object[]> fetchTotals(@Param("today") LocalDate today,
                              @Param("weekFrom") LocalDate weekFrom,
                              @Param("prevWeekFrom") LocalDate prevWeekFrom,
                              @Param("monthFrom") LocalDate monthFrom,
                              @Param("liveFrom") Instant liveFrom);

    /**
     * Per-day series for the trend chart. Days with no traffic are absent from the result — the
     * service fills the gaps so the chart keeps an even x-axis.
     *
     * <p>Column order: {@code visit_day, uniqueVisitors, visits, pageViews}.
     */
    @Query(value = """
            SELECT visit_day,
                   COUNT(DISTINCT visitor_id),
                   COUNT(DISTINCT session_id),
                   COUNT(*)
            FROM site_visits
            WHERE visit_day >= :from
            GROUP BY visit_day
            ORDER BY visit_day
            """, nativeQuery = true)
    List<Object[]> fetchDailySeries(@Param("from") LocalDate from);

    /** Retention sweep — see {@code SiteVisitPurgeJob}. */
    @Modifying
    @Query("DELETE FROM SiteVisit v WHERE v.visitDay < :before")
    int deleteOlderThan(@Param("before") LocalDate before);
}
