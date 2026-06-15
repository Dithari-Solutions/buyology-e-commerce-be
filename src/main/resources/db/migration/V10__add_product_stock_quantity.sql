-- Admin-managed stock quantity for products. When set and low (< 5) the storefront
-- shows an "almost sold out" urgency message; it is decremented as orders are placed.
-- Nullable: NULL means stock isn't tracked for that product (no urgency shown).
-- Guarded with to_regclass because Flyway runs before Hibernate ddl-auto: on a fresh
-- database the products table may not exist yet (Hibernate then creates the column from
-- the entity); on the existing prod DB this adds the column.
DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL THEN
        ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_quantity INTEGER;
    END IF;
END $$;
