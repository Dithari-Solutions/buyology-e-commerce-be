-- Open/closed state for a giveaway campaign, so a draw can be ended from the dashboard without a
-- deploy and without deleting anything: entries stay, the doors close.
--
-- Keyed by campaign rather than being a single settings row. GiveawayEntry already carries the
-- campaign as a column ("a column rather than a constant so the next one needs no migration"), and
-- a switch that could only ever describe one campaign would be the piece that forces that migration.
--
-- Same to_regclass DO-block guard as V42/V43/V45: Flyway runs BEFORE Hibernate ddl-auto, so on a
-- fresh DB this is a safe no-op while on the existing prod DB it creates the table.
DO $$
BEGIN
    IF to_regclass('public.giveaway_entries') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS giveaway_campaigns (
          id UUID PRIMARY KEY,
          campaign VARCHAR(60) NOT NULL UNIQUE,
          is_open BOOLEAN NOT NULL DEFAULT true,
          closed_at TIMESTAMPTZ,
          updated_by UUID,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        -- The live campaign starts open, which is what it already is in effect today. Without this
        -- row the first read would create it anyway; seeding it means the dashboard shows the true
        -- state before anyone touches the switch.
        INSERT INTO giveaway_campaigns (id, campaign, is_open)
        VALUES (gen_random_uuid(), 'IPHONE_18_PRO', true)
        ON CONFLICT (campaign) DO NOTHING;

    END IF;
END $$;
