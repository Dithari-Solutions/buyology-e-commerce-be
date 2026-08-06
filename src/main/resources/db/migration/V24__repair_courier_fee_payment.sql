-- Repair courier-fee payment: link a standalone Paymob courier-fee charge to a repair request,
-- and track whether the customer has paid the fee for the selected courier leg.
--
-- Both columns are Hibernate-owned (added to the entities); ddl-auto=update creates them and
-- these guarded ALTERs keep the existing prod schema in sync. Each ALTER is wrapped in a
-- to_regclass guard because Flyway runs BEFORE Hibernate — on a fresh DB the tables don't exist
-- yet (Hibernate creates them later). Mirrors V21/V23.
DO $$
BEGIN
    IF to_regclass('public.payment_transactions') IS NOT NULL THEN
        ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS repair_id UUID;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.repair_requests') IS NOT NULL THEN
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS courier_fee_paid BOOLEAN NOT NULL DEFAULT false;
    END IF;
END $$;
