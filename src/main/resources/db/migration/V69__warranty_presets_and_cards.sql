-- Warranty: reusable durations (presets), a duration on each product, and one card per unit sold.
--
-- The two new tables are created unguarded, like V63 and V68: nothing else has to exist first.
-- The products columns are guarded, because on a fresh database Flyway runs before Hibernate has
-- created products; there Hibernate adds the columns itself and only the foreign key is missing.

CREATE TABLE IF NOT EXISTS warranty_presets (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    duration_months INTEGER NOT NULL CHECK (duration_months BETWEEN 1 AND 120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
-- "Laptop" and "laptop" are the same preset to whoever picks from the dropdown.
CREATE UNIQUE INDEX IF NOT EXISTS uq_warranty_presets_name ON warranty_presets (LOWER(name));

DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL THEN
        -- Whole months. Null = no warranty stated, so no card is issued when the product sells.
        ALTER TABLE products ADD COLUMN IF NOT EXISTS warranty_months INTEGER;
        -- Set when the duration follows a preset; editing the preset moves these products with it.
        ALTER TABLE products ADD COLUMN IF NOT EXISTS warranty_preset_id UUID;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_warranty_months') THEN
            ALTER TABLE products ADD CONSTRAINT chk_products_warranty_months
                CHECK (warranty_months IS NULL OR warranty_months BETWEEN 1 AND 120);
        END IF;
        -- Deleting a preset keeps each product's months and turns them into a custom duration.
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_warranty_preset') THEN
            ALTER TABLE products ADD CONSTRAINT fk_products_warranty_preset
                FOREIGN KEY (warranty_preset_id) REFERENCES warranty_presets (id) ON DELETE SET NULL;
        END IF;
        CREATE INDEX IF NOT EXISTS idx_products_warranty_preset ON products (warranty_preset_id);
    END IF;
END $$;

-- One row per unit: an order item of quantity 3 gets unit_index 1, 2 and 3. That pair is unique,
-- which is what makes issuing idempotent — the delivery listener and the catch-up sweep can both
-- run for the same order and the second one adds nothing.
--
-- The product fields are a snapshot taken at issue time. A card describes the unit that was sold,
-- so later edits to the listing (or deleting it) must not change what the customer's card says.
CREATE TABLE IF NOT EXISTS warranty_cards (
    id UUID PRIMARY KEY,
    serial_number VARCHAR(32) NOT NULL,
    order_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    unit_index INTEGER NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID,
    duration_months INTEGER NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'VOID')),
    void_reason VARCHAR(255),
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    product_name VARCHAR(255) NOT NULL,
    brand_name VARCHAR(120),
    category_name VARCHAR(255),
    condition_label VARCHAR(60),
    specs JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_warranty_cards_serial UNIQUE (serial_number),
    CONSTRAINT uq_warranty_cards_unit UNIQUE (order_item_id, unit_index)
);
CREATE INDEX IF NOT EXISTS idx_warranty_cards_order ON warranty_cards (order_id);
