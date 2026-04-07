# Website: Local-Only Purchasing & Delivery — Frontend Integration Guide

This document covers every API, request field, response field, enum, and UI rule the frontend needs to implement the local-country purchase restriction and delivery logic.

---

## 0. Common Conventions

- All endpoints are relative to the API base URL (e.g. `https://api.yourdomain.com`)
- All responses are wrapped: `{ "success": true, "message": "...", "data": { ... } }`
- UUIDs are strings in `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` format
- Prices are `BigDecimal` — display with the `currency` field returned alongside them
- Country codes: ISO 3166-1 alpha-3 (e.g. `UAE`, `AZE`, `SAU`)
- Currency codes: ISO 4217 (e.g. `AED`, `AZN`, `SAR`)
- Language codes: `EN`, `AZ`, `AR`
- Authenticated endpoints require a Bearer JWT in `Authorization: Bearer <token>`

---

## 1. User Profile — Country & Currency Preference

The user's `selectedCountryCode` drives the purchase restriction. Always load the profile on app boot.

### GET `/api/users/{userId}/profile`

**Response fields relevant to local-purchase logic:**

| Field | Type | Description |
|---|---|---|
| `selectedCountryCode` | `String` | ISO alpha-3 country the user can buy from (e.g. `"UAE"`) |
| `preferredCurrency` | `String` | ISO 4217 currency for price display (e.g. `"AED"`) |
| `preferredLanguage` | `String` | UI language (`"EN"`, `"AZ"`, `"AR"`) |
| `paymentReady` | `boolean` | `true` when firstName + lastName + phoneNumber + at least one address are all set |
| `missingFields` | `String[]` | List of what's missing when `paymentReady` is `false`. Show a "complete profile" banner. |

---

### PATCH `/api/users/{userId}/profile/country-preference`

Use this when the user changes their home country from the profile/settings page.

**Query params:**

| Param | Required | Description |
|---|---|---|
| `countryCode` | yes | ISO alpha-3 (e.g. `UAE`) |
| `currency` | no | Override display currency. If omitted, backend auto-derives from country. |

**Example:** `PATCH /api/users/{userId}/profile/country-preference?countryCode=UAE`
→ `preferredCurrency` will auto-set to `AED`.

**Example with override:** `PATCH /api/users/{userId}/profile/country-preference?countryCode=UAE&currency=AZN`
→ Price shown in AZN, but user is purchasing from UAE stores.

---

### PATCH `/api/users/{userId}/profile`

Update other profile fields (all optional, send only what changed):

```json
{
  "firstName": "Ali",
  "lastName": "Hasanov",
  "phoneNumber": "+971501234567",
  "dateOfBirth": "1995-06-15",
  "selectedCountryCode": "UAE",
  "preferredCurrency": "AED",
  "preferredLanguage": "EN"
}
```

> `phoneNumber` must be E.164 format: `+[country-code][number]`

---

## 2. Product Browsing

### GET `/api/product`

**Query params:**

| Param | Required | Description |
|---|---|---|
| `lang` | yes | Language for translations: `EN`, `AZ`, `AR` |
| `countryCode` | no | Scope results to stores in this country and include pricing |
| `currency` | no | Convert prices to this currency. Defaults to country's native currency. |
| `lat` | no | Customer latitude — enables `expressDelivery` badge on results |
| `lng` | no | Customer longitude — enables `expressDelivery` badge on results |

**Also available:**
- `GET /api/product/{productId}` — single product, same params
- `GET /api/product/category/{categoryId}` — by category, same params
- `GET /api/product/search` — search/filter, same params + filter fields

**Key response fields on `ProductResponse`:**

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Product ID |
| `title` | String | Localized title |
| `storeId` | UUID | **Use this as `storeId` when adding to cart.** The cheapest store in the country. Null if no `countryCode` supplied. |
| `storePrice` | BigDecimal | Lowest price in the country, in `currency`. Null if no `countryCode`. |
| `currency` | String | Currency of `storePrice`. |
| `availableInSelectedCountry` | Boolean | Whether any store in the country carries this product. Null if no `countryCode`. |
| `expressDelivery` | Boolean | `true` = store is ≤12.5 km away (30-min delivery). Null if no lat/lng. |
| `storeOptions` | `StoreOptionDto[]` | All stores in the country with their own price + delivery badge (for "choose a store" UI) |
| `variants` | `VariantDto[]` | Pre-built variants (e.g. refurb grades). Use `variantId` in add-to-cart. |
| `specs` | `SpecGroupDto[]` | Configurable spec groups. User picks one `SpecOptionDto` per group → pass as `specOptionIds` in add-to-cart. |

**`StoreOptionDto` fields:**

| Field | Type | Description |
|---|---|---|
| `storeId` | UUID | |
| `storePrice` | BigDecimal | |
| `currency` | String | |
| `expressDelivery` | Boolean | True = within 30-min delivery radius |

---

## 3. Cart

### GET `/api/cart/{authCredentialId}?lat={lat}&lng={lng}`

Pass `lat`/`lng` to get the `quickDelivery` badge on each cart item.

**`CartResponse` fields:**

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Cart ID — needed for order creation |
| `authCredentialId` | UUID | |
| `status` | String | `ACTIVE`, `CHECKED_OUT` |
| `totalPrice` | BigDecimal | Sum of all items |
| `currency` | String | Display currency |
| `countryCode` | String | Country this cart is locked to |
| `items` | `CartItemResponse[]` | See below |

**`CartItemResponse` fields:**

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Cart item ID — needed for update/remove |
| `productId` | UUID | |
| `productSku` | String | |
| `variantId` | UUID | Null if no variant |
| `variantSku` | String | |
| `storeId` | UUID | Which store this item is from |
| `quantity` | Integer | |
| `unitPrice` | BigDecimal | Price per unit |
| `totalPrice` | BigDecimal | `unitPrice × quantity` |
| `quickDelivery` | Boolean | `true` = store is within 30-min radius |
| `selectedSpecs` | `CartItemSpecSelectionResponse[]` | Chosen spec options |

---

### POST `/api/cart/{authCredentialId}/items`

**Request body:**

```json
{
  "storeId": "<UUID>",
  "productId": "<UUID>",
  "variantId": "<UUID or null>",
  "specOptionIds": ["<UUID>", "<UUID>"],
  "quantity": 1
}
```

| Field | Required | Description |
|---|---|---|
| `storeId` | yes | From `product.storeId` (or chosen from `storeOptions`) |
| `productId` | yes | |
| `variantId` | no | Required only for variant-based products |
| `specOptionIds` | no | Required for DIY/configurable products — one per spec group |
| `quantity` | no | Defaults to `1` |

> **403 Forbidden** — returned when the store's country does not match the user's `selectedCountryCode`.
> Show: *"You can only purchase products from stores in [selectedCountry]. Go to Profile → Country Settings to change your home country."*

---

### PATCH `/api/cart/{authCredentialId}/items/{cartItemId}`

```json
{ "quantity": 3 }
```

---

### DELETE `/api/cart/{authCredentialId}/items/{cartItemId}`

Remove a single item.

---

### DELETE `/api/cart/{authCredentialId}`

Clear all items.

---

### POST `/api/cart/{authCredentialId}/checkout`

Transitions cart status from `ACTIVE` → `CHECKED_OUT`. Call this before creating an order.
Returns an updated `CartResponse` with `status: "CHECKED_OUT"`.

---

## 4. Delivery Classification

The backend computes delivery method and fee based on store-to-customer distance. The frontend must display the pre-computed values — do **not** calculate these independently.

| Distance | `deliveryMethod` | Estimated Time |
|---|---|---|
| ≤ 12.5 km | `EXPRESS_DELIVERY` | "Within 30 minutes" |
| > 12.5 km | `REGULAR_ORDER` | "2–3 business days" |

**Express delivery fee (AED-based, converted to cart currency):**

| Cart Total | Fee |
|---|---|
| < 150 AED equivalent | 15 AED |
| ≥ 150 AED equivalent | 10 AED |

> The `shippingFee` is returned in the `CartResponse` and `OrderResponse`. Always display what the backend returns — do not hardcode these amounts.

---

## 5. Order Creation

### POST `/api/orders`

**Headers:**
- `Authorization: Bearer <token>`
- `X-Auth-Credential-Id: <authCredentialId>`

**Request body:**

```json
{
  "cartId": "<UUID>",
  "addressId": "<UUID>",
  "deliveryMethod": "EXPRESS_DELIVERY",
  "shippingFee": 15.00,
  "couponCode": "SAVE10",
  "notes": "Leave at door"
}
```

| Field | Required | Description |
|---|---|---|
| `cartId` | yes | From `CartResponse.id` after checkout |
| `addressId` | yes | From user's saved addresses |
| `deliveryMethod` | yes | `EXPRESS_DELIVERY` or `REGULAR_ORDER` |
| `shippingFee` | no | Pass the fee shown to the user (from cart summary) |
| `couponCode` | no | |
| `notes` | no | Delivery notes |

**`deliveryMethod` enum values:**

| Value | When to use |
|---|---|
| `EXPRESS_DELIVERY` | Store is within ≤12.5 km (`expressDelivery: true`) |
| `REGULAR_ORDER` | Store is > 12.5 km away |

---

**`OrderResponse` key fields:**

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Order ID |
| `status` | `OrderStatus` | Current order status (see below) |
| `deliveryMethod` | `DeliveryMethod` | `EXPRESS_DELIVERY` or `REGULAR_ORDER` |
| `subtotal` | BigDecimal | Items total before shipping |
| `shippingFee` | BigDecimal | Delivery fee |
| `discount` | BigDecimal | Coupon/discount applied |
| `totalAmount` | BigDecimal | `subtotal + shippingFee - discount` |
| `currency` | String | |
| `estimatedDeliveryTime` | String | Human-readable, e.g. `"Within 30 minutes"` |
| `trackingCode` | String | Set when shipped (regular orders) |
| `carrierName` | String | Set when shipped (regular orders) |
| `paidAt` | Instant | |
| `shippedAt` | Instant | |
| `deliveredAt` | Instant | |
| `items` | `OrderItemResponse[]` | Line items snapshot |
| `trackingHistory` | `TrackingEventResponse[]` | Tracking events for order status timeline |

**`OrderStatus` enum — all possible values:**

| Status | Flow | Meaning |
|---|---|---|
| `PENDING_PAYMENT` | Both | Order created, awaiting payment |
| `PAID` | Both | Payment confirmed via webhook |
| `PROCESSING` | Regular | Admin acknowledged, preparing shipment |
| `COURIER_ASSIGNED` | Express | A courier has been assigned |
| `PICKED_UP` | Express | Courier picked up from store |
| `SHIPPED` | Regular | Dispatched, tracking code set |
| `IN_TRANSIT` | Both | On the way to customer |
| `DELIVERED` | Both | Successfully delivered |
| `CANCELLED` | Both | Cancelled from `PENDING_PAYMENT` or `PAID` |
| `FAILED` | Both | Delivery failed |

---

### GET `/api/orders/{orderId}`

Get a single order. Returns `404` if the order doesn't belong to the authenticated user.

### GET `/api/orders?page=0&size=20`

List all orders for the authenticated user, paginated, most recent first.

---

## 6. Payment

### POST `/api/payments/initiate`

Call after creating the order. Pass the order ID, cart, address, and billing details.

**Request body:**

```json
{
  "appOrderId": "<UUID>",
  "cartId": "<UUID>",
  "addressId": "<UUID>",
  "deliveryMethod": "EXPRESS_DELIVERY",
  "shippingFee": 15.00,
  "methodType": "CARD",
  "amount": 115.00,
  "currency": "AED",
  "customerId": "<UUID>",
  "customerEmail": "user@example.com",
  "customerPhone": "+971501234567",
  "billingName": "Ali Hasanov",
  "billingStreet": "Sheikh Zayed Rd",
  "billingCity": "Dubai",
  "billingCountry": "UAE",
  "billingState": "Dubai",
  "billingPostalCode": "00000",
  "billingApartment": "Apt 5",
  "billingFloor": "3",
  "billingBuilding": "Tower A"
}
```

| Field | Required | Description |
|---|---|---|
| `cartId` | yes | |
| `addressId` | yes | |
| `methodType` | yes | `CARD`, `TABBY`, or `TAMARA` |
| `amount` | yes | Total including shipping (min 0.01) |
| `currency` | yes | ISO 4217, exactly 3 chars |
| `customerId` | yes | User's UUID |
| `customerEmail` | yes | |
| `billingName` | yes | Full name |
| `appOrderId` | no | Pass if order was already created |
| `deliveryMethod` | no | Auto-determined by backend |
| `shippingFee` | no | |
| `customerPhone` | no | |
| `billingApartment/Floor/Building/Street/City/Country/State/PostalCode` | no | Passed to Paymob |

**`methodType` enum values:** `CARD` · `TABBY` · `TAMARA`

**`PaymentInitiatedResponse` fields:**

| Field | Type | Description |
|---|---|---|
| `transactionId` | UUID | Internal transaction ID — poll status with this |
| `methodType` | String | |
| `amount` | BigDecimal | |
| `currency` | String | |
| `clientSecret` | String | Single-use token for Paymob Unified Checkout |
| `checkoutUrl` | String | **Redirect to or open in WebView to complete payment** |

> After initiating payment, redirect the user to `checkoutUrl`. Paymob will POST a webhook to the backend on success/failure — the order status will update automatically to `PAID` or remain `PENDING_PAYMENT`.

---

### GET `/api/payments/transactions/{transactionId}`

Poll payment status after checkout redirect returns.

### GET `/api/payments/orders/{appOrderId}/transactions`

Get all payment attempts for an order.

---

## 7. Full Checkout Flow (Step-by-Step)

```
1. On app boot:
   GET /api/users/{userId}/profile
   → read selectedCountryCode, preferredCurrency, paymentReady

2. Browsing:
   GET /api/product?lang=EN&countryCode={selectedCountryCode}&currency={preferredCurrency}&lat={lat}&lng={lng}
   → use product.storeId when adding to cart
   → show expressDelivery badge if product.expressDelivery === true

3. Add to cart:
   POST /api/cart/{authCredentialId}/items
   → body: { storeId, productId, variantId?, specOptionIds?, quantity }
   → on 403: show "purchase restriction" message

4. View cart:
   GET /api/cart/{authCredentialId}?lat={lat}&lng={lng}
   → display totalPrice, currency, shippingFee, estimatedDeliveryTime
   → show quickDelivery badge per item

5. Checkout cart:
   POST /api/cart/{authCredentialId}/checkout

6. Create order:
   POST /api/orders
   Headers: Authorization + X-Auth-Credential-Id
   Body: { cartId, addressId, deliveryMethod, shippingFee, couponCode?, notes? }
   → deliveryMethod = EXPRESS_DELIVERY if any item has quickDelivery:true, else REGULAR_ORDER

7. Initiate payment:
   POST /api/payments/initiate
   → redirect to checkoutUrl

8. After payment redirect returns:
   GET /api/payments/transactions/{transactionId}
   or
   GET /api/orders/{orderId}
   → show order status to user
```

---

## 8. Error Handling Cheatsheet

| HTTP Status | Scenario | UI Action |
|---|---|---|
| `403` on add-to-cart | Store country ≠ user's selectedCountryCode | Show restriction modal with link to profile country settings |
| `404` on order fetch | Order doesn't belong to this user | Redirect to order list |
| `400` on payment initiate | Missing required fields or amount < 0.01 | Show field validation errors |
| `paymentReady: false` on profile | Missing firstName/lastName/phone/address | Show "complete profile" banner before reaching checkout; display `missingFields` list |
