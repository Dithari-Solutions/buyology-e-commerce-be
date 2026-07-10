-- Quiqup STAGING test module: append-only log of webhook callbacks received while
-- exercising the Quiqup delivery API. The Hibernate entity (QuiqupTestEvent) is the
-- source of truth; this migration keeps Flyway-managed (prod) schemas in sync,
-- exactly like the b2b_quotes / b2b_product_requests pairs in V18 / V21.
--
-- This table is unrelated to any real order — it is a throwaway test audit trail so
-- webhooks are visible on the admin "Quiqup Testing" page no matter which app instance
-- (behind the nginx load balancer) received them.
--
-- Wrapped in a to_regclass DO-block guard because Flyway runs BEFORE Hibernate ddl-auto:
-- on a fresh DB (and in FlywayBaselineMigrationIT) Hibernate creates the schema from the
-- entities later, so the guard makes this a safe no-op there and only creates the table on
-- the existing prod DB. Anchored on `orders` (a stable core table present on prod). Mirrors
-- V17/V18/V21 and the other patches.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS quiqup_test_events (
          id UUID PRIMARY KEY,
          direction VARCHAR(40) NOT NULL DEFAULT 'INBOUND_WEBHOOK',
          event_type VARCHAR(100),
          payload JSONB,
          headers JSONB,
          hmac_valid BOOLEAN,
          source_ip VARCHAR(64),
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE INDEX IF NOT EXISTS idx_quiqup_events_created_at ON quiqup_test_events(created_at DESC);

    END IF;
END $$;
