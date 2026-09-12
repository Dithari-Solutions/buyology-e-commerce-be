-- VAT charged on top of the goods and delivery.
--
-- Stored as its own figure rather than folded into total_amount, because a tax that only exists
-- inside a total is a tax nobody can account for afterwards — it has to be shown as its own line
-- to the customer and reported as its own number to the business.
--
-- vat_rate_percent is snapshotted per order rather than read from configuration at display time.
-- Rates change; a two-year-old order must still say what it was actually taxed at, and recomputing
-- an old order at today's rate would quietly restate history.
--
-- Existing rows get 0, which is correct: they were charged no VAT, and their total_amount already
-- reflects exactly what the customer paid. vat_rate_percent stays NULL for them — "no rate applied"
-- rather than "taxed at zero percent", which are different statements about an order.
--
-- Same to_regclass DO-block guard as V42/V45/V52/V53.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders
            ADD COLUMN IF NOT EXISTS vat_amount       NUMERIC(12,2) NOT NULL DEFAULT 0,
            ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(5,2);
    END IF;
END $$;
