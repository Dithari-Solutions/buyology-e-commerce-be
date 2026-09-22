-- Quiqup release: when a dispatched order's job was marked ready for collection, and how many
-- release calls were made.
--
-- Creating a Quiqup job only books it. Quiqup's docs: a new order "will be created in a pending
-- state and will only be visible on your Quiqup Portal, but it is not visible for our dispatching
-- system". No courier comes until the job is marked ready_for_collection. Nothing did that
-- automatically: it was either a setting that released every job the moment it was created
-- (before anyone had packed it) or a button on a testing page that needed the Quiqup job id.
--
-- The job is now released when the order moves to PACKAGING, because that is when the shop has
-- the order in hand. quiqup_released_at records that it happened, so the retry job can catch a
-- release that failed. quiqup_release_attempts caps those retries, and doubles as a
-- compare-and-set claim so the two replicas do not both call Quiqup for the same order.
--
-- Existing rows start NULL, meaning "not released". The retry job only looks at PACKAGING orders
-- updated in the last 48 hours, so a job that was released by hand earlier is at most asked once
-- more, which Quiqup treats as a no-op on a job already in that state.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database the
-- orders table does not exist yet and Hibernate creates these columns from the entity. Mirrors
-- V32, V36 and V56. Idempotent — safe to re-run.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL THEN
        RAISE NOTICE 'orders does not exist yet; Hibernate will create these columns.';
        RETURN;
    END IF;

    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_released_at      TIMESTAMPTZ;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_release_attempts INTEGER;
END $$;
