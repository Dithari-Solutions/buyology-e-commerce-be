# Checkout market and payment readiness

Order fulfilment now uses the server-priced cart's country ahead of an old profile
browsing preference. The delivery address must match that cart country; no cross-country
shipping is enabled by this change. If a legacy cart has no country, the profile remains
the fallback. A captured cart-first payment is unaffected by later profile country changes.
Old addresses with no recipient name/phone use the contact completed on the account;
explicitly saved delivery recipients are preserved.

Authenticated endpoints added under `/api/payments`:

- `GET /methods`: `{ methods: ["CARD", ...], currency: "AED" }`. Only locally configured,
  usable methods on the active provider are returned. No provider secrets or integration
  IDs are exposed. This describes configuration, not live gateway uptime or issuer approval.
- `GET /quote?amount=1000&currency=AZN`: an estimated `{ amount, currency: "AED" }` using
  the existing FX service. It never creates a payment. The authoritative order total is
  still recalculated and converted when payment is initiated.

An explicit `appOrderId` must exist before any gateway request. Existing ownership,
cash-order, already-settled, authoritative-amount and payment HMAC guards remain enforced.

Deploy the backend before the v2 storefront because checkout now asks these endpoints.
Azerbaijan storefront orders remain AZN; configured card payments use the existing AED
Paymob integration, with the converted estimate disclosed before payment. Instalment
options remain limited to UAE. Production location detection requires nginx to overwrite
the geo headers documented in the v2 region-routing guide.

Tests use mocked Paymob/FX responses. Production readiness still requires an approved and
declined Paymob test payment through the deployed callback configuration and a dashboard
order-state check. Never establish a payment/order link merely from matching price or phone.
