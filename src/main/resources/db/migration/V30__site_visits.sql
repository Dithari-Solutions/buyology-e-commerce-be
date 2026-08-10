-- Website visitor counting for the dashboard home page.
--
-- One row per storefront page view, written by the public POST /api/analytics/visit beacon. Three
-- different numbers come out of it and they are not interchangeable:
--   * unique visitors = COUNT(DISTINCT visitor_id)  — browsers (localStorage id)
--   * visits          = COUNT(DISTINCT session_id)  — browsing sessions ("general" visitors)
--   * page views      = COUNT(*)
--
-- visit_day is stored rather than derived from created_at so the daily buckets are indexable and cut
-- at midnight Asia/Dubai (the business day the dashboard reports on) instead of midnight UTC.
--
-- No IP address is stored. ip_hash is a salted SHA-256 written only when app.analytics.ip-salt is
-- configured, purely so a flood of fake beacons can be traced to one source; no endpoint returns it.
--
-- This is the only table in the schema that grows with traffic rather than with business events, so
-- SiteVisitPurgeJob trims rows past app.analytics.retention-days (default 400) nightly.
--
-- The SiteVisit entity is the source of truth; this migration keeps Flyway-managed (prod) schemas in
-- sync. Wrapped in a to_regclass DO-block guard because Flyway runs BEFORE Hibernate ddl-auto: on a
-- fresh DB (and in FlywayBaselineMigrationIT) Hibernate creates the schema from the entities later,
-- so the guard makes this a safe no-op there and only creates the table on the existing prod DB.
-- Anchored on `orders` (a stable core table present on prod). Mirrors V17/V18/V21/V22.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS site_visits (
          id UUID PRIMARY KEY,
          visitor_id VARCHAR(64) NOT NULL,
          session_id VARCHAR(64) NOT NULL,
          path VARCHAR(300) NOT NULL,
          referrer_host VARCHAR(255),
          language VARCHAR(8),
          ip_hash VARCHAR(64),
          visit_day DATE NOT NULL,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        -- created_at: the "active now" window. day+visitor / day+session: the windowed
        -- COUNT(DISTINCT …) aggregates and the daily trend series.
        CREATE INDEX IF NOT EXISTS idx_site_visits_created_at ON site_visits(created_at);
        CREATE INDEX IF NOT EXISTS idx_site_visits_day_visitor ON site_visits(visit_day, visitor_id);
        CREATE INDEX IF NOT EXISTS idx_site_visits_day_session ON site_visits(visit_day, session_id);

    END IF;
END $$;
