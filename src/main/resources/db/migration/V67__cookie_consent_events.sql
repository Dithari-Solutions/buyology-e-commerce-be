-- Append-only anonymous choices. No cookie contents, customer identities, IPs, or user agents.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS cookie_consent_events (
            id UUID PRIMARY KEY,
            consent_id UUID NOT NULL,
            analytics BOOLEAN NOT NULL,
            marketing BOOLEAN NOT NULL,
            policy_version VARCHAR(20) NOT NULL,
            source VARCHAR(16) NOT NULL,
            language VARCHAR(8) NOT NULL,
            source_host VARCHAR(253),
            recorded_at TIMESTAMPTZ NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_cookie_consent_recorded ON cookie_consent_events(recorded_at);
        CREATE INDEX IF NOT EXISTS idx_cookie_consent_browser ON cookie_consent_events(consent_id, recorded_at);
    END IF;
END $$;
