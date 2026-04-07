# Payment Module — Frontend Integration Guide

> **Provider**: Paymob (Intention API v2)
> **Supported Methods**: CARD, TABBY, TAMARA
> **Base URL**: `/api/payments`
> **Auth**: JWT Bearer token (except webhook — HMAC-only)

---

## Table of Contents

1. [Overview & Flow](#1-overview--flow)
2. [Auto Order Creation Flow](#2-auto-order-creation-flow)
3. [API Reference](#3-api-reference)
4. [Request & Response Shapes](#4-request--response-shapes)
5. [Paymob Unified Checkout Integration](#5-paymob-unified-checkout-integration)
6. [Payment Method Configuration](#6-payment-method-configuration)
7. [Webhook Handling (Backend-only)](#7-webhook-handling-backend-only)
8. [Payment Status Reference](#8-payment-status-reference)
9. [Error Handling](#9-error-handling)
10. [End-to-End Sequence Diagram](#10-end-to-end-sequence-diagram)

---

## 1. Overview & Flow

The payment system uses a **cart-first auto-order** model. The customer does not create the order manually — the order is created automatically by the backend **after a successful payment webhook**.

```
Cart (CHECKED_OUT) → Initiate Payment → Paymob Checkout → Webhook → Auto Order Created
```

The frontend is responsible for:
- Calling `POST /api/payments/initiate` with cart and delivery details
- Opening the Paymob Unified Checkout using the returned `clientSecret`
- Redirecting the customer after checkout (success/failure)
- Polling or displaying the transaction status

---

## 2. Auto Order Creation Flow

### Step-by-step

| Step | Actor | Action |
|------|-------|--------|
| 1 | Customer | Reviews cart, selects delivery method and address |
| 2 | Frontend | Calls `POST /api/payments/initiate` |
| 3 | Backend | Creates a `PaymentTransaction` (status: `PENDING`) |
| 4 | Backend | Returns `clientSecret` + `checkoutUrl` |
| 5 | Frontend | Opens Paymob Unified Checkout with `clientSecret` |
| 6 | Customer | Completes payment on Paymob |
| 7 | Paymob | POSTs webhook to `/api/payments/webhook?hmac=<sha512>` |
| 8 | Backend | Validates HMAC, transitions transaction → `SUCCESS` |
| 9 | Backend | **Automatically creates the Order from cart metadata** |
| 10 | Backend | Marks order `PAID`, back-fills `transaction.appOrderId` |
| 11 | Frontend | Shows success screen, directs to order history |

> **Important**: The order is not created by the frontend. After a successful payment, the order appears automatically in `GET /api/orders`.

### Cart Prerequisites

Before calling `initiate`, the cart **must** be in `CHECKED_OUT` status. Also, the authenticated user profile **must** have the following fields populated:

- `firstName`
- `lastName`
- `phone`
- At least one saved address

If any of these are missing, the backend returns a `400 Bad Request` with a validation message.

---

## 3. API Reference

### 3.1 Initiate Payment

```
POST /api/payments/initiate
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

Initiates a payment transaction and returns the Paymob checkout token.

### 3.2 Get Transaction

```
GET /api/payments/transactions/{transactionId}
Authorization: Bearer <jwt_token>
```

Fetches the current status of a specific transaction.

### 3.3 Get Transactions for an Order

```
GET /api/payments/orders/{appOrderId}/transactions
Authorization: Bearer <jwt_token>
```

Lists all payment attempts linked to a given order (e.g., initial attempt + retry).

### 3.4 Initiate Refund (Admin)

```
POST /api/payments/refunds
Authorization: Bearer <jwt_token> (Admin role required)
Content-Type: application/json
```

Initiates a full or partial refund through Paymob.

---

## 4. Request & Response Shapes

### 4.1 `POST /api/payments/initiate` — Request

```json
{
  "cartId": "uuid",
  "addressId": "uuid",
  "deliveryMethod": "EXPRESS | REGULAR",
  "shippingFee": 15.00,
  "methodType": "CARD | TABBY | TAMARA",
  "amount": 250.00,
  "currency": "AED",
  "customerId": "uuid",
  "customerEmail": "user@example.com",
  "customerPhone": "+971501234567",
  "billingName": "John Doe",
  "street": "123 Main St",
  "building": "Tower A",
  "floor": "3",
  "apartment": "301",
  "city": "Dubai",
  "state": "Dubai",
  "country": "AE",
  "postalCode": "00000"
}
```

> `appOrderId` is **not** required for the cart-first (auto-order) flow. Leave it null.

### 4.2 `POST /api/payments/initiate` — Response

```json
{
  "transactionId": "uuid",
  "methodType": "CARD",
  "amount": 250.00,
  "currency": "AED",
  "clientSecret": "paymob_client_secret_token",
  "checkoutUrl": "https://accept.paymob.com/unifiedcheckout/?publicKey=...&clientSecret=..."
}
```

| Field | Description |
|-------|-------------|
| `transactionId` | Store this locally to poll status later |
| `clientSecret` | Pass to Paymob JS SDK or redirect to `checkoutUrl` |
| `checkoutUrl` | Full URL — redirect customer directly if not using the JS SDK |

### 4.3 `GET /api/payments/transactions/{id}` — Response

```json
{
  "id": "uuid",
  "appOrderId": "uuid | null",
  "methodType": "CARD",
  "amount": 250.00,
  "amountCents": 25000,
  "currency": "AED",
  "status": "SUCCESS",
  "providerTransactionId": "paymob_txn_id",
  "failureReason": null,
  "failureCode": null,
  "createdAt": "2026-03-30T12:00:00Z",
  "updatedAt": "2026-03-30T12:05:00Z"
}
```

### 4.4 `POST /api/payments/refunds` — Request

```json
{
  "transactionId": "uuid",
  "amount": 100.00,
  "reason": "Customer request",
  "notes": "Item not received",
  "refundedBy": "admin_uuid"
}
```

### 4.5 `POST /api/payments/refunds` — Response

```json
{
  "id": "uuid",
  "transactionId": "uuid",
  "amount": 100.00,
  "amountCents": 10000,
  "currency": "AED",
  "status": "SUCCESS",
  "reason": "Customer request",
  "providerRefundId": "paymob_refund_id",
  "refundedBy": "admin_uuid",
  "createdAt": "2026-03-30T14:00:00Z"
}
```

---

## 5. Paymob Unified Checkout Integration

### Option A — Full Redirect (Simplest)

Redirect the customer directly to `checkoutUrl` returned by the initiate endpoint:

```js
// After calling POST /api/payments/initiate
const { checkoutUrl, transactionId } = response.data;

// Store transactionId in localStorage for later status polling
localStorage.setItem('pendingTransactionId', transactionId);

// Redirect customer to Paymob
window.location.href = checkoutUrl;
```

After the customer completes payment, Paymob redirects back to your configured `redirectUrl`. On that page, poll transaction status:

```js
const transactionId = localStorage.getItem('pendingTransactionId');
const res = await fetch(`/api/payments/transactions/${transactionId}`, {
  headers: { Authorization: `Bearer ${token}` }
});
const txn = await res.json();

if (txn.status === 'SUCCESS') {
  // Navigate to order history — order is already auto-created
  router.push('/orders');
} else if (txn.status === 'FAILED') {
  // Show failure message
}
```

### Option B — Paymob Embedded JS SDK

Include the Paymob script and pass `clientSecret` + `publicKey`:

```html
<script src="https://accept.paymob.com/v1/intention/pay.js"></script>
```

```js
const { clientSecret } = response.data;

PaymobCheckout.init({
  clientSecret: clientSecret,
  publicKey: process.env.PAYMOB_PUBLIC_KEY,
  onSuccess: (data) => {
    router.push('/orders');
  },
  onFailure: (error) => {
    console.error('Payment failed', error);
  }
});
```

---

## 6. Payment Method Configuration

### Supported Methods

| Method | Description | Currency |
|--------|-------------|----------|
| `CARD` | Credit/Debit card via Paymob iframe | AED (default) |
| `TABBY` | Buy Now Pay Later (Tabby) | AED |
| `TAMARA` | Buy Now Pay Later (Tamara) | AED |

### Selecting the Method at Checkout

Pass `methodType` in the initiate request. The backend selects the correct Paymob integration automatically.

```js
// Card checkout
{ methodType: "CARD", ... }

// Tabby BNPL
{ methodType: "TABBY", ... }

// Tamara BNPL
{ methodType: "TAMARA", ... }
```

### Showing Available Methods on the UI

You should conditionally show payment methods based on order amount and regional rules:

- **CARD**: Always available
- **TABBY**: Available if Tabby integration is active (check with your backend team)
- **TAMARA**: Available if Tamara integration is active

> Ask your backend team to expose a `/api/payments/available-methods` endpoint if dynamic method availability checking is needed.

### Environment Variables (Frontend)

```env
VITE_PAYMOB_PUBLIC_KEY=your_paymob_public_key
VITE_API_BASE_URL=https://api.yourdomain.com
```

> **Never** expose `secretKey` or `hmacSecret` on the frontend. These are backend-only.

---

## 7. Webhook Handling (Backend-only)

> This section is for awareness only. The frontend does not call or configure the webhook.

- **Endpoint**: `POST /api/payments/webhook?hmac=<sha512_hex>`
- **Security**: HMAC-SHA512 validated against Paymob's HMAC secret
- **Idempotent**: Duplicate events are safely ignored (keyed on `provider_txn_id`)
- **Effect on Orders**: On `SUCCESS` webhook → order is auto-created or transitioned to `PAID`

The frontend should **not** wait for the webhook synchronously. Instead, poll `GET /api/payments/transactions/{id}` until the status changes from `PENDING`.

### Polling Strategy (Recommended)

```js
async function pollTransactionStatus(transactionId, token, maxAttempts = 20) {
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(r => setTimeout(r, 3000)); // 3s interval
    const res = await fetch(`/api/payments/transactions/${transactionId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    const txn = await res.json();
    if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(txn.status)) {
      return txn;
    }
  }
  throw new Error('Payment status timeout');
}
```

---

## 8. Payment Status Reference

| Status | Meaning | Frontend Action |
|--------|---------|-----------------|
| `PENDING` | Transaction created, awaiting payment | Show loading / payment screen |
| `PROCESSING` | Paymob is processing | Show processing indicator |
| `SUCCESS` | Payment confirmed, order auto-created | Redirect to order history |
| `FAILED` | Payment declined | Show failure message, offer retry |
| `CANCELLED` | Cancelled by customer | Show cancellation message |
| `REFUNDED` | Full refund processed | Show refund confirmation |
| `PARTIALLY_REFUNDED` | Partial refund processed | Show partial refund details |

---

## 9. Error Handling

### Common Error Responses

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "User profile is incomplete. firstName, lastName, and phone are required."
}
```

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Transaction not found."
}
```

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Cart is not in CHECKED_OUT status."
}
```

### Handling in the Frontend

```js
const res = await fetch('/api/payments/initiate', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(payload)
});

if (!res.ok) {
  const err = await res.json();
  if (res.status === 400) {
    // Profile incomplete — prompt user to complete profile
    showProfileIncompleteModal();
  } else if (res.status === 422) {
    // Cart state issue — redirect to cart
    router.push('/cart');
  } else {
    showGenericError(err.message);
  }
  return;
}

const data = await res.json();
// Proceed with checkout
```

---

## 10. End-to-End Sequence Diagram

```
Customer          Frontend              Backend              Paymob
   |                  |                    |                    |
   |  [Select Method] |                    |                    |
   |----------------->|                    |                    |
   |                  |  POST /initiate    |                    |
   |                  |------------------->|                    |
   |                  |                    |  Create Intention  |
   |                  |                    |------------------->|
   |                  |                    |  clientSecret      |
   |                  |                    |<-------------------|
   |                  |  clientSecret +    |                    |
   |                  |  checkoutUrl       |                    |
   |                  |<-------------------|                    |
   |  [Redirect]      |                    |                    |
   |<-----------------|                    |                    |
   |                  |                    |                    |
   |          [Complete Payment on Paymob] |                    |
   |--------------------------------------------->|            |
   |                  |                    |  Webhook POST      |
   |                  |                    |<-------------------|
   |                  |                    |  HMAC Validate     |
   |                  |                    |  Update txn→SUCCESS|
   |                  |                    |  Auto-create Order |
   |  [Paymob redirect back]               |                    |
   |<---------------------------------------------|            |
   |                  |  Poll /transactions/{id}   |            |
   |                  |------------------->|                    |
   |                  |  status: SUCCESS   |                    |
   |                  |<-------------------|                    |
   |  [Order History] |                    |                    |
   |<-----------------|                    |                    |
```

---

*Last updated: 2026-03-30*
