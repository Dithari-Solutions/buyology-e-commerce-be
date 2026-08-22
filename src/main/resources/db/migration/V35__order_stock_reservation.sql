-- =============================================================================
-- Stock taken at order creation, and whether it was ever given back
--
-- OrderService.createOrder decrements a store listing's stock the moment an order is built
-- in PENDING_PAYMENT, and nothing ever incremented it back — not on a declined card, not
-- on a customer, admin or partner cancellation, not on createOrder's own cancellation of a
-- stale prior order. Three declined attempts on a five-unit variant left it at two with
-- nothing sold, and the shop then refused sales for inventory sitting on the shelf.
--
-- These two columns are the record of what was taken and what was returned, and they are
-- the reason a return cannot happen twice.
--
-- Deliberately timestamps, not an @Enumerated(STRING) status column: Hibernate 6 writes a
-- CHECK from an enum's values at TABLE-CREATION time and never revisits it, so adding a
-- value later fails in production while passing in Java. That has bitten this repo twice
-- already (V33, V34). Timestamps carry no CHECK, so nothing here needs one dropped.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate: on a fresh database the
-- orders table does not exist yet and Hibernate creates these columns from the entity.
-- Idempotent — safe to re-run.
-- =============================================================================

DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL THEN
        RAISE NOTICE 'orders does not exist yet; Hibernate will create these columns.';
        RETURN;
    END IF;

    ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_reserved_at TIMESTAMPTZ;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_restored_at TIMESTAMPTZ;

    -- (1) Which historical orders actually hold stock.
    --
    -- Every order built from a real cart went through createOrder and decremented. B2B quote
    -- orders never did — buildOrderFromQuote bypasses createOrder entirely.
    --
    -- They cannot be told apart by a null cart_id, which is the obvious-looking test and is
    -- wrong: orders.cart_id is NOT NULL, and B2bQuoteService stores the QUOTE's id in it
    -- (B2bQuoteService.java:866). So the discriminator is whether that id actually names a
    -- row in carts. Getting this wrong in the permissive direction would stamp B2B orders as
    -- holding stock they never took, and the first cancellation would then invent inventory.
    UPDATE orders o
       SET stock_reserved_at = o.created_at
     WHERE o.stock_reserved_at IS NULL
       AND EXISTS (SELECT 1 FROM carts c WHERE c.id = o.cart_id);

    -- (2) Declare every already-terminal order settled.
    --
    -- These are the orders whose units leaked. We do NOT put them back: there is no way to
    -- tell from the database which shortfalls an admin has already corrected by hand in the
    -- dashboard, and adding on top of a hand-correction invents inventory that does not
    -- exist — the one failure worse than the leak being fixed here. Stamping them restored
    -- means the new code treats them as done and never revisits them.
    --
    -- The real shortfall stays until someone recounts, which understates availability rather
    -- than overstating it. The diagnostic query in the migration notes below sizes it.
    UPDATE orders
       SET stock_restored_at = COALESCE(cancelled_at, updated_at, created_at)
     WHERE stock_restored_at IS NULL
       AND stock_reserved_at IS NOT NULL
       AND status IN ('CANCELLED', 'FAILED');
END $$;

-- -----------------------------------------------------------------------------
-- For ops, not run here. How much stock is understated by the historical leak:
--
--   SELECT oi.store_id, oi.product_id, oi.variant_id, SUM(oi.quantity) AS units_owed
--     FROM order_items oi
--     JOIN orders o ON o.id = oi.order_id
--    WHERE o.status IN ('CANCELLED','FAILED')
--      AND o.cart_id IS NOT NULL
--      AND oi.variant_id IS NOT NULL
--      AND oi.store_id IS NOT NULL
--    GROUP BY 1,2,3
--    ORDER BY units_owed DESC;
--
-- Reconcile against a physical count before adding any of it back.
-- -----------------------------------------------------------------------------
