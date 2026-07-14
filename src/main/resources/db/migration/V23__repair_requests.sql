-- Customer device-repair requests: any logged-in customer asks Buyology to repair a device.
-- The Hibernate entity (RepairRequest) is the source of truth; this migration keeps
-- Flyway-managed schemas in sync, exactly like the b2b_product_requests table in V21.
--
-- The whole DDL is wrapped in a to_regclass DO-block guard because Flyway runs BEFORE
-- Hibernate ddl-auto: on a fresh DB (and in FlywayBaselineMigrationIT) the auth_credentials
-- table this row references (loosely, no FK) doesn't exist yet — Hibernate creates the schema
-- later from the entities. Guarding on auth_credentials keeps the migration a safe no-op on a
-- fresh DB and creates the table on the existing prod DB. Mirrors V17/V18/V21.
DO $$
BEGIN
    IF to_regclass('public.auth_credentials') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS repair_requests (
          id UUID PRIMARY KEY,
          reference VARCHAR(30),                     -- display ref, e.g. RR-2026-001
          credential_id UUID NOT NULL,               -- owner (sub / auth_credentials.id)
          user_id UUID,                              -- users.id (uid)
          product_name VARCHAR(255) NOT NULL,
          brand VARCHAR(255) NOT NULL,
          model VARCHAR(255) NOT NULL,
          purchase_date DATE,                        -- optional
          description TEXT NOT NULL,                  -- problem details
          image_keys TEXT,                           -- up to 4 Contabo keys, newline-delimited
          status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
                                                     -- SUBMITTED|AWAITING_DEVICE|UNDER_REVIEW|
                                                     -- PRICE_ESTIMATED|IN_REPAIR|COMPLETED|DECLINED|CANCELLED
          inbound_delivery_method VARCHAR(20),       -- COURIER_PICKUP|STORE_DROPOFF
          store_location_id UUID,                    -- chosen store branch (store_locations.id)
          return_delivery_method VARCHAR(20),        -- COURIER_RETURN|STORE_PICKUP (after a decline)
          courier_fee_amount NUMERIC(12,2),          -- 20 AED base, converted to customer currency
          courier_fee_currency VARCHAR(3),
          estimated_price NUMERIC(12,2),             -- fixing price set by the team
          estimated_price_currency VARCHAR(3),
          estimated_time VARCHAR(120),               -- e.g. "3-5 business days"
          admin_note TEXT,                           -- last admin note / custom update text
          updated_by UUID,                           -- admin users.id (uid) of last update
          contact_email VARCHAR(255),                -- snapshotted from profile at submit
          contact_phone VARCHAR(30),
          admin_unread BOOLEAN NOT NULL DEFAULT true, -- drives the dashboard badge
          customer_unread BOOLEAN NOT NULL DEFAULT false,
          device_received_at TIMESTAMPTZ,
          priced_at TIMESTAMPTZ,
          submitted_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE INDEX IF NOT EXISTS idx_repair_requests_credential ON repair_requests(credential_id);
        CREATE INDEX IF NOT EXISTS idx_repair_requests_status ON repair_requests(status);
        CREATE INDEX IF NOT EXISTS idx_repair_requests_admin_unread ON repair_requests(admin_unread);
        CREATE INDEX IF NOT EXISTS idx_repair_requests_created_at ON repair_requests(created_at DESC);

    END IF;
END $$;
