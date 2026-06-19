-- Promo-code enhancements for the WELCOME10 signup offer:
--   excluded_category_ids   — JSON array of category UUIDs the code may NOT be used on
--                             (WELCOME10 excludes laptops)
--   valid_days_from_signup  — per-user validity window in days from the customer's signup
--                             (WELCOME10 = 7 days), independent of the global expires_at
--
-- Guarded with to_regclass because Flyway runs before Hibernate ddl-auto: on a fresh
-- database promo_codes may not exist yet (Hibernate then creates these columns from the
-- entity); on the existing prod DB this adds them. IF NOT EXISTS keeps it idempotent.
DO $$
BEGIN
    IF to_regclass('public.promo_codes') IS NOT NULL THEN
        ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS excluded_category_ids text;
        ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS valid_days_from_signup integer;
    END IF;
END $$;
