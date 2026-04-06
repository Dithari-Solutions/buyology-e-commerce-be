# Mobile Integration Handoff — Cart, Checkout & Payment (Paymob)

**Backend Base URL:** `https://api-dev.dithari.com`  
**Auth:** JWT Bearer Token — `Authorization: Bearer <access_token>` (required on order endpoints).  
**All responses follow the envelope:**
```json
{
  "success": true,
  "message": "...",
  "data": { ... }
}
```

> **Read first:** Country & product docs are in `MOBILE_INTEGRATION_HANDOFF.md`.  
> This doc covers: Addresses → Cart → Checkout → Payment (Paymob) → Order Tracking.

---

## Table of Contents

1. [Addresses](#1-addresses)
2. [Cart](#2-cart)
3. [Checkout & Orders](#3-checkout--orders)
4. [Payment — Paymob Integration](#4-payment--paymob-integration)
5. [Order Tracking & Status](#5-order-tracking--status)
6. [Enums Reference](#6-enums-reference)
7. [Auth Notes](#7-auth-notes)
8. [End-to-End Flow Walkthrough](#8-end-to-end-flow-walkthrough)

---

## 1. Addresses

> Addresses must exist before checkout. The address `id` is required when initiating payment and creating orders. Addresses are geocoded server-side (lat/lng set automatically).

**Base path:** `/api/users/{userId}/addresses`  
**Auth:** NOT required (access is scoped to the user's own `userId`)

---

### 1.1 Save New Address

```
POST /api/users/{userId}/addresses
Auth: NOT required
Content-Type: application/json
```

**Request body:**
```json
{
  "firstName": "Ahmed",
  "lastName": "Al-Rashid",
  "phoneNumber": "+971501234567",
  "label": "HOME",
  "addressLine1": "Apartment 5, Building 12",
  "addressLine2": "Sheikh Zayed Road",
  "city": "Dubai",
  "state": "Dubai",
  "country": "AE",
  "postalCode": "00000",
  "isDefault": true
}
```

| Field          | Type    | Required | Notes                                         |
|----------------|---------|----------|-----------------------------------------------|
| `firstName`    | String  | YES      | Max 100 chars                                 |
| `lastName`     | String  | YES      | Max 100 chars                                 |
| `phoneNumber`  | String  | YES      | E.164 format, e.g. `+971501234567`            |
| `label`        | String  | NO       | `HOME`, `WORK`, `OTHER` — defaults to `HOME`  |
| `addressLine1` | String  | YES      | Max 255 chars                                 |
| `addressLine2` | String  | NO       | Max 255 chars                                 |
| `city`         | String  | YES      | Max 100 chars                                 |
| `state`        | String  | NO       | Max 100 chars                                 |
| `country`      | String  | YES      | ISO 3166-1 alpha-2 or alpha-3 (e.g., `"AE"`) |
| `postalCode`   | String  | NO       | Max 20 chars                                  |
| `isDefault`    | Boolean | NO       | `true` sets this as the default; clears previous default |

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "firstName": "Ahmed",
    "lastName": "Al-Rashid",
    "phoneNumber": "+971501234567",
    "phoneVerified": false,
    "label": "HOME",
    "addressLine1": "Apartment 5, Building 12",
    "addressLine2": "Sheikh Zayed Road",
    "city": "Dubai",
    "state": "Dubai",
    "country": "AE",
    "postalCode": "00000",
    "formattedAddress": "Apartment 5, Building 12, Sheikh Zayed Road, Dubai, AE",
    "latitude": 25.2048,
    "longitude": 55.2708,
    "addressVerified": true,
    "isDefault": true,
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:00:00Z"
  }
}
```

| Field              | Notes                                                |
|--------------------|------------------------------------------------------|
| `id`               | Use this UUID for checkout and payment requests      |
| `latitude/longitude` | Auto-set by server via Google Maps geocoding       |
| `addressVerified`  | `true` if geocoding succeeded                        |
| `isDefault`        | Only one address per user can be `true` at a time    |

---

### 1.2 List User Addresses

```
GET /api/users/{userId}/addresses
Auth: NOT required
```

Response is an array of `AddressResponse` (same structure as above).

---

### 1.3 Get Address by ID

```
GET /api/users/{userId}/addresses/{addressId}
Auth: NOT required
```

---

### 1.4 Set Default Address

```
PATCH /api/users/{userId}/addresses/{addressId}/default
Auth: NOT required
```

No request body needed. Sets the given address as default and clears the previous one.

---

### 1.5 Delete Address

```
DELETE /api/users/{userId}/addresses/{addressId}
Auth: NOT required
```

---

## 2. Cart

> The cart is per-user, identified by the user's `authCredentialId` (from the JWT `sub` claim). A cart is **single-country** — all items must be from stores in the same country. Country and currency are locked in when the first item is added.

**Base path:** `/api/cart/{authCredentialId}`  
**Auth:** NOT required (but the `authCredentialId` must match the logged-in user)

---

### 2.1 Get Active Cart

```
GET /api/cart/{authCredentialId}
Auth: NOT required
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "cart-uuid",
    "authCredentialId": "auth-cred-uuid",
    "status": "ACTIVE",
    "totalPrice": 7998.00,
    "countryCode": "ARE",
    "currency": "AED",
    "items": [
      {
        "id": "cart-item-uuid",
        "productId": "product-uuid",
        "productSku": "APL-IPH15-128",
        "variantId": "variant-uuid",
        "variantSku": "APL-IPH15-128-MDN",
        "storeId": "store-uuid",
        "quantity": 2,
        "unitPrice": 3999.00,
        "totalPrice": 7998.00,
        "quickDelivery": true,
        "selectedSpecs": [
          {
            "specOptionId": "uuid",
            "groupCode": "storage",
            "value": "128",
            "unit": "GB",
            "additionalPrice": 0.00,
            "colorCode": null
          }
        ],
        "createdAt": "2026-01-01T00:00:00Z",
        "updatedAt": "2026-01-01T00:00:00Z"
      }
    ],
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:00:00Z"
  }
}
```

| Field          | Notes                                                                  |
|----------------|------------------------------------------------------------------------|
| `id`           | Cart UUID — pass this to payment initiation                            |
| `status`       | `ACTIVE`, `CHECKED_OUT`, `ABANDONED`                                  |
| `totalPrice`   | Sum of all item totals including spec option price adjustments         |
| `countryCode`  | ISO 3166-1 alpha-3 (e.g., `"ARE"`)                                    |
| `currency`     | Locked when first item is added                                        |
| `quickDelivery`| `true` if the store is within ~12.5 km of the user (express delivery) |

---

### 2.2 Add Item to Cart

```
POST /api/cart/{authCredentialId}/items
Auth: NOT required
Content-Type: application/json
```

**Request body:**
```json
{
  "storeId": "store-uuid",
  "productId": "product-uuid",
  "variantId": "variant-uuid",
  "specOptionIds": ["spec-option-uuid-1", "spec-option-uuid-2"],
  "quantity": 1
}
```

| Field           | Type    | Required | Notes                                                                  |
|-----------------|---------|----------|------------------------------------------------------------------------|
| `storeId`       | UUID    | YES      | From `product.storeOptions[].storeId`                                  |
| `productId`     | UUID    | YES      |                                                                        |
| `variantId`     | UUID    | NO       | Required if the product has variants (e.g., 128GB Midnight)            |
| `specOptionIds` | UUID[]  | NO       | Selected spec option UUIDs (e.g., color, storage)                     |
| `quantity`      | Integer | NO       | Min 1, defaults to 1                                                   |

> **Country enforcement:** The backend rejects adding items from a different country than the cart's current `countryCode`. If the user wants to shop from a different country, they must clear the cart first.

**Response:** Updated `CartResponse` (same as 2.1).

---

### 2.3 Update Item Quantity

```
PATCH /api/cart/{authCredentialId}/items/{cartItemId}
Auth: NOT required
Content-Type: application/json
```

**Request body:**
```json
{
  "quantity": 3
}
```

`quantity` must be ≥ 1. To remove an item, use the DELETE endpoint below.

**Response:** Updated `CartResponse`.

---

### 2.4 Remove Item from Cart

```
DELETE /api/cart/{authCredentialId}/items/{cartItemId}
Auth: NOT required
```

**Response:** Updated `CartResponse`.

---

### 2.5 Clear Cart

```
DELETE /api/cart/{authCredentialId}
Auth: NOT required
```

Removes all items. Use when the user switches country or abandons the cart.

**Response:** Updated `CartResponse` with empty `items` array.

---

## 3. Checkout & Orders

> After cart is ready, create an order. **Auth is required for all order endpoints.**  
> The JWT `sub` (subject) claim is the `authCredentialId` passed to cart endpoints.

---

### 3.1 Create Order

```
POST /api/orders
Auth: REQUIRED (JWT Bearer)
Content-Type: application/json
```

**Request body:**
```json
{
  "cartId": "cart-uuid",
  "addressId": "address-uuid",
  "deliveryMethod": "LOCAL_EXPRESS",
  "shippingFee": 10.00,
  "couponCode": "SAVE10",
  "notes": "Leave at door"
}
```

| Field            | Type       | Required | Notes                                                                 |
|------------------|------------|----------|-----------------------------------------------------------------------|
| `cartId`         | UUID       | YES      | Must be an `ACTIVE` cart owned by the authenticated user              |
| `addressId`      | UUID       | YES      | Must belong to the user                                               |
| `deliveryMethod` | String     | NO       | `LOCAL_EXPRESS` or `INTERNATIONAL` — auto-determined if omitted       |
| `shippingFee`    | BigDecimal | NO       | Optional override; server may calculate its own                       |
| `couponCode`     | String     | NO       | Discount code                                                         |
| `notes`          | String     | NO       | Delivery instructions                                                 |

**Delivery method auto-determination:** If omitted, the server checks if all cart items' stores are within ~12.5 km of the delivery address — if yes, `LOCAL_EXPRESS`; otherwise `INTERNATIONAL`.

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "order-uuid",
    "userId": "user-uuid",
    "cartId": "cart-uuid",
    "paymentTransactionId": null,
    "courierUserId": null,
    "deliveryMethod": "LOCAL_EXPRESS",
    "status": "PENDING_PAYMENT",
    "recipientFirstName": "Ahmed",
    "recipientLastName": "Al-Rashid",
    "recipientPhone": "+971501234567",
    "addressLine1": "Apartment 5, Building 12",
    "addressLine2": "Sheikh Zayed Road",
    "city": "Dubai",
    "state": "Dubai",
    "country": "AE",
    "postalCode": "00000",
    "deliveryLatitude": 25.2048,
    "deliveryLongitude": 55.2708,
    "subtotal": 7998.00,
    "shippingFee": 10.00,
    "discount": 0.00,
    "totalAmount": 8008.00,
    "currency": "AED",
    "countryCode": "ARE",
    "couponCode": null,
    "trackingCode": null,
    "carrierName": null,
    "paidAt": null,
    "shippedAt": null,
    "deliveredAt": null,
    "cancelledAt": null,
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:00:00Z",
    "items": [
      {
        "id": "order-item-uuid",
        "productId": "product-uuid",
        "variantId": "variant-uuid",
        "storeId": "store-uuid",
        "productSku": "APL-IPH15-128",
        "variantSku": "APL-IPH15-128-MDN",
        "quantity": 2,
        "unitPrice": 3999.00,
        "totalPrice": 7998.00,
        "createdAt": "2026-01-01T00:00:00Z"
      }
    ],
    "trackingHistory": []
  }
}
```

> **Note the `id` (orderId) — pass this as `appOrderId` to payment initiation.**

---

### 3.2 Get Order Detail

```
GET /api/orders/{orderId}
Auth: REQUIRED (JWT Bearer)
```

Returns full `OrderResponse` (same structure as above including `trackingHistory`).

---

### 3.3 List My Orders

```
GET /api/orders
Auth: REQUIRED (JWT Bearer)
```

Returns array of `OrderSummaryResponse`:

```json
{
  "success": true,
  "data": [
    {
      "id": "order-uuid",
      "userId": "user-uuid",
      "deliveryMethod": "LOCAL_EXPRESS",
      "status": "PAID",
      "totalAmount": 8008.00,
      "currency": "AED",
      "countryCode": "ARE",
      "trackingCode": null,
      "carrierName": null,
      "recipientFirstName": "Ahmed",
      "recipientLastName": "Al-Rashid",
      "city": "Dubai",
      "country": "AE",
      "paidAt": "2026-01-01T00:05:00Z",
      "deliveredAt": null,
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-01-01T00:05:00Z"
    }
  ]
}
```

---

## 4. Payment — Paymob Integration

> The app uses the **Paymob Unified Checkout** (Intention API). The flow is:  
> **Initiate → Open WebView/Browser → Paymob handles card UI → Webhook updates server → App polls or receives push.**

### 4.1 Full Payment Flow

```
Mobile App                          Backend                         Paymob
    │                                   │                               │
    │  POST /api/payments/initiate       │                               │
    │──────────────────────────────────>│                               │
    │                                   │  POST /v1/intention/          │
    │                                   │──────────────────────────────>│
    │                                   │  ← clientSecret + intentionId │
    │  ← { clientSecret, checkoutUrl }  │                               │
    │                                   │                               │
    │  Open checkoutUrl in WebView      │                               │
    │──────────────────────────────────────────────────────────────────>│
    │                                   │      User enters card details  │
    │                                   │  ← Webhook POST /api/payments/webhook
    │                                   │<──────────────────────────────│
    │                                   │  Order marked PAID             │
    │  Poll GET /api/payments/transactions/{transactionId}               │
    │──────────────────────────────────>│                               │
    │  ← { status: "SUCCESS" }          │                               │
```

---

### 4.2 Initiate Payment

```
POST /api/payments/initiate
Auth: NOT required (but pass customerId from JWT sub)
Content-Type: application/json
```

**Request body:**
```json
{
  "appOrderId": "order-uuid",
  "cartId": "cart-uuid",
  "addressId": "address-uuid",
  "deliveryMethod": "LOCAL_EXPRESS",
  "shippingFee": 10.00,

  "methodType": "CARD",
  "amount": 8008.00,
  "currency": "AED",

  "customerId": "user-uuid",
  "customerEmail": "ahmed@example.com",
  "customerPhone": "+971501234567",
  "billingName": "Ahmed Al-Rashid",

  "billingApartment": "5",
  "billingFloor": "2",
  "billingStreet": "Sheikh Zayed Road",
  "billingBuilding": "12",
  "billingCity": "Dubai",
  "billingCountry": "AE",
  "billingState": "Dubai",
  "billingPostalCode": "00000"
}
```

| Field              | Type       | Required | Notes                                                             |
|--------------------|------------|----------|-------------------------------------------------------------------|
| `appOrderId`       | UUID       | NO       | Pass the order `id` from step 3.1. Omit for cart-first flow.     |
| `cartId`           | UUID       | YES      | Active cart UUID                                                  |
| `addressId`        | UUID       | YES      | UUID of saved address (billing/delivery data)                    |
| `deliveryMethod`   | String     | NO       | `LOCAL_EXPRESS` or `INTERNATIONAL`; auto-determined if omitted    |
| `shippingFee`      | BigDecimal | NO       |                                                                   |
| `methodType`       | String     | YES      | `CARD`, `TABBY`, or `TAMARA`                                      |
| `amount`           | BigDecimal | YES      | Full amount the user pays. Must be ≥ 0.01.                       |
| `currency`         | String     | YES      | ISO 4217 — must match cart currency (e.g., `"AED"`)              |
| `customerId`       | UUID       | YES      | Authenticated user's UUID                                         |
| `customerEmail`    | String     | YES      |                                                                   |
| `customerPhone`    | String     | NO       | E.164 format                                                      |
| `billingName`      | String     | YES      | Full name as it appears on card                                   |
| `billing*`         | String     | NO       | Billing address fields — falls back to saved address if omitted  |

**Response:**
```json
{
  "success": true,
  "data": {
    "transactionId": "txn-uuid",
    "methodType": "CARD",
    "amount": 8008.00,
    "currency": "AED",
    "clientSecret": "pk_live_...",
    "checkoutUrl": "https://uae.paymob.com/unifiedcheckout/?publicKey=pk_live_...&clientSecret=..."
  }
}
```

| Field          | Notes                                                                          |
|----------------|--------------------------------------------------------------------------------|
| `transactionId`| Store this — poll it to check payment result                                   |
| `clientSecret` | Single-use token; consumed when the WebView loads the `checkoutUrl`            |
| `checkoutUrl`  | **Open this URL in a WebView or in-app browser.** Paymob renders the payment UI. |

---

### 4.3 Opening the Checkout (WebView)

1. Open `checkoutUrl` in an in-app WebView or `SFSafariViewController` / `Chrome Custom Tab`.
2. Paymob handles the entire card entry / 3DS / BNPL flow.
3. After completion, Paymob redirects to a success/failure URL (configure in your Paymob dashboard — use a deep link like `dithari://payment/result`).
4. Intercept this redirect in the WebView delegate and dismiss the view.
5. **Do not rely solely on the redirect** — poll the transaction status to confirm (step 4.4).

**WebView redirect URL patterns to intercept:**
```
dithari://payment/success   → poll transaction → show success screen
dithari://payment/failure   → poll transaction → show failure / retry screen
```

> Configure these redirect URLs in the Paymob merchant dashboard under your integration settings.

---

### 4.4 Poll Transaction Status

```
GET /api/payments/transactions/{transactionId}
Auth: NOT required
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "txn-uuid",
    "appOrderId": "order-uuid",
    "cartId": "cart-uuid",
    "methodType": "CARD",
    "amount": 8008.00,
    "currency": "AED",
    "status": "SUCCESS",
    "customerId": "user-uuid",
    "customerEmail": "ahmed@example.com",
    "failureReason": null,
    "failureCode": null,
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:05:00Z"
  }
}
```

| `status` value   | Meaning                                          | Action                              |
|------------------|--------------------------------------------------|-------------------------------------|
| `PENDING`        | Waiting for Paymob webhook                       | Keep polling (every 2–3 seconds)    |
| `PROCESSING`     | Paymob is processing (3DS or BNPL)               | Keep polling                        |
| `SUCCESS`        | Payment confirmed, order is now `PAID`           | Navigate to order confirmation       |
| `FAILED`         | Payment declined — see `failureReason`           | Show error, offer retry             |
| `CANCELLED`      | User cancelled                                   | Return to cart                      |

**Polling strategy:** Poll every 3 seconds for up to 2 minutes. If still `PENDING` after 2 minutes, show "Payment is being processed — check your orders later."

---

### 4.5 Get Transactions for an Order

```
GET /api/payments/orders/{appOrderId}/transactions
Auth: NOT required
```

Returns an array of `TransactionResponse` for all payment attempts on a given order (includes retries).

---

### 4.6 Payment Method Types

| `methodType` | Description                        | Availability  |
|--------------|------------------------------------|---------------|
| `CARD`       | Credit / debit card (Visa, MC, Amex) | All countries |
| `TABBY`      | Buy Now Pay Later — Tabby           | UAE, KSA      |
| `TAMARA`     | Buy Now Pay Later — Tamara          | UAE, KSA      |

Show BNPL options (Tabby/Tamara) only for countries where they are supported. The backend will reject an unsupported method with a `400` error.

---

### 4.7 Webhook (Server-side — for reference only)

> Mobile apps do **not** call this. It is called by Paymob's servers.

```
POST /api/payments/webhook
Auth: NOT required (HMAC-validated by backend)
```

When the webhook fires with `success: true`, the backend:
1. Validates HMAC signature.
2. Marks the `PaymentTransaction` as `SUCCESS`.
3. Transitions the order from `PENDING_PAYMENT` → `PAID`.
4. For `LOCAL_EXPRESS` orders: automatically dispatches to the courier system.
5. Marks the cart as `ABANDONED`.

The mobile app learns about this outcome by **polling** `GET /api/payments/transactions/{transactionId}`.

---

## 5. Order Tracking & Status

### 5.1 Order Status Values

| Status              | Who Sets It        | Meaning                                              |
|---------------------|--------------------|------------------------------------------------------|
| `PENDING_PAYMENT`   | System             | Order created, payment not yet confirmed             |
| `PAID`              | System (webhook)   | Payment confirmed                                    |
| `PROCESSING`        | Admin              | International order being prepared                   |
| `COURIER_ASSIGNED`  | Admin              | Local express: courier has been assigned             |
| `PICKED_UP`         | Courier            | Courier picked up the package                        |
| `SHIPPED`           | Admin              | International: carrier tracking code assigned        |
| `IN_TRANSIT`        | Admin / Courier    | Package is on its way                                |
| `DELIVERED`         | Courier / Admin    | Package delivered — terminal                         |
| `CANCELLED`         | Admin / System     | Order cancelled — terminal                           |
| `FAILED`            | Courier / Admin    | Delivery failed — terminal                           |

### 5.2 State Machine

```
PENDING_PAYMENT ──► PAID ──► PROCESSING ──► SHIPPED ──► IN_TRANSIT ──► DELIVERED
                      │                                       ▲
                      └──► COURIER_ASSIGNED ──► PICKED_UP ───┘
                      │
                      └──► CANCELLED (from PENDING_PAYMENT or PAID only)
                                                         └──► FAILED (from IN_TRANSIT)
```

### 5.3 Tracking History

Each order contains a `trackingHistory` array of events:

```json
"trackingHistory": [
  {
    "id": "event-uuid",
    "status": "COURIER_ASSIGNED",
    "notes": "Your order has been assigned to a courier.",
    "latitude": null,
    "longitude": null,
    "locationDescription": null,
    "actorId": "admin-uuid",
    "actorRole": "ADMIN",
    "createdAt": "2026-01-01T00:10:00Z"
  },
  {
    "id": "event-uuid",
    "status": "PICKED_UP",
    "notes": "Package picked up.",
    "latitude": 25.2050,
    "longitude": 55.2710,
    "locationDescription": "Warehouse — Dubai South",
    "actorId": "courier-uuid",
    "actorRole": "COURIER",
    "createdAt": "2026-01-01T00:30:00Z"
  }
]
```

| `actorRole`  | Who created this event        |
|--------------|-------------------------------|
| `SYSTEM`     | Automatic (e.g., webhook)     |
| `ADMIN`      | Back-office staff             |
| `COURIER`    | Delivery courier              |

Use `latitude`/`longitude` (when non-null) to show the courier's live location on a map.

### 5.4 Display Logic by Delivery Method

| Delivery Method  | How to display progress                                         |
|------------------|-----------------------------------------------------------------|
| `LOCAL_EXPRESS`  | Show courier location on map when lat/lng available             |
| `INTERNATIONAL`  | Show `trackingCode` + `carrierName` for external carrier lookup |

---

## 6. Enums Reference

### CartStatus
| Value          | Meaning                              |
|----------------|--------------------------------------|
| `ACTIVE`       | User can add/remove items            |
| `CHECKED_OUT`  | Order created from this cart         |
| `ABANDONED`    | Payment completed (post-payment)     |

### PaymentStatus
| Value                | Meaning                              |
|----------------------|--------------------------------------|
| `PENDING`            | Awaiting Paymob webhook              |
| `PROCESSING`         | Paymob processing (3DS/BNPL)         |
| `SUCCESS`            | Confirmed                            |
| `FAILED`             | Declined                             |
| `CANCELLED`          | User-cancelled                       |
| `REFUNDED`           | Fully refunded                       |
| `PARTIALLY_REFUNDED` | Partial refund issued                |

### PaymentMethodType
| Value    | Description                  |
|----------|------------------------------|
| `CARD`   | Credit / debit card          |
| `TABBY`  | BNPL — Tabby                 |
| `TAMARA` | BNPL — Tamara                |

### DeliveryMethod
| Value            | Description                      |
|------------------|----------------------------------|
| `LOCAL_EXPRESS`  | ~12.5 km radius, courier delivery |
| `INTERNATIONAL`  | Cross-border, carrier shipping    |

### AddressLabel
| Value   |
|---------|
| `HOME`  |
| `WORK`  |
| `OTHER` |

---

## 7. Auth Notes

| Endpoint                             | JWT Required | Notes                                                     |
|--------------------------------------|--------------|-----------------------------------------------------------|
| `POST /api/users/{id}/addresses`     | NO           | Scoped by userId in path                                  |
| `GET /api/users/{id}/addresses`      | NO           |                                                           |
| `GET /api/cart/{authCredentialId}`   | NO           | authCredentialId = JWT sub claim                          |
| `POST /api/cart/{id}/items`          | NO           |                                                           |
| `POST /api/payments/initiate`        | NO           | Pass customerId manually from JWT                         |
| `GET /api/payments/transactions/{id}`| NO           |                                                           |
| **`POST /api/orders`**               | **YES**      | JWT required                                              |
| **`GET /api/orders`**                | **YES**      | JWT required                                              |
| **`GET /api/orders/{id}`**           | **YES**      | JWT required                                              |
| `POST /api/payments/webhook`         | NO           | Paymob HMAC-validated — do not call from mobile           |

**Getting the `authCredentialId`:** Decode your JWT and read the `sub` claim. This is the value used in all cart and address endpoints as `{userId}` / `{authCredentialId}`.

---

## 8. End-to-End Flow Walkthrough

### Step 1 — Manage Addresses (Profile / Checkout Screen)

```
// List existing addresses
GET /api/users/{userId}/addresses

// If none, prompt user to add one
POST /api/users/{userId}/addresses
→ save returned address.id
```

### Step 2 — Build the Cart

```
// Add item (after user taps "Add to Cart" on product screen)
POST /api/cart/{authCredentialId}/items
{
  "storeId": "...",          // from product.storeOptions[0].storeId
  "productId": "...",
  "variantId": "...",        // if user selected a variant
  "specOptionIds": [...],    // if user selected spec options (color, etc.)
  "quantity": 1
}

// View cart
GET /api/cart/{authCredentialId}
→ display items, totalPrice, currency

// Update quantity
PATCH /api/cart/{authCredentialId}/items/{cartItemId}
{ "quantity": 2 }

// Remove item
DELETE /api/cart/{authCredentialId}/items/{cartItemId}
```

### Step 3 — Checkout (Order Creation)

```
// User taps "Proceed to Checkout"
// Show address selector → user picks or adds address

POST /api/orders
Authorization: Bearer <jwt>
{
  "cartId": "...",
  "addressId": "...",
  "notes": "Ring doorbell"
}
→ save order.id (= appOrderId), order.totalAmount, order.currency
```

### Step 4 — Payment

```
// Show payment method selector (CARD / TABBY / TAMARA)
// User picks CARD

POST /api/payments/initiate
{
  "appOrderId": "<order.id>",
  "cartId": "<cart.id>",
  "addressId": "<address.id>",
  "methodType": "CARD",
  "amount": <order.totalAmount>,
  "currency": "<order.currency>",
  "customerId": "<userId>",
  "customerEmail": "...",
  "billingName": "..."
}
→ save transactionId, open checkoutUrl in WebView
```

### Step 5 — WebView Handling

```
// Open checkoutUrl in WebView
// Intercept deep link redirect:
//   dithari://payment/success → proceed to step 6
//   dithari://payment/failure → show error, offer retry

// ALWAYS confirm with backend:
GET /api/payments/transactions/{transactionId}
→ poll every 3 seconds until status != "PENDING" and != "PROCESSING"
```

### Step 6 — Confirm & Show Result

```
if status == "SUCCESS":
  GET /api/orders/{orderId}
  → show order confirmation screen with:
    - order.id (order number)
    - order.status ("PAID")
    - order.totalAmount + currency
    - order.deliveryMethod
    - estimated delivery info

if status == "FAILED":
  → show error: transactionResponse.failureReason
  → offer "Try Again" (re-initiate payment with same orderId)

if status == "CANCELLED":
  → return to cart
```

### Step 7 — Order Tracking Screen

```
GET /api/orders/{orderId}
→ render trackingHistory as a timeline
→ if LOCAL_EXPRESS and latest event has lat/lng:
    show courier location on map
→ if INTERNATIONAL and trackingCode is set:
    show carrier name + tracking code with a "Track Package" deep link
```

---

## Notes for the Mobile Dev

- **Cart is single-country.** If the user changes their selected country, call `DELETE /api/cart/{authCredentialId}` first.
- **`authCredentialId` = JWT `sub` claim.** Use this for all cart/address path params.
- **Amount in payment request must match `order.totalAmount`.** Don't recalculate client-side — read it from the order response.
- **Cart-first flow (no order pre-creation):** You may omit `POST /api/orders` and pass only `cartId` to payment initiation (no `appOrderId`). The backend auto-creates the order on successful payment. This is simpler but you won't have an `orderId` until after payment.
- **Never trust the WebView redirect alone.** Always poll the transaction to confirm payment status.
- **Paymob `clientSecret` is single-use.** If the user cancels and wants to retry, call `POST /api/payments/initiate` again to get a new `clientSecret` and `checkoutUrl`.
- **BNPL (Tabby/Tamara):** Same flow as CARD — Paymob handles the BNPL UI. Just change `methodType`.
- **Refunds** are admin-initiated only — no customer-facing refund endpoint in mobile scope.
- **Order history pagination:** `GET /api/orders` returns all orders. Implement client-side pagination or request the backend to add a `page`/`size` param if the list grows large.
