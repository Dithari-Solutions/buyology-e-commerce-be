-- One-winner claim for scheduled jobs.
--
-- The backend runs on web-app-1 AND web-app-2 (see the deploy matrix), so every @Scheduled method
-- fires twice — which is why the streak reminder arrives as two identical notifications. A row
-- with a unique (task_name, run_on) makes the first instance to insert the winner and the second
-- lose harmlessly on the constraint.
--
-- A table rather than a distributed lock library: the guarantee needed here is "once per task per
-- day", which is exactly what a unique index already gives, and it survives a restart mid-job
-- without a lease to expire.
DO $$
BEGIN
    CREATE TABLE IF NOT EXISTS scheduled_task_runs (
      id UUID PRIMARY KEY,
      task_name VARCHAR(80) NOT NULL,
      run_on DATE NOT NULL,
      claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      CONSTRAINT uq_scheduled_task_runs UNIQUE (task_name, run_on)
    );

    -- Old rows have no value once their day has passed; this keeps the sweep cheap.
    CREATE INDEX IF NOT EXISTS idx_scheduled_task_runs_day ON scheduled_task_runs (run_on);
END $$;
