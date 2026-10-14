-- Rotate the settlement sweep through attempts instead of starving older paid orders.
-- Flyway precedes Hibernate table creation on a fresh installation.
DO $$
BEGIN
    IF to_regclass('public.payment_transactions') IS NOT NULL THEN
        ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS last_gateway_check_at TIMESTAMPTZ;
    END IF;
END $$;
