-- Optional icon key for product categories (shown in the storefront Shop menu).
-- Guarded for the Flyway-before-Hibernate / minimal-schema test (mirrors the V8 pattern);
-- on prod (ddl-auto=validate) this adds the column, on dev Hibernate also adds it.
DO $$
BEGIN
    IF to_regclass('public.product_categories') IS NOT NULL THEN
        ALTER TABLE product_categories ADD COLUMN IF NOT EXISTS icon VARCHAR(50);
    END IF;
END $$;
