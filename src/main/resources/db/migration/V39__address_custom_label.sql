-- The customer's own name for a saved address ("Grandma's place"), shown instead of the fixed
-- HOME/WORK/OTHER label. Set by the storefront when the OTHER label is chosen; nullable, purely
-- cosmetic, no constraint — the enum label column stays authoritative for grouping.
--
-- Needed as a migration because prod runs ddl-auto=validate. Guarded with to_regclass because
-- Flyway runs BEFORE Hibernate: on a fresh database the table does not exist yet and Hibernate
-- creates the column from the entity. Idempotent.
DO $$
BEGIN
    IF to_regclass('public.user_addresses') IS NULL THEN
        RAISE NOTICE 'user_addresses does not exist yet; Hibernate will create the column.';
        RETURN;
    END IF;

    ALTER TABLE user_addresses ADD COLUMN IF NOT EXISTS custom_label VARCHAR(60);
END $$;
