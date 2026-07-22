-- AI repair price estimate: a preliminary, non-binding price range produced by Claude from the
-- customer's problem photos + description, priced for the UAE market (always stored in AED).
--
-- Advisory only — it never becomes the customer's quote. The repair team still sends the binding
-- estimate via estimated_price/estimated_price_currency; these columns only feed the "preliminary
-- estimate" card shown to the customer and the AI suggestion shown in the dashboard.
--
-- All columns are Hibernate-owned (added to RepairRequest); ddl-auto=update creates them on a
-- fresh DB and this guarded ALTER keeps an existing prod schema in sync. The to_regclass guard is
-- required because Flyway runs BEFORE Hibernate — on a fresh DB the table does not exist yet.
-- Mirrors V21/V23/V24.
DO $$
BEGIN
    IF to_regclass('public.repair_requests') IS NOT NULL THEN
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS ai_estimate_min_price NUMERIC(12, 2);
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS ai_estimate_max_price NUMERIC(12, 2);
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS ai_estimate_currency  VARCHAR(3);
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS ai_estimate_confidence VARCHAR(16);
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS ai_estimate_summary   TEXT;
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS ai_estimate_time      VARCHAR(120);
        ALTER TABLE repair_requests ADD COLUMN IF NOT EXISTS ai_estimated_at       TIMESTAMPTZ;
    END IF;
END $$;
