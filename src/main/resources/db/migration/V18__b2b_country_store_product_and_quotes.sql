-- B2B country toggle + channel flags on store products + RFQ (Request-for-Quote) tables.
--
-- Tri-state region control: Country.b2b_enabled joins the existing is_active flag so a
-- region can be B2C-only / B2B-only / Both / Inactive. Store-product assignment gains two
-- independent channel flags (b2c_enabled default TRUE preserves current behaviour, b2b_enabled
-- default FALSE keeps products out of the B2B catalog until explicitly opted in).
--
-- Guarded DDL (IF NOT EXISTS) so this is safe even though Hibernate ddl-auto=update may have
-- already added the entity columns/tables at startup.

ALTER TABLE countries       ADD COLUMN IF NOT EXISTS b2b_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE store_products  ADD COLUMN IF NOT EXISTS b2c_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE store_products  ADD COLUMN IF NOT EXISTS b2b_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS b2b_quotes (
  id UUID PRIMARY KEY,
  credential_id UUID NOT NULL,          -- owner (sub / auth_credentials.id)
  user_id UUID,                         -- users.id (uid)
  membership_id UUID,                   -- b2b_memberships.id
  country_code VARCHAR(3) NOT NULL,     -- alpha-3
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT|SUBMITTED|QUOTED|ACCEPTED|REJECTED|EXPIRED|CANCELLED|ORDERED
  member_note TEXT,
  procurement_note TEXT,
  submitted_at TIMESTAMPTZ,
  quoted_at TIMESTAMPTZ,
  quoted_by UUID,
  valid_until TIMESTAMPTZ,
  accepted_at TIMESTAMPTZ,
  order_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_b2b_quotes_credential ON b2b_quotes(credential_id);
CREATE INDEX IF NOT EXISTS idx_b2b_quotes_status ON b2b_quotes(status);
-- one DRAFT (active B2B cart) per credential:
CREATE UNIQUE INDEX IF NOT EXISTS uq_b2b_quotes_draft_per_cred
  ON b2b_quotes(credential_id) WHERE status = 'DRAFT';

CREATE TABLE IF NOT EXISTS b2b_quote_items (
  id UUID PRIMARY KEY,
  quote_id UUID NOT NULL REFERENCES b2b_quotes(id) ON DELETE CASCADE,
  store_product_id UUID NOT NULL,
  product_id UUID NOT NULL,
  variant_id UUID,
  quantity INTEGER NOT NULL,
  quoted_unit_price NUMERIC(12,2),      -- null until QUOTED
  product_title VARCHAR(255),
  sku VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_b2b_quote_items_quote ON b2b_quote_items(quote_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_b2b_quote_items_line ON b2b_quote_items(quote_id, store_product_id, variant_id);
