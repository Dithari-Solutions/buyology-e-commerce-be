-- Courier return-pickup fee, paid separately by the customer via Paymob.
--
-- The fee is a standalone charge (NOT tied to an order) that the customer pays
-- right after requesting a courier pickup for a refund return. It is recorded as
-- a normal payment_transactions row tagged with purpose = 'COURIER_RETURN_FEE'
-- and linked back to the refund request, so it surfaces as delivery-fee revenue
-- without a dedicated table.
--
-- Guarded with to_regclass because Flyway runs before Hibernate ddl-auto: on a fresh
-- database payment_transactions may not exist yet (Hibernate then creates these columns
-- from the entity); on the existing prod DB this adds them. IF NOT EXISTS keeps it idempotent.
DO $$
BEGIN
    IF to_regclass('public.payment_transactions') IS NOT NULL THEN
        ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS purpose VARCHAR(30) NOT NULL DEFAULT 'ORDER';
        ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS refund_request_id UUID;
        CREATE INDEX IF NOT EXISTS idx_payment_transactions_purpose ON payment_transactions(purpose);
        CREATE INDEX IF NOT EXISTS idx_payment_transactions_refund_request ON payment_transactions(refund_request_id);
    END IF;
END $$;
