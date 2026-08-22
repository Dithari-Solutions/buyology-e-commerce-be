-- Quiqup delivery cancel: whether the job carrying a cancelled order's parcel was actually stopped.
--
-- Cancelling a dispatched order used to refund the customer, return their B2B credit and email
-- them — and never told Quiqup. The courier collected and delivered anyway: the customer kept the
-- goods and the money, and the later "delivered" webhook was dropped because the order was already
-- terminal. These columns are the durable record of the cancel leg: the intent (stamped in the
-- same transaction that cancels the order, so a crash between commit and the outbound call still
-- leaves a row the retry job finds), the outcome, the attempt count, and the claim marker that
-- keeps two replicas from both calling Quiqup about the same order.
--
-- quiqup_cancel_status is deliberately VARCHAR, not an enum-backed CHECK: Hibernate 6 writes a
-- CHECK from an @Enumerated(STRING) column's values at table-creation time and never revisits it,
-- so the first added value fails in production under ddl-auto=validate. Bitten twice (V33, V34).
--
-- cancel_refund_initiated_at is a short-circuit and an audit trail, NOT the double-refund guard —
-- that stays RefundClaimStore, which counts SUCCESS and PENDING refunds before any HTTP reaches
-- Paymob.
--
-- Numbered V36: V35 belongs to the stock-reservation columns shipped in the same batch.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database the
-- orders table does not exist yet and Hibernate creates these columns from the entity. Mirrors
-- V32. Idempotent — safe to re-run.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL THEN
        RAISE NOTICE 'orders does not exist yet; Hibernate will create these columns.';
        RETURN;
    END IF;

    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_cancel_status       VARCHAR(24);
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_cancel_requested_at TIMESTAMPTZ;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_cancel_confirmed_at TIMESTAMPTZ;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_cancel_error        VARCHAR(1000);
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_cancel_attempts     INTEGER;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS quiqup_cancel_claimed_at   TIMESTAMPTZ;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancel_refund_initiated_at TIMESTAMPTZ;
END $$;

-- The retry job's worklist: cancelled orders whose job is not confirmed stopped. Partial, so the
-- index stays tiny — almost every order is not in this state.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL THEN
        RETURN;
    END IF;

    CREATE INDEX IF NOT EXISTS idx_orders_quiqup_cancel_pending
        ON orders (cancelled_at)
        WHERE quiqup_cancel_status IN ('PENDING', 'UNCONFIRMED');
END $$;
