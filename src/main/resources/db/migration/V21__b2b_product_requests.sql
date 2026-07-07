-- B2B product-sourcing requests: a logged-in ACTIVE B2B member asks Buyology to source a
-- free-form product (name + quantity >= 5, optional image, message to procurement). The
-- Hibernate entity (B2bProductRequest) is the source of truth; this migration keeps
-- Flyway-managed schemas in sync, exactly like the b2b_quotes pair in V18.
--
-- The whole DDL is wrapped in a to_regclass DO-block guard because Flyway runs BEFORE
-- Hibernate ddl-auto: on a fresh DB (and in FlywayBaselineMigrationIT) the auth_credentials
-- table this row references (loosely, no FK) doesn't exist yet — Hibernate creates the schema
-- later from the entities. Guarding on auth_credentials keeps the migration a safe no-op on a
-- fresh DB and creates the table on the existing prod DB. Mirrors V17/V18 and the other patches.
DO $$
BEGIN
    IF to_regclass('public.auth_credentials') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS b2b_product_requests (
          id UUID PRIMARY KEY,
          credential_id UUID NOT NULL,          -- owner (sub / auth_credentials.id)
          user_id UUID,                         -- users.id (uid)
          membership_id UUID,                   -- b2b_memberships.id (active at submit time)
          product_name VARCHAR(255) NOT NULL,   -- free-form
          quantity INTEGER NOT NULL,            -- validated >= 5 (server + client)
          message TEXT,                         -- member's message to procurement
          image_key VARCHAR(1024),              -- Contabo S3 key (presigned URL generated on read)
          status VARCHAR(20) NOT NULL DEFAULT 'NEW', -- NEW|UNDER_REVIEW|IN_PROGRESS|COMPLETED|REJECTED
          procurement_note TEXT,                -- last admin note / custom update text
          updated_by UUID,                      -- admin users.id (uid) of last update
          contact_email VARCHAR(255),           -- denormalized member email for notifications
          submitted_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE INDEX IF NOT EXISTS idx_b2b_product_requests_credential ON b2b_product_requests(credential_id);
        CREATE INDEX IF NOT EXISTS idx_b2b_product_requests_status ON b2b_product_requests(status);
        CREATE INDEX IF NOT EXISTS idx_b2b_product_requests_created_at ON b2b_product_requests(created_at DESC);

    END IF;
END $$;
