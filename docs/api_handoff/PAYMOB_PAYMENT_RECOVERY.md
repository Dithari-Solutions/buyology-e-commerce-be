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

## Signup contact and dashboard display

Current web and mobile email/password signup forms collect an international phone.
`POST /auth/signup` accepts `phoneNumber`, normalizes it, and retains it on the email
OTP record. Verifying the email code saves the number on UserProfiles and the local
AuthCredentials record without an SMS step. The field remains optional in the API
for previously released clients; ordering still requires a nonblank profile phone.
Flyway V66 adds `email_otp.phone_number`.

Payment initiation retains the same resolved contact sent to Paymob on the local
payment attempt. Dashboard customer details prefer profile/credential phones and
saved addresses, then fall back to an existing payment contact. Order lists and
details also fall back to the payment contact; list lookups are batched. A billing
number from a payment does not overwrite a customer's current account phone.

The order re-check no longer interprets a missing eligible local attempt as evidence
that the customer did not pay. A supplied Paymob ID is still queried to identify its
actual local order. The order page links to Payment verification with that ID filled
in, and ineligible local states are explicitly identified as requiring review.

## Phone requirement

New orders require a nonblank customer profile phone before capture; SMS verification
is not required. Payment initiation retains its existing profile-readiness phone
check. Recovery after capture does not reject a historical paid order merely because
its customer profile lacks a phone.

## Deployment and verification

Deploy backend first so Flyway applies V64 (phone defaults) if needed and V65 (gateway
check timestamp) and V66 (signup phone), then deploy the dashboard using its normal production API URL.
Unit tests and mocked dashboard browser tests never query or settle a live payment.
The reported transaction 36914143 still requires inspection through the merchant
account after deployment; its live state was not established from source code.

## Paymob request contract and unmatched-payment evidence

Intention requests use Paymob's documented top-level `special_reference`, `items`,
`notification_url` and `redirection_url`. The former request placed the reference
under `merchant_order_id` and callbacks inside an `order` object, which is not the
Intention API contract. `extras` now includes a Buyology platform marker and a copy
of the local attempt reference. The numeric `intention_order_id` is still stored.

The admin lookup returns a restricted evidence object: Paymob order/reference,
intention, integration, creation time, billing phone/email, source, platform marker,
gateway URL and live/test flag when supplied. It never exposes payment tokens,
client secrets or card numbers. Numeric order replies, intention IDs and documented
extra references can identify an existing payment; amounts and contacts cannot.
The dashboard also displays its configured backend API so a dev/production mismatch
is visible. No local match alone does not establish that a payment is external.

Callbacks try all supported reference routes instead of stopping after an unknown
merchant reference. Unresolved callbacks are audited without consuming their
idempotency event, so later re-delivery can resolve after reference persistence.
Known Paymob order ID conflicts remain a refusal to associate payments. For legacy
attempts with no saved Paymob order ID, unsigned callback references must also be
confirmed by an authenticated gateway query before they can identify the payment.

These changes do not invent or backfill a missing historical payment/order mapping.
For transaction 36914143, deploy the diagnostic fields and compare its Paymob order
and merchant reference with the production database before any manual association.
