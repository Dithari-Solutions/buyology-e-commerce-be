-- Promo redemption is now recorded only once an order is PAID (see OrderService.recordPromoUsageOnPaid).
-- The resolved promo code id is stamped on the order at creation so it can be redeemed at payment time.
-- Guarded with to_regclass because Flyway runs before Hibernate ddl-auto: on a fresh database the
-- orders table may not exist yet (Hibernate then creates the column from the entity); on the existing
-- prod DB this adds the column. Loose reference (no FK) — mirrors the entity's plain UUID field.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS promo_code_id UUID;
    END IF;
END $$;
