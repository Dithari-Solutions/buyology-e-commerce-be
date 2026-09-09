-- Admin-composed email to customers, with the record that makes it accountable.
--
-- Two tables rather than one because "we sent 600 emails" must be a fact, not an assumption. The
-- existing broadcasts (PromoCodeService, NewsletterService.publishAndSend) log the number of rows
-- they iterated over and call it a send count — so a run where every message bounced reports the
-- same number as one where every message arrived. Here each recipient carries its own outcome.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database the
-- users table does not exist yet and Hibernate creates it from the entity instead. Mirrors
-- V8 / V11 / V17 / V18 / V21 / V22 / V26 / V49.

CREATE TABLE IF NOT EXISTS customer_email_campaigns (
    id                       UUID PRIMARY KEY,
    subject                  VARCHAR(300)  NOT NULL,
    body_html                TEXT          NOT NULL,
    -- SELECTED or ALL_CUSTOMERS. Stored so the record says what was asked for, not just who
    -- happened to be resolved at the time.
    audience                 VARCHAR(30)   NOT NULL,
    -- DRAFT -> SENDING -> SENT | FAILED | ABORTED. The DRAFT -> SENDING move is a conditional
    -- UPDATE, which is what stops a double-click, a retry, and the second application host from
    -- all starting the same run.
    status                   VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    recipient_count          INTEGER       NOT NULL DEFAULT 0,
    sent_count               INTEGER       NOT NULL DEFAULT 0,
    failed_count             INTEGER       NOT NULL DEFAULT 0,
    abort_reason             VARCHAR(500),
    -- Captured on the request thread. AuditService is @Async and reads SecurityContextHolder on
    -- the pool thread, where it is already gone, so anything written from the worker would record
    -- an unknown admin. The name is snapshotted because admins leave.
    created_by_admin_id      UUID          NOT NULL,
    created_by_admin_name    VARCHAR(200),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    started_at               TIMESTAMPTZ,
    finished_at              TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS customer_email_recipients (
    id           UUID PRIMARY KEY,
    campaign_id  UUID          NOT NULL REFERENCES customer_email_campaigns(id) ON DELETE CASCADE,
    user_id      UUID          NOT NULL,
    email        VARCHAR(255)  NOT NULL,
    -- PENDING -> SENT | FAILED, written from the send call's actual return value.
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    error        VARCHAR(500),
    sent_at      TIMESTAMPTZ,
    -- Exactly once per address per campaign, enforced here rather than hoped for in code: a
    -- customer holding more than one credential row must not receive the same mail twice.
    CONSTRAINT uq_customer_email_recipient UNIQUE (campaign_id, email)
);

CREATE INDEX IF NOT EXISTS idx_customer_email_recipients_campaign
    ON customer_email_recipients (campaign_id, status);

CREATE INDEX IF NOT EXISTS idx_customer_email_campaigns_created_at
    ON customer_email_campaigns (created_at DESC);

-- Per-customer opt-out. Until now the only suppression signal in the system was
-- newsletter_subscribers.is_active, which nothing joined to a customer — so a broadcast to "all
-- customers" would have mailed people who had explicitly unsubscribed. The token mirrors the
-- newsletter's: an unguessable single-purpose UUID that a link in the footer can carry.
DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL THEN
        ALTER TABLE "users" ADD COLUMN IF NOT EXISTS email_opt_out_token UUID;
        ALTER TABLE "users" ADD COLUMN IF NOT EXISTS email_opt_out_at    TIMESTAMPTZ;

        -- Backfill so every existing customer has a working unsubscribe link from the first send.
        UPDATE "users"
           SET email_opt_out_token = gen_random_uuid()
         WHERE email_opt_out_token IS NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_opt_out_token
            ON "users" (email_opt_out_token)
            WHERE email_opt_out_token IS NOT NULL;
    END IF;
END $$;
