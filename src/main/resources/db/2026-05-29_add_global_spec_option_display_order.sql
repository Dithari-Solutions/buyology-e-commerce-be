-- =============================================================================
-- Add display_order to global_spec_options
--
-- The app runs with spring.jpa.hibernate.ddl-auto=update, which will add the
-- column automatically — BUT it cannot safely add a NOT NULL column to a table
-- that already has rows, nor backfill a sensible per-group ordering. Run this
-- migration once against the database BEFORE deploying the new build.
--
-- PostgreSQL. Idempotent: safe to run more than once.
-- =============================================================================

-- 1. Add the column with a default so existing rows are valid immediately.
ALTER TABLE global_spec_options
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;

-- 2. Backfill: give existing options a stable order within each group based on
--    their creation time (0-based per group).
WITH ordered AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY group_id ORDER BY created_at, id) - 1 AS rn
    FROM global_spec_options
)
UPDATE global_spec_options o
SET display_order = ordered.rn
FROM ordered
WHERE o.id = ordered.id;

-- 3. (Optional) helps ordered reads when groups have many options.
CREATE INDEX IF NOT EXISTS idx_global_spec_options_group_order
    ON global_spec_options (group_id, display_order);
