-- Reminders for carts that were filled and left.
--
-- One column, not a table: the only fact worth keeping is when this cart was last reminded about,
-- and the re-arm rule falls out of comparing it with the cart's own updated_at. A shopper who adds
-- something new after a reminder has a cart that is newer than its reminder, and so becomes
-- eligible again — which is what we want, and what a "reminded: true" flag could not express.
ALTER TABLE carts ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMPTZ;

COMMENT ON COLUMN carts.reminder_sent_at IS
    'When an abandoned-cart reminder was last sent for this cart. Re-arms when updated_at moves past it.';

-- The sweep asks for ACTIVE carts that have gone quiet, oldest first. Without this it is a
-- sequential scan of a table that holds a row per customer plus one per Buy Now attempt and is
-- never pruned.
CREATE INDEX IF NOT EXISTS idx_carts_reminder_sweep
    ON carts (updated_at)
    WHERE status = 'ACTIVE';

-- The sweep's EXISTS check on the lines of a candidate cart.
CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON cart_items (cart_id);
