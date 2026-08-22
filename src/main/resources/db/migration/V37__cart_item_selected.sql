-- Whether a cart line is part of the order the shopper is about to place.
--
-- The cart page has had per-item checkboxes for months — in Redux only. Nothing about the
-- selection ever reached the server, so an unticked item was still priced, stock-decremented,
-- charged and shipped, while the cart page showed a subtotal, a total and an item count that all
-- excluded it. The checkbox was a cosmetic control over real money.
--
-- selected=true default: every existing row and every client that does not know about selection
-- behaves exactly as before. With the column in place, cart.total_price becomes the SELECTED
-- subtotal everywhere at once — the promo validator, the free-shipping threshold, the delivery-fee
-- preview, the order subtotal and the charged amount all read the same number.
--
-- Numbered V37: V35 (stock reservation) and V36 (quiqup cancel) ship in the same batch.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database the
-- table does not exist yet and Hibernate creates the column from the entity. Idempotent.
DO $$
BEGIN
    IF to_regclass('public.cart_items') IS NULL THEN
        RAISE NOTICE 'cart_items does not exist yet; Hibernate will create the column.';
        RETURN;
    END IF;

    ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS selected BOOLEAN NOT NULL DEFAULT true;
END $$;
