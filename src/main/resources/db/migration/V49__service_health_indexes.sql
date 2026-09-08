-- Indexes for the admin Service Health page.
--
-- Every query behind that page runs on each dashboard poll, and two of them were full scans:
-- the signup-cluster query joins "users" to "auth_credentials" filtered on created_at, and the
-- payment-webhook freshness query does MAX(created_at) over an append-only table that is never
-- pruned. A health page that degrades the database it is reporting on is worse than no page.

-- Signup clusters: "accounts registered in the last 24h, grouped by IP". Partial, because rows
-- without an IP can never appear in that panel — OAuth signups never set the column.
CREATE INDEX IF NOT EXISTS idx_users_created_at_with_ip
    ON "users" (created_at DESC)
    WHERE registration_ip IS NOT NULL;

-- Webhook freshness and the 1-hour receipt count. "Webhooks in the last hour: 0" is the loudest
-- signal available for a lost-webhook outage, so it must stay cheap enough to poll often.
CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_created_at
    ON payment_webhook_events (created_at DESC);
