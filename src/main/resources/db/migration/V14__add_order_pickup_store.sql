-- Store pickup: when deliveryMethod == PICKUP the customer collects from a chosen
-- store branch instead of having it delivered. We snapshot the branch onto the order
-- so admins and customers can see exactly where to collect, even if the store later changes.
--
-- Guarded with to_regclass because Flyway runs before Hibernate ddl-auto: on a fresh
-- database the orders table may not exist yet (Hibernate then creates these columns from
-- the entity); on the existing prod DB this adds them. IF NOT EXISTS keeps it idempotent.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS pickup_store_id UUID;
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS pickup_store_name VARCHAR(200);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS pickup_store_address VARCHAR(500);
    END IF;
END $$;
