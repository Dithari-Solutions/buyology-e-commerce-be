-- Quiqup dispatch: count create attempts, and record when automatic retries stopped.
--
-- The retry job retried every failed dispatch every five minutes on both replicas until the
-- 48-hour horizon ran out. quiqup.dispatch.max-attempts existed but nothing read it. A payload
-- Quiqup rejected (a 422 on payment_mode, for every cash order) was sent again and again with
-- nothing changed, about 425 times in a day for one order, until Quiqup asked us to stop.
--
-- quiqup_dispatch_attempts caps the retries that can help (Quiqup briefly unavailable).
-- quiqup_dispatch_stopped_at takes an order out of the retry job's worklist when retrying cannot
-- help (Quiqup rejected the job itself) or is not safe: a create that may have gone through without
-- an answer. Quiqup do not deduplicate on partner_order_id, so repeating that create books a second
-- courier for the same parcel. An admin's manual dispatch is the only thing that moves such an
-- order on.
--
-- Existing rows start at NULL, which the code reads as "no attempts and not stopped", so any order
-- stuck before this change gets its first retry once the corrected payload ships.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database the
-- orders table does not exist yet and Hibernate creates these columns from the entity. Mirrors
-- V32 and V36. Idempotent — safe to re-run.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL THEN
        RAISE NOTICE 'orders does not exist yet; Hibernate will create these columns.';
        RETURN;
    END IF;

    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_dispatch_attempts   INTEGER;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_dispatch_stopped_at TIMESTAMPTZ;
END $$;
