-- ERPNext order sync: mirror of what was pushed to ERPNext for each PAID order.
-- ErpOrderSyncService stamps these once it creates the Sales Order + Sales Invoice, so a
-- retry can be made idempotent (an order that already carries erp_sales_invoice is skipped)
-- and the admin ERP page can show per-order sync state. Nothing in the order lifecycle
-- reads these — a failed push only records erp_sync_error and leaves the order PAID.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh
-- database (and in FlywayBaselineMigrationIT) the orders table does not exist yet and
-- Hibernate creates these columns from the entity instead; on the existing prod DB this
-- adds them. Mirrors V8 / V17 / V18 / V21 / V22.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS erp_sales_order   VARCHAR(140);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS erp_sales_invoice VARCHAR(140);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS erp_synced_at     TIMESTAMPTZ;
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS erp_sync_error    VARCHAR(1000);

        -- Drives the admin "unsynced paid orders" lookup on the ERP page.
        CREATE INDEX IF NOT EXISTS idx_orders_erp_synced_at ON orders(erp_synced_at);
    END IF;
END $$;
