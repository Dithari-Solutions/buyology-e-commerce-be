-- Cart line items keep the pre-discount unit price (nullable) so the cart can show
-- the original (struck-through) price alongside the discounted price actually charged.
-- Guarded with to_regclass because Flyway runs before Hibernate creates the table on
-- a fresh database (Hibernate then adds the column from the entity).
DO $$
BEGIN
    IF to_regclass('public.cart_items') IS NOT NULL THEN
        ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS original_unit_price NUMERIC(12, 2);
    END IF;
END $$;
