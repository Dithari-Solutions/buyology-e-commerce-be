-- V40's backfill picked the NEWEST pan-bearing webhook event per transaction. That event is
-- not always the one that settled the payment: money-mismatch-rejected callbacks and
-- post-terminal callbacks are stored with error NULL too, and a late one from a DIFFERENT
-- payment attempt (same checkout, different card) would win the recency ordering — putting
-- another card's last4 on the customer's order page. Recompute, pinned to the event whose
-- provider_txn_id equals the transaction's own paymob_transaction_id: the callback that
-- actually drove the status change. A wrong card display is worse than none, so values with
-- no pinned pan-bearing event are cleared rather than kept.
DO $$
BEGIN
    IF to_regclass('public.payment_webhook_events') IS NOT NULL
       AND to_regclass('public.payment_transactions') IS NOT NULL THEN

        -- Re-pin: where a settling event carries the pan, its values win.
        UPDATE payment_transactions pt
        SET card_last4 = sub.last4,
            card_brand = NULLIF(sub.brand, '')
        FROM (
            SELECT DISTINCT ON (e.transaction_id)
                   e.transaction_id,
                   right(regexp_replace(e.payload -> 'obj' -> 'source_data' ->> 'pan', '\D', '', 'g'), 4) AS last4,
                   left(e.payload -> 'obj' -> 'source_data' ->> 'sub_type', 32) AS brand
            FROM payment_webhook_events e
            JOIN payment_transactions t ON t.id = e.transaction_id
            WHERE e.provider_txn_id = t.paymob_transaction_id::text
              AND e.payload -> 'obj' -> 'source_data' ->> 'pan' IS NOT NULL
            ORDER BY e.transaction_id, e.created_at DESC
        ) sub
        WHERE pt.id = sub.transaction_id
          AND length(sub.last4) = 4
          AND (pt.card_last4 IS DISTINCT FROM sub.last4
               OR pt.card_brand IS DISTINCT FROM NULLIF(sub.brand, ''));

        -- Clear values that only a foreign (non-settling) event could have produced.
        UPDATE payment_transactions pt
        SET card_last4 = NULL,
            card_brand = NULL
        WHERE pt.card_last4 IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM payment_webhook_events e
              WHERE e.transaction_id = pt.id
                AND e.provider_txn_id = pt.paymob_transaction_id::text
                AND e.payload -> 'obj' -> 'source_data' ->> 'pan' IS NOT NULL
          );
    END IF;
END $$;
