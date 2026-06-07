-- Cart line items keep the pre-discount unit price (nullable) so the cart can show
-- the original (struck-through) price alongside the discounted price actually charged.
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS original_unit_price NUMERIC(12, 2);
