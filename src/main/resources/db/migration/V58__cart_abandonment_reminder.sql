-- Reminders for carts that were filled and left.
--
-- One column, not a table: the only fact worth keeping is when this cart was last reminded about,
-- and the re-arm rule falls out of comparing it with the cart's own updated_at. A shopper who adds
-- something new after a reminder has a cart that is newer than its reminder, and so becomes
-- eligible again — which is what we want, and what a "reminded: true" flag could not express.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database the
-- carts table does not exist yet and Hibernate creates the column from the entity. Mirrors V32,
-- V36, V55, V56 and V57. Idempotent — safe to re-run.
DO $$
BEGIN
    IF to_regclass('public.carts') IS NULL THEN
        RAISE NOTICE 'carts does not exist yet; Hibernate will create this column.';
    ELSE
        ALTER TABLE carts ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMPTZ;

        COMMENT ON COLUMN carts.reminder_sent_at IS
            'When an abandoned-cart reminder was last sent for this cart. Re-arms when updated_at moves past it.';

        -- The sweep asks for ACTIVE carts that have gone quiet, oldest first. Without this it is a
        -- sequential scan of a table that holds a row per customer plus one per Buy Now attempt and
        -- is never pruned.
        CREATE INDEX IF NOT EXISTS idx_carts_reminder_sweep
            ON carts (updated_at)
            WHERE status = 'ACTIVE';
    END IF;

    -- The sweep's EXISTS check on the lines of a candidate cart.
    IF to_regclass('public.cart_items') IS NULL THEN
        RAISE NOTICE 'cart_items does not exist yet; the index is created with the table.';
    ELSE
        CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON cart_items (cart_id);
    END IF;
END $$;
