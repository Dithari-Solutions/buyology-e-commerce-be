-- The B2B membership application now captures a password during the public sign-up
-- flow (no login). The BCrypt hash is stored on the application row and copied onto
-- the member's LOCAL auth credential when an admin approves the application.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh
-- DB the table may not exist yet (Hibernate then creates it with this column). On the
-- existing prod DB (ddl-auto=validate) this adds the column; on dev Hibernate also adds it.
DO $$
BEGIN
    IF to_regclass('public.b2b_membership_applications') IS NOT NULL THEN
        ALTER TABLE b2b_membership_applications ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);
    END IF;
END $$;
