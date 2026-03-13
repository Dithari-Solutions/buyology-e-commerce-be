# Cart API Documentation

All cart endpoints are prefixed with `/api/cart` and require the user to be authenticated. Include the access token in every request:

```
Authorization: Bearer <accessToken>
```

---

## Common Response Shape

All endpoints return the same envelope:

```json
{
  "status": 200,
  "message": "...",
  "data": { ... }
}
```

On error, `data` is `null` and `message` describes the problem.

---

## Cart Object

Most endpoints return a full `CartResponse` in `data`:

```json
{
  "id": "uuid",
  "userId": "uuid",
  "status": "ACTIVE",
  "totalPrice": 1299.99,
  "createdAt": "2026-03-13T10:00:00Z",
  "updatedAt": "2026-03-13T10:05:00Z",
  "items": [ <CartItem>, ... ]
}
```

### CartItem Object

```json
{
  "id": "uuid",
  "productId": "uuid",
  "productSku": "SKU-001",
  "variantId": "uuid | null",
  "variantSku": "VAR-001 | null",
  "quantity": 2,
  "unitPrice": 499.99,
  "totalPrice": 999.98,
  "selectedSpecs": [ <SpecSelection>, ... ],
  "createdAt": "2026-03-13T10:00:00Z",
  "updatedAt": "2026-03-13T10:05:00Z"
}
```

### SpecSelection Object

Present only for configurable/DIY products that have spec options selected.

```json
{
  "specOptionId": "uuid",
  "groupCode": "RAM",
  "value": "16",
  "unit": "GB",
  "additionalPrice": 50.00,
  "colorCode": null
}
```

> `unit` and `colorCode` may be `null` depending on the spec type.

---

## Endpoints

### 1. Get Cart

Retrieves the active cart for a user. If no active cart exists, one is created automatically.

```
GET /api/cart/{userId}
```

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `userId` | UUID | The authenticated user's ID |

#### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Cart retrieved successfully",
  "data": { <CartResponse> }
}
```

#### Error Responses

| Status | Meaning |
|---|---|
| `404` | User not found |

#### Example

```js
async function getCart(userId) {
  const res = await apiFetch(`/api/cart/${userId}`);
  const json = await res.json();
  return json.data;
}
```

---

### 2. Add Item to Cart

Adds a product to the cart. Supports three product types:
- **Simple product** — `productId` + `quantity` only
- **Variant product** (e.g. refurbished grades) — include `variantId`
- **Configurable/DIY product** — include `specOptionIds`

If the same product+variant combination (with no custom specs) is already in the cart, the quantity is **incremented** rather than creating a duplicate entry.

```
POST /api/cart/{userId}/items
Content-Type: application/json
```

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `userId` | UUID | The authenticated user's ID |

#### Request Body

```json
{
  "productId": "uuid",
  "variantId": "uuid",
  "specOptionIds": ["uuid", "uuid"],
  "quantity": 1
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `productId` | UUID | Yes | The product to add |
| `variantId` | UUID | No | Pre-built variant (e.g. refurbished grade) |
| `specOptionIds` | UUID[] | No | Selected spec options for configurable products |
| `quantity` | Integer | No (default: `1`) | Must be ≥ 1 |

> `variantId` and `specOptionIds` are mutually exclusive in practice — use one or the other depending on the product type.

#### Price Calculation

The unit price is calculated server-side:
- If `variantId` is provided: uses the variant's price
- Otherwise: uses the product's effective price (base price minus any discount)
- Each selected `specOptionId` adds its `additionalPrice` on top

#### Success Response — `201 Created`

```json
{
  "status": 201,
  "message": "Item added to cart",
  "data": { <CartResponse> }
}
```

If the item already existed and quantity was incremented:

```json
{
  "status": 200,
  "message": "Cart updated",
  "data": { <CartResponse> }
}
```

#### Error Responses

| Status | Meaning |
|---|---|
| `400` | `productId` missing, `quantity` < 1, variant doesn't belong to the product, or a `specOptionId` was not found |
| `404` | User or product not found |

#### Example — Simple product

```js
async function addToCart(userId, productId, quantity = 1) {
  const res = await apiFetch(`/api/cart/${userId}/items`, {
    method: 'POST',
    body: JSON.stringify({ productId, quantity }),
  });
  const json = await res.json();
  return json.data;
}
```

#### Example — Variant product

```js
await apiFetch(`/api/cart/${userId}/items`, {
  method: 'POST',
  body: JSON.stringify({
    productId: 'uuid',
    variantId: 'uuid',
    quantity: 1,
  }),
});
```

#### Example — Configurable product with spec options

```js
await apiFetch(`/api/cart/${userId}/items`, {
  method: 'POST',
  body: JSON.stringify({
    productId: 'uuid',
    specOptionIds: ['uuid-ram-16gb', 'uuid-storage-512gb'],
    quantity: 1,
  }),
});
```

---

### 3. Update Item Quantity

Updates the quantity of a specific cart item. To remove an item entirely, use the Remove Item endpoint instead.

```
PATCH /api/cart/{userId}/items/{cartItemId}
Content-Type: application/json
```

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `userId` | UUID | The authenticated user's ID |
| `cartItemId` | UUID | The cart item's ID (from `CartItem.id`) |

#### Request Body

```json
{
  "quantity": 3
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `quantity` | Integer | Yes | New quantity — must be ≥ 1 |

#### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Cart item updated",
  "data": { <CartResponse> }
}
```

#### Error Responses

| Status | Meaning |
|---|---|
| `400` | `quantity` is missing or < 1 |
| `404` | No active cart found, or cart item not found / doesn't belong to this cart |

#### Example

```js
async function updateQuantity(userId, cartItemId, quantity) {
  const res = await apiFetch(`/api/cart/${userId}/items/${cartItemId}`, {
    method: 'PATCH',
    body: JSON.stringify({ quantity }),
  });
  const json = await res.json();
  return json.data;
}
```

---

### 4. Remove Item

Removes a single item (and its spec selections) from the cart.

```
DELETE /api/cart/{userId}/items/{cartItemId}
```

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `userId` | UUID | The authenticated user's ID |
| `cartItemId` | UUID | The cart item's ID |

#### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Item removed from cart",
  "data": { <CartResponse> }
}
```

#### Error Responses

| Status | Meaning |
|---|---|
| `404` | No active cart found, or cart item not found / doesn't belong to this cart |

#### Example

```js
async function removeItem(userId, cartItemId) {
  const res = await apiFetch(`/api/cart/${userId}/items/${cartItemId}`, {
    method: 'DELETE',
  });
  const json = await res.json();
  return json.data;
}
```

---

### 5. Clear Cart

Removes **all items** from the active cart. The cart itself remains (status stays `ACTIVE`, total resets to `0`).

```
DELETE /api/cart/{userId}
```

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `userId` | UUID | The authenticated user's ID |

#### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Cart cleared",
  "data": null
}
```

#### Error Responses

| Status | Meaning |
|---|---|
| `404` | No active cart found |

#### Example

```js
async function clearCart(userId) {
  await apiFetch(`/api/cart/${userId}`, { method: 'DELETE' });
}
```

---

### 6. Checkout

Marks the active cart as `CHECKED_OUT`. A new active cart will be created automatically on the next `GET` or `Add Item` call.

```
POST /api/cart/{userId}/checkout
```

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `userId` | UUID | The authenticated user's ID |

#### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Cart checked out successfully",
  "data": { <CartResponse with status: "CHECKED_OUT"> }
}
```

#### Error Responses

| Status | Meaning |
|---|---|
| `400` | Cart is empty — cannot check out |
| `404` | No active cart found |

#### Example

```js
async function checkout(userId) {
  const res = await apiFetch(`/api/cart/${userId}/checkout`, {
    method: 'POST',
  });
  const json = await res.json();
  return json.data;
}
```

---

## Cart Status Values

| Value | Meaning |
|---|---|
| `ACTIVE` | The current working cart |
| `CHECKED_OUT` | Cart has been submitted — a new cart will be auto-created on next use |

---

## Notes

- The `apiFetch` helper used in examples above is the wrapper from the auth guide that attaches the `Authorization` header and handles token refresh automatically.
- All prices (`unitPrice`, `totalPrice`, `totalPrice` on the cart) are calculated server-side. Never compute or trust prices from the client.
- Spec option prices (`additionalPrice`) are additive — each selected spec's price is summed and added to the base/variant price to form the `unitPrice`.
