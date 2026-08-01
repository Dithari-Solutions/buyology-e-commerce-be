-- Customer sell (trade-in) requests: a customer offers Buyology a device and procurement quotes
-- what we'll pay for it. The mirror of repair_requests (V23), with the money flowing the other way:
-- offer_price is what WE pay, and payout_method records how the customer takes it.
--
-- The Hibernate entity (SellRequest) is the source of truth; this migration keeps Flyway-managed
-- schemas in sync, exactly like repair_requests in V23.
--
-- The whole DDL is wrapped in a to_regclass DO-block guard because Flyway runs BEFORE Hibernate:
-- on a fresh DB (and in FlywayBaselineMigrationIT) the auth_credentials table this row references
-- (loosely, no FK) doesn't exist yet — Hibernate creates the schema later from the entities.
-- Guarding on auth_credentials keeps the migration a safe no-op on a fresh DB and creates the
-- table on the existing prod DB. Mirrors V17/V18/V21/V23.
DO $$
BEGIN
    IF to_regclass('public.auth_credentials') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS sell_requests (
          id UUID PRIMARY KEY,
          reference VARCHAR(30),                     -- display ref, e.g. SR-2026-001
          credential_id UUID NOT NULL,               -- owner (sub / auth_credentials.id)
          user_id UUID,                              -- users.id (uid)
          product_name VARCHAR(255) NOT NULL,
          brand VARCHAR(255) NOT NULL,
          model VARCHAR(255) NOT NULL,
          purchase_date DATE,                        -- optional; drives the age multiplier
          device_condition VARCHAR(20) NOT NULL DEFAULT 'GOOD',
                                                     -- LIKE_NEW|GOOD|FAIR|POOR (customer's own grading)
          description TEXT NOT NULL,                  -- what's included, faults, specs
          image_keys TEXT,                           -- up to 4 Contabo keys, newline-delimited
          status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
                                                     -- SUBMITTED|AWAITING_DEVICE|UNDER_REVIEW|
                                                     -- OFFER_MADE|ACCEPTED|COMPLETED|DECLINED|CANCELLED
          inbound_delivery_method VARCHAR(20),       -- COURIER_PICKUP|STORE_DROPOFF
          store_location_id UUID,                    -- chosen store branch (store_locations.id)
          return_delivery_method VARCHAR(20),        -- COURIER_RETURN|STORE_PICKUP (after a decline)
          courier_fee_amount NUMERIC(12,2),          -- 20 AED base, converted to customer currency
          courier_fee_currency VARCHAR(3),
          courier_fee_paid BOOLEAN NOT NULL DEFAULT false,
          offer_price NUMERIC(12,2),                 -- what Buyology pays, set by procurement
          offer_price_currency VARCHAR(3),
          offer_valid_for VARCHAR(120),              -- e.g. "valid for 7 days"
          inspected_condition VARCHAR(20),           -- grade procurement gave it on arrival
          payout_method VARCHAR(20),                 -- STORE_CASH (WALLET_CREDIT reserved, rejected)
          paid_out_at TIMESTAMPTZ,                   -- when the store handed the money over
          ai_estimate_min_price NUMERIC(12,2),       -- advisory AI valuation, always AED
          ai_estimate_max_price NUMERIC(12,2),
          ai_estimate_currency VARCHAR(3),
          ai_estimate_confidence VARCHAR(16),        -- LOW|MEDIUM|HIGH
          ai_estimate_summary TEXT,
          ai_estimate_condition VARCHAR(20),         -- condition the model read off the photos
          ai_estimated_at TIMESTAMPTZ,
          admin_note TEXT,                           -- last admin note / custom update text
          updated_by UUID,                           -- admin users.id (uid) of last update
          contact_email VARCHAR(255),                -- snapshotted from profile at submit
          contact_phone VARCHAR(30),
          admin_unread BOOLEAN NOT NULL DEFAULT true, -- drives the dashboard badge
          customer_unread BOOLEAN NOT NULL DEFAULT false,
          device_received_at TIMESTAMPTZ,
          offered_at TIMESTAMPTZ,
          submitted_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE INDEX IF NOT EXISTS idx_sell_requests_credential ON sell_requests(credential_id);
        CREATE INDEX IF NOT EXISTS idx_sell_requests_status ON sell_requests(status);
        CREATE INDEX IF NOT EXISTS idx_sell_requests_admin_unread ON sell_requests(admin_unread);
        CREATE INDEX IF NOT EXISTS idx_sell_requests_created_at ON sell_requests(created_at DESC);

    END IF;
END $$;

-- Links a standalone Paymob courier-fee charge to a sell request (purpose = SELL_COURIER_FEE).
-- Hibernate-owned (added to PaymentTransaction); this guarded ALTER keeps an existing prod schema
-- in sync. Mirrors V24's repair_id.
DO $$
BEGIN
    IF to_regclass('public.payment_transactions') IS NOT NULL THEN
        ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS sell_request_id UUID;
    END IF;
END $$;
