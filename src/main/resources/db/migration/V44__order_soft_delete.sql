-- Order trash: a superadmin can delete an order, it disappears from every list, and it is
-- destroyed for good 30 days later unless someone restores it first.
--
-- Soft delete rather than DELETE because an order is the record of a real transaction: a
-- mis-click that vaporises one is unrecoverable, and the 30-day window is what makes the
-- action safe to offer at all.
--
-- Guarded like every other table migration here (see V23): Flyway runs before Hibernate
-- ddl-auto, so this is a no-op on a fresh database and adds the columns on the existing one.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN

        ALTER TABLE orders ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS deleted_by UUID;

        -- Partial index: every normal query filters deleted_at IS NULL, and the trash itself is
        -- tiny, so indexing only the deleted rows keeps the common path cheap.
        CREATE INDEX IF NOT EXISTS idx_orders_deleted_at
            ON orders(deleted_at) WHERE deleted_at IS NOT NULL;

    END IF;
END $$;
