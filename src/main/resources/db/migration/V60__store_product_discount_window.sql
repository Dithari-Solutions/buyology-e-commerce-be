-- When a store discount is live: the flash sale, as two dates on the discount that already exists.
--
-- store_products.discount_type / discount_value have priced this shop since the beginning, and they
-- are the REAL price — StoreProduct.effectivePrice(now) is what the cart stamps onto a line and what
-- Buy Now charges. What they have never had is a time. A "flash sale" set today therefore ran until
-- a human remembered to remove it, which is not a flash sale, it is a permanent markdown with a
-- promise attached.
--
-- The window goes ON the existing discount rather than into a flash_sales table of its own, for two
-- reasons that are both about money:
--
--   1. The price a customer pays flows through exactly ONE function. A second, parallel pricing
--      system would have to be consulted by CartService, OrderService, the product read path and the
--      admin view, and kept in step with this one forever; the first time the two disagreed, the
--      cart would show one price and checkout would charge another. Reusing the single choke point
--      makes that class of bug unreachable.
--   2. Expiry becomes a property of the DATA, not of a job. A sale whose end needs a cron to enforce
--      is a sale that keeps discounting when the cron fails — and this codebase has no reliable
--      place to hang one. Time-aware pricing cannot leak that way: nothing has to run for a sale to
--      end, the next read simply prices it at store_price again.
--
-- What NULL means on each — this is the backward-compatibility contract, and the reason there is no
-- backfill:
--
--   discount_starts_at   NULL = "already started". Every existing discounted row is live now and
--                        must keep pricing EXACTLY as it does today.
--   discount_ends_at     NULL = "never ends", i.e. an ordinary permanent markdown, which is what
--                        every existing discount is.
--
-- So a row with a discount and two NULL dates prices identically before and after this migration,
-- and 200k rows do not need rewriting to say so.
--
-- Both ends are INCLUSIVE, matching the only other validity window in this codebase
-- (PromoCodeService treats a code as expired only when expires_at is strictly before now).
-- Instants, not local timestamps: the admin API converts "ends 31 March" into the end of that day
-- in Asia/Dubai before it ever reaches this column, so the database never has to know a timezone.
--
-- "On flash sale" is then defined honestly, and derived rather than stored: an ACTIVE discount that
-- has an END in the future. A permanent markdown is not a flash sale. There is no third column and
-- no flag to keep in sync, so a sale cannot end while a boolean still claims it is on.
--
-- NOTE: products.is_super_deal is a separate, manual editorial flag driving the "Super Deals" rail.
-- It is not a discount, it has no dates, and nothing here touches it.
--
-- The index is the one query this feature adds that runs on a schedule the shop cares about: "what
-- is on the flash sale right now", asked by the storefront rail, the app home screen and the
-- dashboard's flash-sale screen. Partial, because it is only ever asked about rows that HAVE an end
-- date — which is a small minority of store_products and always will be — so the write cost on
-- every ordinary price edit is nil. V53 added its partial index on the same reasoning.
--
-- Same to_regclass DO-block guard as V42/V43/V45/V52/V53/V59: Flyway runs BEFORE Hibernate
-- ddl-auto, so on a fresh DB this is a safe no-op (Hibernate creates the columns from the entity)
-- while on the existing prod DB it adds them.
DO $$
BEGIN
    IF to_regclass('public.store_products') IS NOT NULL THEN
        ALTER TABLE store_products
            ADD COLUMN IF NOT EXISTS discount_starts_at TIMESTAMPTZ,
            ADD COLUMN IF NOT EXISTS discount_ends_at   TIMESTAMPTZ;

        CREATE INDEX IF NOT EXISTS idx_store_products_flash_sale
            ON store_products (discount_ends_at)
            WHERE discount_ends_at IS NOT NULL
              AND discount_type IS NOT NULL
              AND deleted_at IS NULL;
    END IF;
END $$;
