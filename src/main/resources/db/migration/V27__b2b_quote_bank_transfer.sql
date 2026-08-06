-- Bank-transfer payment + proof-of-payment for B2B quotes, plus per-line lead time /
-- description and quote-level payment terms / T&C captured by procurement at pricing.
--
-- b2b_quotes / b2b_quote_items are Hibernate-owned (ddl-auto=update as a no-op safety
-- net). Guarded with to_regclass because Flyway runs BEFORE Hibernate on a fresh DB
-- (FlywayBaselineMigrationIT), where these tables do not yet exist.

DO $$
BEGIN
    IF to_regclass('public.b2b_quotes') IS NOT NULL THEN
        -- The new AWAITING_PAYMENT_VERIFICATION status is 29 chars — widen the column.
        ALTER TABLE b2b_quotes ALTER COLUMN status TYPE VARCHAR(40);

        ALTER TABLE b2b_quotes ADD COLUMN IF NOT EXISTS payment_method       VARCHAR(20);
        ALTER TABLE b2b_quotes ADD COLUMN IF NOT EXISTS proof_of_payment_key VARCHAR(500);
        ALTER TABLE b2b_quotes ADD COLUMN IF NOT EXISTS proof_uploaded_at    TIMESTAMPTZ;
        ALTER TABLE b2b_quotes ADD COLUMN IF NOT EXISTS payment_verified_at  TIMESTAMPTZ;
        ALTER TABLE b2b_quotes ADD COLUMN IF NOT EXISTS payment_verified_by  UUID;
        ALTER TABLE b2b_quotes ADD COLUMN IF NOT EXISTS payment_terms        TEXT;
        ALTER TABLE b2b_quotes ADD COLUMN IF NOT EXISTS terms_and_conditions TEXT;
    END IF;

    IF to_regclass('public.b2b_quote_items') IS NOT NULL THEN
        ALTER TABLE b2b_quote_items ADD COLUMN IF NOT EXISTS lead_time   VARCHAR(255);
        ALTER TABLE b2b_quote_items ADD COLUMN IF NOT EXISTS description TEXT;
    END IF;
END $$;
