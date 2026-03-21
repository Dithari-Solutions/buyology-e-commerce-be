# Payment System — API Handoff

**Base URL:** `https://api-dev.dithari.com`
**Auth:** All endpoints require a valid JWT access token **except** the webhook endpoint (verified by Paymob HMAC instead).

```
Authorization: Bearer <access_token>
```

---

## Response Envelope

Every endpoint returns the same wrapper:

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

On failure, `data` is `null` and `statusCode` + `message` describe the error.

---

## How the Payment Flow Works

Understanding this before integrating will save debugging time.

The backend uses the **Paymob Intention API (v2)**. One backend call creates a payment intention and returns a `clientSecret` + a ready-to-open `checkoutUrl`. All payment methods (card, Tabby, Tamara) go through the same **Unified Checkout** page hosted by Paymob.

```
Frontend                        Backend                         Paymob
   │                               │                               │
   │  POST /api/payments/initiate  │                               │
   │──────────────────────────────>│                               │
   │                               │  POST /v1/intention/          │
   │                               │──────────────────────────────>│
   │                               │<── { intentionId,             │
   │                               │      clientSecret }           │
   │                               │                               │
   │<── { clientSecret,            │                               │
   │      checkoutUrl }            │                               │
   │                               │                               │
   │  Open checkoutUrl             │                               │
   │  (WebView / redirect)         │                               │
   │──────────────────────────────────────────────────────────────>│
   │                               │                               │
   │              [user pays on Paymob Unified Checkout]           │
   │                               │                               │
   │                               │  Webhook callback             │
   │                               │<──────────────────────────────│
   │                               │  (stored + processed)         │
   │                               │                               │
   │  GET /transactions/{id}       │                               │
   │──────────────────────────────>│                               │
   │<── { status: "SUCCESS" }      │                               │
```

**Key rules:**
- One `POST /initiate` call = one transaction row. If a payment fails, call `/initiate` again — never reuse a failed transaction.
- The backend stores the raw Paymob webhook before processing it. **Never rely on a redirect to determine final status** — always poll the transaction status after the user returns.

---

## Enums

### PaymentMethodType
| Value | Description |
|---|---|
| `CARD` | Direct card payment |
| `TABBY` | Buy Now Pay Later — Tabby |
| `TAMARA` | Buy Now Pay Later — Tamara |

All three methods open the same Unified Checkout URL. Paymob displays the correct UI based on the `integration_id` passed during intention creation.

### PaymentStatus
| Value | Description |
|---|---|
| `PENDING` | Transaction created, user has not completed payment yet |
| `PROCESSING` | Webhook arrived, status update in progress |
| `SUCCESS` | Paymob confirmed payment via webhook |
| `FAILED` | Paymob reported failure via webhook |
| `CANCELLED` | User abandoned or session timed out |
| `REFUNDED` | Full refund processed |
| `PARTIALLY_REFUNDED` | One or more partial refunds processed |

**Status lifecycle:**
```
PENDING
  └─→ PROCESSING
        ├─→ SUCCESS
        │     ├─→ REFUNDED
        │     └─→ PARTIALLY_REFUNDED
        ├─→ FAILED
        └─→ CANCELLED
```

> Once a transaction reaches `SUCCESS`, `FAILED`, or `CANCELLED` it is immutable except for the refund workflow.

### RefundStatus
| Value | Description |
|---|---|
| `PENDING` | Refund submitted, awaiting Paymob confirmation |
| `SUCCESS` | Refund confirmed |
| `FAILED` | Refund failed at Paymob |

---

## 1. Initiate Payment

`POST /api/payments/initiate`

Creates a Paymob payment intention and returns a `checkoutUrl` to open for the user.

**Request Body:**
```json
{
  "appOrderId": "uuid",
  "methodType": "CARD",
  "amount": 250.00,
  "currency": "AED",
  "customerId": "uuid",
  "customerEmail": "user@example.com",
  "customerPhone": "+971501234567",
  "billingName": "Ahmed Al Mansouri",
  "billingApartment": "4B",
  "billingFloor": "2",
  "billingStreet": "Sheikh Zayed Road",
  "billingBuilding": "Tower 1",
  "billingCity": "Dubai",
  "billingCountry": "AE",
  "billingState": "Dubai",
  "billingPostalCode": "00000"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `appOrderId` | UUID | Yes | Your order ID from the orders service |
| `methodType` | PaymentMethodType | Yes | `CARD`, `TABBY`, or `TAMARA` |
| `amount` | decimal | Yes | Minimum `0.01`. Use the full order amount |
| `currency` | string | Yes | Exactly 3 chars — use `"AED"` |
| `customerId` | UUID | Yes | Logged-in user's ID (auth credentials ID from JWT) |
| `customerEmail` | string | Yes | Snapshotted on the transaction row |
| `customerPhone` | string | No | |
| `billingName` | string | Yes | Full name |
| `billingApartment` | string | No | Sent to Paymob; defaults to `"NA"` if omitted |
| `billingFloor` | string | No | |
| `billingStreet` | string | No | |
| `billingBuilding` | string | No | |
| `billingCity` | string | No | |
| `billingCountry` | string | No | ISO 2-letter country code, e.g. `"AE"` |
| `billingState` | string | No | |
| `billingPostalCode` | string | No | |

**Response `data`:**
```json
{
  "transactionId": "uuid",
  "methodType": "CARD",
  "amount": 250.00,
  "currency": "AED",
  "clientSecret": "zsk_test_abc123...",
  "checkoutUrl": "https://uae.paymob.com/unifiedcheckout/?publicKey=are_xxx&clientSecret=zsk_test_abc123..."
}
```

| Field | Type | Notes |
|---|---|---|
| `transactionId` | UUID | Save this — you need it to poll for the final status |
| `clientSecret` | string | Single-use token tied to this intention. Short-lived |
| `checkoutUrl` | string | Open this URL in a WebView or redirect the user to it. Works for all payment methods |

---

## 2. Get Transaction

`GET /api/payments/transactions/{transactionId}`

Poll this after the user returns from the Paymob checkout to get the final status.

**Response `data`:**
```json
{
  "id": "uuid",
  "appOrderId": "uuid",
  "methodType": "CARD",
  "amount": 250.00,
  "amountCents": 25000,
  "currency": "AED",
  "status": "SUCCESS",
  "providerTransactionId": "123456789",
  "failureReason": null,
  "failureCode": null,
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:05:00Z"
}
```

---

## 3. Get All Transactions for an Order

`GET /api/payments/orders/{appOrderId}/transactions`

Returns every payment attempt for a given order (initial attempt + any retries).

**Response `data`:** Array of transaction objects (same shape as above), ordered oldest first.

Use this on the order detail page to show payment history and the current status.

---

## 4. Initiate Refund

`POST /api/payments/refunds`

Supports full and partial refunds. Multiple partial refunds are allowed as long as the total does not exceed the original transaction amount.

**Request Body:**
```json
{
  "transactionId": "uuid",
  "amount": 100.00,
  "reason": "Customer requested return",
  "notes": "Item returned in good condition",
  "refundedBy": "uuid"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `transactionId` | UUID | Yes | Must be in `SUCCESS` or `PARTIALLY_REFUNDED` status |
| `amount` | decimal | Yes | Must not exceed the remaining refundable balance |
| `reason` | string | No | Shown in admin/reconciliation views |
| `notes` | string | No | Internal notes |
| `refundedBy` | UUID | Yes | UUID of the admin initiating the refund. Use a system UUID for automated refunds |

**Response `data`:**
```json
{
  "id": "uuid",
  "transactionId": "uuid",
  "amount": 100.00,
  "amountCents": 10000,
  "currency": "AED",
  "status": "SUCCESS",
  "reason": "Customer requested return",
  "providerRefundId": "987654321",
  "refundedBy": "uuid",
  "createdAt": "2026-03-21T11:00:00Z"
}
```

---

## Frontend Integration Guide

### All Payment Methods — Unified Checkout

The new Paymob Intention API uses a single **Unified Checkout** page for all payment methods (card, Tabby, Tamara). The integration steps are the same regardless of method.

**Step 1** — Call `POST /api/payments/initiate` with the chosen `methodType`.

**Step 2** — Open `checkoutUrl` from the response. Use a WebView in mobile apps, or redirect/open a new tab on web:

```js
// Web — redirect current page
window.location.href = data.checkoutUrl;

// Web — open in new tab
window.open(data.checkoutUrl, '_blank');
```

```swift
// iOS — open in SFSafariViewController or WKWebView
let url = URL(string: data.checkoutUrl)!
let safariVC = SFSafariViewController(url: url)
present(safariVC, animated: true)
```

```kotlin
// Android — open in CustomTabs or WebView
val intent = CustomTabsIntent.Builder().build()
intent.launchUrl(context, Uri.parse(data.checkoutUrl))
```

**Step 3** — Paymob redirects the user back to your app/site after they complete or cancel. On return, poll `GET /api/payments/transactions/{transactionId}` to get the authoritative status.

> The `clientSecret` in the response is already embedded in `checkoutUrl`. You only need `clientSecret` separately if you are building a custom checkout UI directly on top of the Paymob JS SDK — for standard integration, just open `checkoutUrl`.

---

### Polling vs Webhooks

| Situation | What to do |
|---|---|
| User returns to your site after payment | Poll `GET /transactions/{id}` immediately |
| Status is still `PROCESSING` after polling | Poll again with a short delay (1–2 seconds, max ~10 attempts) |
| Status is `PENDING` after polling | The webhook has not arrived yet — show a loading state and keep polling |
| Status is `SUCCESS` | Show confirmation, update order UI |
| Status is `FAILED` | Show failure message with option to retry (call `/initiate` again) |
| Status is `CANCELLED` | Offer to try a different payment method |

> The webhook typically arrives within 2–5 seconds of payment completion. If status is still `PROCESSING` after 30 seconds, show the user a "payment is being confirmed" message and check again in a few minutes — do not assume failure.

---

### Retry Flow (failed or cancelled payment)

Each retry is a **new** call to `POST /api/payments/initiate` with the same `appOrderId`. The backend creates a new transaction row — it does not reuse the failed one.

```
1st attempt  → transactionId: "aaa-111"  status: FAILED
2nd attempt  → transactionId: "bbb-222"  status: SUCCESS  ← poll this one
```

Use `GET /api/payments/orders/{appOrderId}/transactions` to show the user their attempt history on the order detail page.

---

## Common Error Responses

| HTTP Status | Typical Cause |
|---|---|
| `400` | Validation failure — missing required field, amount too low, currency wrong length |
| `404` | Transaction not found |
| `409` | Refund exceeds remaining refundable amount |
| `500` | Paymob API call failed or unexpected server error |

**Error response shape:**
```json
{
  "statusCode": 400,
  "message": "Refund amount exceeds remaining refundable amount. Original: 250.00, already refunded: 200.00, requested: 100.00",
  "data": null
}
```

---

## Typical Checkout Flow (end-to-end)

```
1. User selects items → your frontend has an appOrderId and total amount

2. User picks payment method (Card / Tabby / Tamara) on checkout page

3. POST /api/payments/initiate
   → save transactionId in component state

4. Open checkoutUrl in WebView / redirect
   (same step for CARD, TABBY, and TAMARA)

5. User completes payment on Paymob Unified Checkout

6. User returns to your app/site

7. Poll GET /api/payments/transactions/{transactionId}
   → status: SUCCESS     → navigate to order confirmation page
   → status: FAILED      → show error, offer retry
   → status: PROCESSING  → keep polling (webhook in transit)
```
