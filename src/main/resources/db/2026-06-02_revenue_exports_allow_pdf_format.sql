-- =============================================================================
-- Allow PDF (and any future) revenue export formats
--
-- Hibernate 6 auto-generates CHECK constraints for @Enumerated(STRING) columns
-- from the enum's values AT TABLE-CREATION TIME. `revenue_exports` was first
-- created when RevenueExportFormat only had CSV and XLSX, so the column carries
--     CHECK (format IN ('CSV','XLSX'))
-- (Postgres names inline column checks `<table>_<column>_check`).
--
-- Adding PDF to the enum does NOT update that constraint — ddl-auto=update never
-- alters existing check constraints — so inserting a PDF export row fails with a
-- constraint violation, surfaced by the API as HTTP 409
-- ("A record with the same unique value already exists").
--
-- Fix: drop ALL check constraints on revenue_exports. The enum values are
-- validated in Java, so the DB-level check is redundant; removing it means new
-- export formats/periods never require another migration.
--
-- PostgreSQL. Idempotent: safe to run more than once. Run once before/after
-- deploying the build that adds PDF export.
-- =============================================================================

DO $$
DECLARE
    c record;
BEGIN
    IF to_regclass('public.revenue_exports') IS NULL THEN
        RAISE NOTICE 'revenue_exports does not exist yet; nothing to do.';
        RETURN;
    END IF;

    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.revenue_exports'::regclass
          AND contype = 'c'
    LOOP
        EXECUTE 'ALTER TABLE revenue_exports DROP CONSTRAINT ' || quote_ident(c.conname);
        RAISE NOTICE 'Dropped check constraint % on revenue_exports', c.conname;
    END LOOP;
END $$;
