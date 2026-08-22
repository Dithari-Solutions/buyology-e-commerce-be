-- One row per settled payment that could not be applied to its order.
--
-- Before this table, such a payment vanished: onPaymentSucceeded had no else-branch, so a payment
-- settling after its order was cancelled — money captured, card charged — fell out of the lambda
-- with no log, no record and no refund, and reconcileStuckPayments scanned only PENDING_PAYMENT so
-- it never saw it either. This table is the durable evidence, the admin review queue, and the
-- idempotency key for the auto-refund of the two unambiguous kinds.
--
-- The unique index on payment_transaction_id is not tidiness: it is what makes detection
-- exactly-once across two replicas with no ShedLock, and the reason an auto-refund can be
-- attempted at most once per payment.
--
-- NO foreign keys, by design. A REQUIRES_NEW insert here happens while the order listener's
-- transaction holds writes on payment_transactions and orders; an FK would make PostgreSQL take
-- FOR KEY SHARE on those parents — the lock pattern that hung every refund in this repo once
-- already. Plain UUID columns match the convention payment_transactions.app_order_id already uses
-- across the same boundary.
--
-- NO CHECK constraints on kind/resolution: Hibernate emits one for @Enumerated(STRING) at
-- table-creation time and never revisits it (V33, V34); the entity stores plain VARCHAR so a
-- future kind needs no migration.
--
-- Numbered V38: V35-V37 ship in the same batch. Guarded on `orders` like V30 — Flyway runs BEFORE
-- Hibernate, so on a fresh database this is a no-op and Hibernate creates the table from the
-- entity. Idempotent.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS payment_anomalies (
          id UUID PRIMARY KEY,
          payment_transaction_id UUID NOT NULL,
          app_order_id UUID,
          kind VARCHAR(40) NOT NULL,
          resolution VARCHAR(40) NOT NULL DEFAULT 'OPEN',
          order_status VARCHAR(40),
          amount NUMERIC(12,2) NOT NULL,
          currency VARCHAR(3) NOT NULL,
          detail TEXT,
          detected_by VARCHAR(20) NOT NULL,
          attempts INTEGER NOT NULL DEFAULT 0,
          refund_id UUID,
          resolution_note TEXT,
          resolved_by UUID,
          resolved_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_anomalies_tx
            ON payment_anomalies (payment_transaction_id);
        CREATE INDEX IF NOT EXISTS idx_payment_anomalies_resolution
            ON payment_anomalies (resolution, created_at);
        CREATE INDEX IF NOT EXISTS idx_payment_anomalies_order
            ON payment_anomalies (app_order_id);

    END IF;
END $$;
