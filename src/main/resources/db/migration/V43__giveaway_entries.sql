-- Giveaway entries: one per customer account, one per Instagram handle.
--
-- The Hibernate entity (GiveawayEntry) is the source of truth; this migration keeps
-- Flyway-managed schemas in sync, exactly like support_tickets in V42. Wrapped in the same
-- to_regclass guard: Flyway runs BEFORE Hibernate ddl-auto, so on a fresh DB this is a safe
-- no-op and on the existing prod DB it creates the table.
--
-- The two UNIQUE constraints are the whole point of the feature. Without them a customer
-- enters repeatedly by re-submitting, or spreads entries across handles. instagram_handle is
-- stored already normalised (lower-cased, '@' and any profile-URL prefix stripped) so the
-- uniqueness check cannot be dodged by casing or by pasting a full instagram.com URL.
DO $$
BEGIN
    IF to_regclass('public.auth_credentials') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS giveaway_entries (
          id UUID PRIMARY KEY,
          campaign VARCHAR(60) NOT NULL DEFAULT 'IPHONE_18_PRO',
          user_id UUID NOT NULL,                     -- users.id (uid)
          credential_id UUID,                        -- auth_credentials.id (sub), for audit
          instagram_handle VARCHAR(30) NOT NULL,     -- normalised, no '@'
          instagram_handle_raw VARCHAR(200),          -- exactly what the customer typed
          contact_email VARCHAR(255),                 -- snapshotted at entry
          contact_phone VARCHAR(30),
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        -- One entry per account per campaign, and one per handle per campaign.
        CREATE UNIQUE INDEX IF NOT EXISTS uq_giveaway_entries_user
            ON giveaway_entries(campaign, user_id);
        CREATE UNIQUE INDEX IF NOT EXISTS uq_giveaway_entries_handle
            ON giveaway_entries(campaign, instagram_handle);
        CREATE INDEX IF NOT EXISTS idx_giveaway_entries_created_at
            ON giveaway_entries(created_at DESC);

    END IF;
END $$;
