-- Cash on delivery: how an order is paid for, and whether the cash ever arrived.
--
-- Every order before this was prepaid — nothing was picked, packed or dispatched until a payment
-- reached SUCCESS, and orders.paid_at was therefore both "the money is in" and "fulfilment may
-- start". Cash on delivery breaks that equivalence: the goods go out first and the money is handed
-- over at the door or the counter, so a cash order travels its whole fulfilment journey unpaid and
-- is never in status PAID at all.
--
-- Hence two separate facts rather than one:
--
--   payment_method       ONLINE (everything that existed before) or CASH_ON_DELIVERY. The only
--                        thing that lets an order leave PENDING_PAYMENT for PACKAGING without a
--                        payment, so the status machine, the cancellation path and the courier
--                        payload all read it.
--
--   cod_collected_at     when an admin recorded the cash as in hand, with the amount and who said
--                        so. NULL on a cash order means the platform is still owed the money, and
--                        nothing may be refunded against it — Order.isMoneyCollected() is the one
--                        place that asks.
--
-- Existing rows are left NULL on payment_method and read as ONLINE by the entity, which is exactly
-- what all of them are. No backfill: NULL and ONLINE mean the same thing here, and writing 200k
-- rows to say so buys nothing.
--
-- Same to_regclass DO-block guard as V42/V43/V45/V52: Flyway runs BEFORE Hibernate ddl-auto, so on
-- a fresh DB this is a safe no-op (Hibernate creates the columns from the entity) while on the
-- existing prod DB it adds them.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders
            ADD COLUMN IF NOT EXISTS payment_method       VARCHAR(30),
            ADD COLUMN IF NOT EXISTS cod_collected_at     TIMESTAMPTZ,
            ADD COLUMN IF NOT EXISTS cod_collected_amount NUMERIC(12,2),
            ADD COLUMN IF NOT EXISTS cod_collected_by     UUID;

        -- The operational question this table will be asked every day: which cash orders are still
        -- owed money. Partial, because it is only ever asked about cash orders.
        CREATE INDEX IF NOT EXISTS idx_orders_cod_uncollected
            ON orders (created_at DESC)
            WHERE payment_method = 'CASH_ON_DELIVERY' AND cod_collected_at IS NULL;
    END IF;
END $$;
