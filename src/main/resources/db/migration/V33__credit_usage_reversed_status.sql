-- =============================================================================
-- Let a credit usage be marked REVERSED
--
-- Cancelling a B2B order now returns the wallet credit it consumed and stamps the
-- credit_usages row REVERSED (see CreditReturnService). Without this migration that
-- UPDATE fails in production and the member never gets their credit back.
--
-- Why it fails: credit_usages is Hibernate-owned, and Hibernate 6 generates a CHECK
-- constraint for @Enumerated(STRING) columns from the enum's values AT TABLE-CREATION
-- TIME. The table was created when CreditUsage.Status held only OUTSTANDING, PARTIAL,
-- PAID and OVERDUE, so the column carries
--     CHECK (status IN ('OUTSTANDING','PARTIAL','PAID','OVERDUE'))
-- and ddl-auto never alters an existing check constraint — under prod's
-- ddl-auto=validate it does not even try. Adding a value to the Java enum therefore
-- changes nothing in the database, and the first REVERSED write raises a constraint
-- violation. The credit return catches and logs its failures so a cancellation can
-- never be blocked by one, which is right — and which is also why this would have gone
-- unnoticed as a line in a log while members kept being chased for cancelled orders.
--
-- Same class of bug, same remedy as db/2026-06-02_revenue_exports_allow_pdf_format.sql:
-- drop the check. The values are an enum in Java, validated on the way in, so the
-- database-level copy adds nothing but a second place to forget.
--
-- Deliberately narrow: only constraints whose definition mentions the status column are
-- touched, so anything else guarding this table survives.
--
-- PostgreSQL. Idempotent — safe on a fresh database (the table may not exist yet, since
-- Flyway runs before Hibernate creates it) and safe to re-run.
-- =============================================================================

DO $$
DECLARE
    c record;
BEGIN
    IF to_regclass('public.credit_usages') IS NULL THEN
        RAISE NOTICE 'credit_usages does not exist yet; Hibernate will create it with the current enum values.';
        RETURN;
    END IF;

    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.credit_usages'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE 'ALTER TABLE credit_usages DROP CONSTRAINT ' || quote_ident(c.conname);
        RAISE NOTICE 'Dropped status check constraint % on credit_usages', c.conname;
    END LOOP;
END $$;
