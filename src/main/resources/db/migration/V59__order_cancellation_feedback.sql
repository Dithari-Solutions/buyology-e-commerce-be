-- Why a customer cancelled, as answers rather than as a sentence.
--
-- orders.cancellation_reason already exists and stays exactly as it is. It cannot do this job,
-- for two reasons that have nothing to do with its size:
--
--   1. It has two authors. An admin cancelling an order types into it freely, and a customer
--      cancelling their own order fills it with prose assembled from a questionnaire. There is no
--      way to tell those apart afterwards, so "group the cancellations by reason" over that column
--      groups by sentence and returns one row per order.
--   2. It is shown to the CUSTOMER. Its value is copied into the CANCELLED order_tracking_events
--      note that both the storefront and the mobile app render in the order timeline, so it has to
--      stay a readable sentence — it can never become a machine-readable payload.
--
-- Hence a second, structured store alongside it, holding the questionnaire as the customer answered
-- it. The stored document is deliberately SELF-DESCRIBING — each answer carries the question text
-- and the answer text, not just codes:
--
--   {"version":1,"source":"WEB","reasonCode":"CHEAPER_ELSEWHERE","submittedAt":"...",
--    "answers":[{"key":"reason","question":"Why are you cancelling this order?",
--                "answer":"I found it cheaper elsewhere","code":"CHEAPER_ELSEWHERE"}, ...]}
--
-- so the dashboard renders the list without knowing any codes, and a tenth cancellation reason
-- added in the storefront next month needs no dashboard deploy and no label table in this database.
-- "code" rides alongside purely so the rows stay groupable for analytics, and may be null.
--
-- The text is English even when the customer used the Arabic or Azerbaijani UI, matching
-- cancellation_reason and the admin dashboard. The clients send English; the backend never
-- translates and never interprets the codes.
--
-- NULL is the normal case and means "nobody answered a questionnaire for this order": every order
-- cancelled before this migration, every admin cancellation, and every client that has not shipped
-- the new flow yet. NULL is not an error and never becomes '{}' — an empty document would claim a
-- questionnaire was answered with nothing in it. No backfill: the answers did not exist to collect.
--
-- No index. V53 added a partial one because operations is asked every single day which cash orders
-- are still owed money. There is no daily question here — this column feeds an occasional "why are
-- people cancelling" read over a few thousand cancelled orders, which the existing status/date
-- indexes already narrow. A GIN index on it would be paid for on every cancellation write to serve
-- a query nobody runs on a schedule; add one when a real, repeated query asks for it.
--
-- Same to_regclass DO-block guard as V42/V43/V45/V52/V53: Flyway runs BEFORE Hibernate ddl-auto, so
-- on a fresh DB this is a safe no-op (Hibernate creates the column from the entity) while on the
-- existing prod DB it adds it.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders
            ADD COLUMN IF NOT EXISTS cancellation_feedback JSONB;
    END IF;
END $$;
