-- Indexes for the admin Service Health page.
--
-- Two of that page's queries were sequential scans: the signup-cluster panel joins "users" to
-- "auth_credentials" filtered on created_at, and the webhook-freshness panel does MAX(created_at)
-- over payment_webhook_events, which is append-only and never pruned. A health page that degrades
-- the database it reports on is not a health page.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database (and
-- in FlywayBaselineMigrationIT) these tables do not exist yet and Hibernate creates them from the
-- entities instead; on the existing prod DB this adds the indexes. Mirrors V8 / V11 / V17 / V18 /
-- V21 / V22 / V26.
DO $$
BEGIN
    -- Signup clusters: "accounts registered in the last 24h, grouped by IP", the panel that would
    -- have shown last week's fraudulent registrations. Partial, because a row without an IP can
    -- never appear in it — OAuth signups never set the column.
    IF to_regclass('public.users') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_users_created_at_with_ip
            ON "users" (created_at DESC)
            WHERE registration_ip IS NOT NULL;
    END IF;

    -- Webhook freshness and the one-hour receipt count. "Webhooks in the last hour: 0" is the
    -- loudest signal available for a lost-webhook outage, so it has to stay cheap enough to poll.
    IF to_regclass('public.payment_webhook_events') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_created_at
            ON payment_webhook_events (created_at DESC);
    END IF;
END $$;
