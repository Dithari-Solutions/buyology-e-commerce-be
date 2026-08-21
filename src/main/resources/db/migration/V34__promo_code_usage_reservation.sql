-- =============================================================================
-- Hold a promo code from the moment an order claims it, not from the moment it is paid
--
-- Redemptions were only ever written once an order reached PAID, and every limit check
-- counts those rows. So between placing an order and paying for it, a single-use code
-- looked untouched: a customer could place ten orders against the same code, pay all
-- ten, and every one of them kept its discount. Nothing rejected it — the unique
-- constraint on (promo_code_id, user_id, order_id) is per order, so ten orders meant
-- ten perfectly valid rows — and the loss scales with the code's value. Personal
-- token-redemption codes are the worst case: one earned code, spent as many times as
-- the customer cares to check out.
--
-- The row is now written when the order is created, as RESERVED, and flipped to
-- REDEEMED on payment. Existing rows are all genuine redemptions of paid orders, which
-- is exactly what the default backfills them to.
--
-- The column is added here rather than left to ddl-auto because production runs
-- ddl-auto=validate: without this migration the application does not start.
--
-- PostgreSQL. Idempotent — safe on a fresh database and safe to re-run.
-- =============================================================================

DO $$
BEGIN
    IF to_regclass('public.promo_code_usages') IS NULL THEN
        RAISE NOTICE 'promo_code_usages does not exist yet; Hibernate will create it with the column.';
        RETURN;
    END IF;

    ALTER TABLE promo_code_usages
        ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'REDEEMED';
END $$;

-- Hibernate 6 generates a CHECK from an @Enumerated(STRING) column's values at
-- table-creation time and never revisits it, so a status added later fails at the
-- database while passing in Java. The values are validated on the way in; the database
-- copy is only a second place to forget. Same remedy as V33.
DO $$
DECLARE
    c record;
BEGIN
    IF to_regclass('public.promo_code_usages') IS NULL THEN
        RETURN;
    END IF;

    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.promo_code_usages'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE 'ALTER TABLE promo_code_usages DROP CONSTRAINT ' || quote_ident(c.conname);
        RAISE NOTICE 'Dropped status check constraint % on promo_code_usages', c.conname;
    END LOOP;
END $$;

-- The reservation exists to be counted, so the count must be cheap.
--
-- Guarded like everything above: Flyway runs BEFORE Hibernate, so on a fresh database
-- neither the table nor the column exists yet and an unguarded CREATE INDEX here would
-- fail the migration and stop the application from starting. Hibernate creates this
-- index itself from the entity's @Index in that case.
DO $$
BEGIN
    IF to_regclass('public.promo_code_usages') IS NULL THEN
        RETURN;
    END IF;

    CREATE INDEX IF NOT EXISTS idx_pcu_promo_status
        ON promo_code_usages (promo_code_id, status);
END $$;
