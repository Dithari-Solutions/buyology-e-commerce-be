-- Promo redemption is now recorded only once an order is PAID (see OrderService.recordPromoUsageOnPaid).
-- The resolved promo code id is stamped on the order at creation so it can be redeemed at payment time.
-- Loose reference (no FK) — mirrors the entity's plain UUID field.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promo_code_id UUID;
