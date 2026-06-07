-- Story display order: admin-controllable ordering for the stories feed.
-- Existing rows are backfilled by creation time so current ordering is preserved.
ALTER TABLE stories ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;

WITH ordered AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY created_at)) - 1 AS rn
    FROM stories
)
UPDATE stories s
SET display_order = ordered.rn
FROM ordered
WHERE s.id = ordered.id;
