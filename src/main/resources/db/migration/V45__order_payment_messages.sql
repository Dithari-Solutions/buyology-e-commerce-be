-- Admin outreach about a payment that did not complete.
--
-- Deliberately NOT order_tracking_events: those are rendered to the customer on their own order
-- page (OrderResponse.trackingHistory), and "we emailed this customer about their failed payment"
-- is an internal note. It also has no OrderStatus to file itself under.
--
-- The payment attempts themselves are not duplicated here — payment_transactions already records
-- every attempt with its method, status and decline code, which is the struggled-then-repaid story
-- in full. This table adds only the part nothing else records: what we said, and who said it.
--
-- Same to_regclass DO-block guard as V42/V43: Flyway runs BEFORE Hibernate ddl-auto, so on a fresh
-- DB this is a safe no-op while on the existing prod DB it creates the table.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS order_payment_messages (
          id UUID PRIMARY KEY,
          order_id UUID NOT NULL,
          template_key VARCHAR(60),                  -- null when the admin wrote it themselves
          subject VARCHAR(200) NOT NULL,
          body TEXT NOT NULL,                        -- as typed; escaped at render, never stored as HTML
          diagnosis_code VARCHAR(60),                -- the stall reason at the moment we reached out
          sent_by UUID,                              -- admin users.id
          sent_by_name VARCHAR(150),                 -- snapshotted: admins get deleted, the log stays
          email_sent BOOLEAN NOT NULL DEFAULT false,
          notification_sent BOOLEAN NOT NULL DEFAULT false,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE INDEX IF NOT EXISTS idx_opm_order ON order_payment_messages (order_id, created_at DESC);

    END IF;
END $$;
