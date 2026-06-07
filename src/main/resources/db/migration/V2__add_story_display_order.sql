-- Story display order: admin-controllable ordering for the stories feed.
-- Flyway runs before Hibernate ddl-auto, so on a brand-new database the table may
-- not exist yet — the to_regclass guard makes this a no-op in that case (Hibernate
-- then creates the column from the entity). On an existing DB it adds the column.
DO $$
BEGIN
    IF to_regclass('public.stories') IS NOT NULL THEN
        ALTER TABLE stories ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;
    END IF;
END $$;
