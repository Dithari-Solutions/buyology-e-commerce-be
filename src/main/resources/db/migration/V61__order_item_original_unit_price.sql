-- What the customer was TOLD they were saving, kept on the order line.
--
-- order_items is an immutable snapshot: the SKU, the quantity and the unit price are copied at
-- checkout precisely so later catalogue edits cannot rewrite history. The one figure that was never
-- copied is the struck-through "was" price the basket showed. cart_items has carried it for
-- strike-through display since discounts existed; it died at the order boundary.
--
-- That gap stopped mattering while a discount was permanent — the listing still said 25% off, so the
-- saving could be reconstructed from the product. V60 ended that: a discount now has an END, so the
-- sale a January order was placed under may not exist by February and cannot be looked up from the
-- listing at all. The cart and the checkout also re-price a line against the live window, so which
-- advertised price a given order actually honoured is a fact about the moment it was placed.
--
-- So it is stamped rather than recomputed. NULLABLE, and null is meaningful: it means no discount was
-- advertised on that line, which is a different claim from a saving of zero. No backfill — every
-- existing row predates the column, and inventing a "was" price for a historical order would be
-- fabricating the one thing this column exists to evidence.
--
-- Same to_regclass DO-block guard as V42/V43/V45/V52/V53/V59/V60: Flyway runs BEFORE Hibernate
-- ddl-auto, so on a fresh database this is a safe no-op (Hibernate creates the column from the
-- entity) while on the existing prod database it adds it.
DO $$
BEGIN
    IF to_regclass('public.order_items') IS NOT NULL THEN
        ALTER TABLE order_items
            ADD COLUMN IF NOT EXISTS original_unit_price NUMERIC(12, 2);
    END IF;
END $$;
