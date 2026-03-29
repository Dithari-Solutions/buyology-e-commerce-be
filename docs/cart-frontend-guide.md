# Cart Module — Frontend Integration Guide

This document covers every endpoint, request/response shape, and frontend behaviour rule for the cart module.

---

## Base URL

```
/api/cart
```

---

## Data Types

| Type | Format | Example |
|------|--------|---------|
| UUID | `string` (UUID v4) | `"3fa85f64-5717-4562-b3fc-2c963f66afa6"` |
| Instant | `string` (ISO-8601 UTC) | `"2026-03-29T10:00:00Z"` |
| BigDecimal | `string` or `number` | `"149.99"` |
| countryCode | ISO 3166-1 alpha-3 `string` | `"AZE"`, `"ARE"`, `"USA"` |
| currency | ISO 4217 `string` | `"AZN"`, `"AED"`, `"USD"` |

---

## Response Envelope

Every endpoint returns the same wrapper:

```jsonc
{
  "success": true,           // boolean
  "message": "...",          // human-readable status
  "data": { ... }            // null on error or void responses
}
```

On error `success` is `false` and `data` is `null`.

---

## Endpoints

### 1. Get Active Cart

```
GET /api/cart/{authCredentialId}?lat={lat}&lng={lng}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `authCredentialId` | UUID (path) | Yes | Logged-in user's auth credential ID |
| `lat` | number (query) | No | User's current latitude — enables `quickDelivery` badge |
| `lng` | number (query) | No | User's current longitude — enables `quickDelivery` badge |

> Pass `lat`/`lng` whenever the user's device location is available. Without them every cart item returns `quickDelivery: false`.

**Response `data`:** [`CartResponse`](#cartresponse)

---

### 2. Add Item to Cart

```
POST /api/cart/{authCredentialId}/items
Content-Type: application/json
```

**Request body:**

```jsonc
{
  "storeId": "uuid",          // required — read from ProductResponse.storeId (see note below)
  "productId": "uuid",        // required
  "variantId": "uuid",        // optional — for pre-built variants (e.g. refurbished grade)
  "specOptionIds": ["uuid"],  // optional — for DIY/configurable products
  "quantity": 1               // required, minimum 1
}
```

> **Where does `storeId` come from?**
> Every product listing or detail response includes a `storeId` field when a `countryCode` query parameter is provided. This is the ID of the store that offers the product at the lowest price in that country. Pass it directly as `storeId` in this request.
> See [Getting storeId from the product API](#getting-storeid-from-the-product-api) for the full flow.

**Rules:**
- `storeId` is **mandatory**. The store determines the price and the cart's country/currency.
- Once the first item is added the cart is locked to that item's **country**. Subsequent items must come from a store in the **same country**, otherwise the API returns `400`.
- If the same `productId` + `variantId` combination already exists in the cart (with no custom specs) the quantity is **incremented** rather than creating a duplicate row.

**Response `data`:** [`CartResponse`](#cartresponse)

---

### 3. Update Item Quantity

```
PATCH /api/cart/{authCredentialId}/items/{cartItemId}
Content-Type: application/json
```

**Request body:**

```jsonc
{
  "quantity": 3   // required, minimum 1
}
```

**Response `data`:** [`CartResponse`](#cartresponse)

---

### 4. Remove Item

```
DELETE /api/cart/{authCredentialId}/items/{cartItemId}
```

**Response `data`:** [`CartResponse`](#cartresponse)

---

### 5. Clear Cart

```
DELETE /api/cart/{authCredentialId}
```

Removes all items and resets country/currency on the cart.

**Response `data`:** `null`

---

### 6. Checkout

```
POST /api/cart/{authCredentialId}/checkout
```

Transitions the cart status to `CHECKED_OUT`. A new empty cart will be created automatically on the next `getCart` or `addItem` call.

**Response `data`:** [`CartResponse`](#cartresponse)

---

## Response Schemas

### CartResponse

```jsonc
{
  "id": "uuid",
  "authCredentialId": "uuid",
  "status": "ACTIVE",          // "ACTIVE" | "CHECKED_OUT" | "ABANDONED"
  "totalPrice": "299.98",      // sum of all item totalPrice values
  "countryCode": "AZE",        // ISO 3166-1 alpha-3 — null until first item is added
  "currency": "AZN",           // ISO 4217 — null until first item is added
  "items": [ CartItemResponse ],
  "createdAt": "2026-03-29T10:00:00Z",
  "updatedAt": "2026-03-29T10:05:00Z"
}
```

### CartItemResponse

```jsonc
{
  "id": "uuid",
  "productId": "uuid",
  "productSku": "SKU-001",
  "variantId": "uuid",          // null if no variant selected
  "variantSku": "SKU-001-A",    // null if no variant selected
  "storeId": "uuid",            // the store this item was priced from
  "quantity": 2,
  "unitPrice": "149.99",        // price per unit in the cart's currency
  "totalPrice": "299.98",       // unitPrice × quantity
  "quickDelivery": true,        // true = store is within ~30-min delivery radius of user
  "selectedSpecs": [ CartItemSpecSelectionResponse ],
  "createdAt": "2026-03-29T10:00:00Z",
  "updatedAt": "2026-03-29T10:05:00Z"
}
```

### CartItemSpecSelectionResponse

```jsonc
{
  "specOptionId": "uuid",
  "groupCode": "ram",           // e.g. "ram", "storage", "processor", "screen_size"
  "value": "16",
  "unit": "GB",                 // null if not applicable
  "additionalPrice": "20.00",   // extra charge on top of the base product price
  "colorCode": "#FF5733"        // null if not a colour option
}
```

---

## Multi-Country Rules

1. **Country is derived from the store, not sent by the frontend.** The frontend only sends `storeId`; the backend resolves the country automatically.
2. **All items in one cart must share the same country.** If the user attempts to add a product from a different country (e.g. an Azerbaijani store while the cart already has an Emirati store item) the API returns:
   ```jsonc
   {
     "success": false,
     "message": "All items in a cart must belong to the same country. Current cart country: ARE, item country: AZE",
     "data": null
   }
   ```
   **Frontend action:** show an error dialog and offer the user the option to clear the cart first.
3. **All prices (`unitPrice`, `totalPrice`, `totalPrice` on the cart) are in `CartResponse.currency`.** Display this symbol/code next to every price.
4. After `clearCart` the `countryCode` and `currency` fields on `CartResponse` will be `null` until a new item is added.

---

## Quick Delivery Badge

`CartItemResponse.quickDelivery` is `true` when the store that has this item is within approximately **12.5 km** of the user (≈ 30-minute urban delivery window).

### How to enable it

Pass the user's coordinates as query parameters on every `GET /api/cart` call:

```
GET /api/cart/{authCredentialId}?lat=40.4093&lng=49.8671
```

### Frontend checklist

- Request device location permission on app start or when the cart page is opened.
- Cache the coordinates in app state; re-fetch on `GET /cart` calls.
- If permission is denied or coordinates are unavailable, omit `lat`/`lng` — all items will return `quickDelivery: false` and no badge should be shown.
- Show a distinct badge (e.g. "⚡ Under 30 min") on each cart item where `quickDelivery === true`.
- Do **not** show the badge when `quickDelivery === false` — the delivery time is unknown, not slow.

---

## Error Reference

| HTTP Status | Scenario |
|-------------|----------|
| `400` | Missing required field, quantity < 1, variant not belonging to product, cross-country cart conflict |
| `404` | Auth credential / product / variant / spec option / store product not found, no active cart |
| `201` | Item successfully added (new row created) |
| `200` | All other success responses |

---

## Getting storeId from the Product API

`storeId` is **not** something the frontend chooses — it is returned by the backend as part of every product response when a `countryCode` is supplied. It identifies the store that has the lowest price for that product in the user's country.

### Product listing

```
GET /api/product?lang=EN&countryCode=AZE&currency=AZN
```

Each item in `data[]` includes:

```jsonc
{
  "id": "uuid",           // → use as productId in the cart request
  "storeId": "uuid",      // → use as storeId in the cart request
  "storePrice": 149.99,   // displayed price
  "currency": "AZN",
  ...
}
```

### Product detail page

```
GET /api/product/{productId}?lang=EN&countryCode=AZE&currency=AZN
```

Same fields — `storeId` is present in the response.

### Add to cart — correct flow

```
1. GET /api/product?countryCode=AZE...
   → response includes product.storeId and product.storePrice

2. User taps "Add to cart"

3. POST /api/cart/{authCredentialId}/items
   body: {
     "storeId":   "<product.storeId from step 1>",
     "productId": "<product.id>",
     "quantity":  1
   }
```

> `storeId` will be `null` in product responses when `countryCode` is **not** supplied (e.g. admin-facing endpoints). Never call `addItem` without a valid `storeId` — it will always return `400`.

---

## Typical Frontend Flow

```
1. User opens product listing (country already resolved from user session/preferences)
   └─ GET /api/product?lang=EN&countryCode=AZE&currency=AZN
      → store storeId + productId from each card

2. User taps "Add to cart"
   └─ POST /api/cart/{authCredentialId}/items
      body: { storeId, productId, variantId?, specOptionIds?, quantity }
      → on 400 "cross-country": prompt user to clear cart or switch country

3. User opens cart page
   └─ GET /api/cart/{authCredentialId}?lat=...&lng=...

4. User changes quantity in cart
   └─ PATCH /api/cart/{authCredentialId}/items/{cartItemId}
      body: { quantity }

5. User removes an item
   └─ DELETE /api/cart/{authCredentialId}/items/{cartItemId}

6. User proceeds to checkout
   └─ POST /api/cart/{authCredentialId}/checkout
      → redirect to order/payment flow

7. User clears cart
   └─ DELETE /api/cart/{authCredentialId}
```

---

## Notes

- The cart is created automatically on the first `addItem` call — there is no separate "create cart" endpoint.
- `CartResponse` always reflects the latest cart state; use the returned object to update your local state after every mutating call (add / update / remove / checkout).
- Prices stored in the cart are **snapshot prices** at the time of adding. Re-fetching the cart does not re-price items.
- Always pass `countryCode` when fetching products so that `storeId` and `storePrice` are populated in the response.
