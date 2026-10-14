# Paymob payment verification and recovery

The dashboard exposes **Orders → Payment verification** to superadmins. This is a
read-only Paymob lookup followed by a separate confirmation action on the reviewed
order. A transaction ID may be pasted as `36914143` or `#36914143`.

## API

Both endpoints require SUPERADMIN or the existing legacy-admin policy.

- `GET /api/admin/payments/paymob/transactions/{transactionId}` queries Paymob and
  returns the matched internal payment/order IDs, gateway completion, local payment
  and order statuses, amount/currency, message, and `canConfirm`.
- `POST /api/admin/payments/paymob/transactions/{transactionId}/confirm?orderId={uuid}`
  queries Paymob again, validates the reviewed order ID and then runs the existing
  settlement path. Refresh the lookup after confirmation to see the actual order
  status: a local payment SUCCESS alone does not prove order settlement succeeded.

Confirmation requires a known ORDER payment attempt, matching Paymob order ID or
merchant reference, matching payment amount/currency, gateway success with no pending,
refund or void flags, no uncaptured authorization, and an existing PENDING_PAYMENT order. Cancelled, refunded,
unmatched and already-advanced orders cannot be revived through this endpoint.
A previously FAILED payment attempt can be recovered only after the explicitly
provided transaction is confirmed successful at Paymob. Declines cannot be confirmed.

## Recovery fixes

- Rechecking a local SUCCESS payment now retries the normal order-paid listener when
  its order is still PENDING_PAYMENT, instead of reporting an already-settled order.
- The listener and local-success reconciler lock and re-read orders before applying
  paid effects. Scheduled recovery uses the full paid-order path, including promo
  usage, notifications and OrderPaidEvent, and only selects pending orders with
  successful payment attempts so abandoned orders cannot fill its batch.
- The Paymob gateway sweep rotates attempts by `last_gateway_check_at` rather than
  repeatedly processing only the latest 40 attempts. Its existing grace period,
  lookback and per-run limit remain in effect.

## Phone requirement

New orders require a nonblank customer profile phone before capture; SMS verification
is not required. Payment initiation retains its existing profile-readiness phone
check. Recovery after capture does not reject a historical paid order merely because
its customer profile lacks a phone.

## Deployment and verification

Deploy backend first so Flyway applies V64 (phone defaults) if needed and V65 (gateway
check timestamp), then deploy the dashboard using its normal production API URL.
Unit tests and mocked dashboard browser tests never query or settle a live payment.
The reported transaction 36914143 still requires inspection through the merchant
account after deployment; its live state was not established from source code.
