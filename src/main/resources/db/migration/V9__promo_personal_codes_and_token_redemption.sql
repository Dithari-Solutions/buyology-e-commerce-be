-- Personal (user-bound) promo codes + admin-configurable token redemption.
--
-- Guarded with to_regclass / IF NOT EXISTS because Flyway runs before Hibernate
-- ddl-auto: on a fresh database the tables may not exist yet (Hibernate then
-- creates them from the entities); on the existing prod DB (ddl-auto=validate)
-- this adds the new columns/table so validation passes. Loose UUID reference
-- (no FK) mirrors the entities' plain UUID fields, matching the V8 style.

-- 1) promo_codes: who a personal code belongs to + token-redemption audit.
DO $$
BEGIN
    IF to_regclass('public.promo_codes') IS NOT NULL THEN
        ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS target_user_id UUID;
        ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS redeemed_from_tokens INTEGER;
        CREATE INDEX IF NOT EXISTS idx_pc_target_user ON promo_codes (target_user_id);
    END IF;
END $$;

-- 2) token_redemption_config: single-row admin-tunable redemption rules.
CREATE TABLE IF NOT EXISTS token_redemption_config (
    id                            UUID PRIMARY KEY,
    enabled                       BOOLEAN        NOT NULL DEFAULT TRUE,
    token_cost                    INTEGER        NOT NULL DEFAULT 1000,
    discount_type                 VARCHAR(20)    NOT NULL DEFAULT 'PERCENTAGE',
    discount_value                NUMERIC(12,2)  NOT NULL DEFAULT 10.00,
    minimum_order_amount          NUMERIC(12,2),
    coupon_validity_days          INTEGER        NOT NULL DEFAULT 30,
    max_uses_per_coupon           INTEGER        NOT NULL DEFAULT 1,
    max_redemptions_per_customer  INTEGER                 DEFAULT 10,
    updated_at                    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Seed exactly one default config row if the table is empty.
INSERT INTO token_redemption_config (id, enabled, token_cost, discount_type, discount_value,
                                     coupon_validity_days, max_uses_per_coupon,
                                     max_redemptions_per_customer, updated_at)
SELECT gen_random_uuid(), TRUE, 1000, 'PERCENTAGE', 10.00, 30, 1, 10, now()
WHERE NOT EXISTS (SELECT 1 FROM token_redemption_config);
