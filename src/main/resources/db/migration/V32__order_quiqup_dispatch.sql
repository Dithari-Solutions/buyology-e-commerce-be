-- Quiqup delivery dispatch: what was sent to Quiqup for each PAID order, and how it went.
--
-- QuiqupDispatchService stamps quiqup_order_id once Quiqup accepts a job, which is what makes a
-- retry idempotent (an order that already carries an id is skipped) and lets the admin page show
-- per-order dispatch state. A failure records quiqup_dispatch_error and leaves the order's own
-- status untouched — the customer has paid and the order is valid, it simply has no courier yet.
--
-- quiqup_order_id is deliberately a separate column from delivery_order_id: that one is a UUID and
-- belongs to our own courier service, while Quiqup issues an opaque string id of its own.
-- Overloading a single column would make "which carrier is holding this parcel" unanswerable.
--
-- Numbered V32, not V31: the assistant module on the phase-2 branch already owns V31, and two
-- migrations sharing a version makes Flyway refuse to start the moment those branches meet. A gap
-- in the sequence is harmless by comparison — Flyway applies what it finds, in order.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database (and
-- in FlywayBaselineMigrationIT) the orders table does not exist yet and Hibernate creates these
-- columns from the entity instead; on the existing prod DB this adds them. Mirrors V8/V17/V18/V26.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_order_id       VARCHAR(100);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_status         VARCHAR(60);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_dispatched_at  TIMESTAMPTZ;
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_dispatch_error VARCHAR(1000);

        -- The retry job's lookup: paid orders that Quiqup has not accepted yet. Partial, because
        -- the rows it needs are the rare ones — every successfully dispatched order is excluded.
        CREATE INDEX IF NOT EXISTS idx_orders_quiqup_undispatched
            ON orders(created_at) WHERE quiqup_order_id IS NULL;

        -- Resolving an inbound webhook back to our order.
        CREATE INDEX IF NOT EXISTS idx_orders_quiqup_order_id ON orders(quiqup_order_id);
    END IF;
END $$;
