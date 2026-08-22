-- The card a customer paid with, for display on the order page: last four digits and
-- brand, both parsed from Paymob's callback (source_data.pan is already masked by
-- Paymob — we never see, and never store, a full PAN).
DO $$
BEGIN
    IF to_regclass('public.payment_transactions') IS NOT NULL THEN
        ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS card_last4 VARCHAR(4);
        ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS card_brand VARCHAR(32);
    END IF;
END $$;

-- Backfill from the append-only webhook audit log: every callback's verbatim payload is
-- kept in payment_webhook_events, so historical orders can show their card too. The most
-- recent event per transaction wins; only clean 4-digit tails are written.
DO $$
BEGIN
    IF to_regclass('public.payment_webhook_events') IS NOT NULL
       AND to_regclass('public.payment_transactions') IS NOT NULL THEN
        UPDATE payment_transactions pt
        SET card_last4 = sub.last4,
            card_brand = NULLIF(sub.brand, '')
        FROM (
            SELECT DISTINCT ON (transaction_id)
                   transaction_id,
                   right(regexp_replace(payload -> 'obj' -> 'source_data' ->> 'pan', '\D', '', 'g'), 4) AS last4,
                   left(payload -> 'obj' -> 'source_data' ->> 'sub_type', 32) AS brand
            FROM payment_webhook_events
            WHERE transaction_id IS NOT NULL
              AND payload -> 'obj' -> 'source_data' ->> 'pan' IS NOT NULL
            ORDER BY transaction_id, created_at DESC
        ) sub
        WHERE pt.id = sub.transaction_id
          AND pt.card_last4 IS NULL
          AND length(sub.last4) = 4;
    END IF;
END $$;
