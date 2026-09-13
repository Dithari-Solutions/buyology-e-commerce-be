-- The number of units of a product we actually have, and the one that is allowed to refuse an order.
--
-- WHY A NEW COLUMN, rather than finally enforcing products.stock_quantity.
--
-- stock_quantity cannot be trusted, and that is not a guess: V12 introduced it as a display hint
-- ("when set and low (< 5) the storefront shows an 'almost sold out' message") and the decrement in
-- createOrder was floored at zero and, by its own comment, "never blocks the order". So it has been
-- counting DOWN past whatever an admin once typed, on every order, since it existed, and nothing has
-- ever put units back. A product that has merely sold well reads 0 today while still being on sale.
-- Enforcing it therefore refused checkout for exactly the best-selling catalogue — that shipped once,
-- broke ordering, and was rolled back to the switch that is still off in application-prod.properties.
--
-- The blocker was never the guard, it was the data. A new column starts with no history: every row is
-- NULL, NULL means "not tracked", and not-tracked behaves exactly as today. So enforcement can be on
-- from the first deploy without refusing a single order, and a product becomes enforced at the moment
-- an admin states its count — which is the only moment we have grounds to enforce anything.
--
-- stock_quantity keeps its existing job as the urgency hint. The two are not merged on purpose: one
-- is a number someone vouches for, the other is a number that has drifted for a year.
--
-- Guarded with to_regclass for the same reason V12 was: Flyway runs before Hibernate ddl-auto, so on
-- a fresh database the products table may not exist yet (Hibernate then creates the column from the
-- entity); on the existing production database this adds it.
--
-- Deliberately NOT backfilled. Turning today's NULLs into 0 would make the entire catalogue
-- unbuyable the moment this deploys, and turning them into stock_quantity would import the very
-- drift this column exists to escape.
DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL THEN
        ALTER TABLE products ADD COLUMN IF NOT EXISTS available_quantity INTEGER;

        -- A count is a count. The order path refuses to take units that are not there, so a negative
        -- value should be impossible — but it was reachable through the API before this (no @Min(0)
        -- on either product request DTO, and the dashboard form is noValidate), and a negative here
        -- would make the product permanently unsellable while reading as "tracked". Enforced in the
        -- schema so no future writer can reintroduce it.
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'products_available_quantity_non_negative'
        ) THEN
            ALTER TABLE products
                ADD CONSTRAINT products_available_quantity_non_negative
                CHECK (available_quantity IS NULL OR available_quantity >= 0);
        END IF;
    END IF;
END $$;
