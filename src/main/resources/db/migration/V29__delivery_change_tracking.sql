-- Let a customer change how their device reaches us, right up until it's with our team, and make
-- that change visible to the repair / procurement teams.
--
-- Previously the inbound method could only be set once (chooseDelivery required status=SUBMITTED),
-- so a customer who picked courier pickup and then decided to drop the device off had no way back —
-- meanwhile the detail page kept re-showing the picker as if nothing had been chosen. The service
-- now accepts a change while SUBMITTED or AWAITING_DEVICE, and records what it was before so the
-- team doesn't dispatch a courier for a device the customer is now bringing in themselves.
--
-- courier_fee_refund_due covers the one case where a change costs us money: the customer PAID for a
-- pickup and then switched to a free drop-off. Nothing refunds automatically — the flag surfaces it
-- in the dashboard so a human settles it.
--
-- All three columns are Hibernate-owned (added to RepairRequest / SellRequest); ddl-auto=update
-- creates them on a fresh DB and these guarded ALTERs keep an existing schema in sync. The
-- to_regclass guards are required because Flyway runs BEFORE Hibernate. Mirrors V23/V24/V28.
DO $$
BEGIN
    IF to_regclass('public.repair_requests') IS NOT NULL THEN
        ALTER TABLE repair_requests
            ADD COLUMN IF NOT EXISTS courier_fee_refund_due BOOLEAN NOT NULL DEFAULT false;
        ALTER TABLE repair_requests
            ADD COLUMN IF NOT EXISTS previous_inbound_delivery_method VARCHAR(20);
        ALTER TABLE repair_requests
            ADD COLUMN IF NOT EXISTS inbound_delivery_changed_at TIMESTAMPTZ;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.sell_requests') IS NOT NULL THEN
        ALTER TABLE sell_requests
            ADD COLUMN IF NOT EXISTS courier_fee_refund_due BOOLEAN NOT NULL DEFAULT false;
        ALTER TABLE sell_requests
            ADD COLUMN IF NOT EXISTS previous_inbound_delivery_method VARCHAR(20);
        ALTER TABLE sell_requests
            ADD COLUMN IF NOT EXISTS inbound_delivery_changed_at TIMESTAMPTZ;
    END IF;
END $$;
