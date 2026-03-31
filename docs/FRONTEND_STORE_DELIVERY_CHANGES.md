# Frontend Handoff — Store Delivery & Order Changes

> **Date:** 2026-03-31
> **Scope:** Express delivery badge embedded in product responses, order status changes.

---

## 1. Express Delivery Badge on Product Listings

The delivery badge is embedded directly in every product response — no separate API call needed.

Pass the customer's current coordinates as optional `lat` and `lng` query parameters on any product endpoint:

```
GET /api/product?lang=EN&countryCode=AE&lat=25.2048&lng=55.2708
GET /api/product/{id}?lang=EN&countryCode=AE&lat=25.2048&lng=55.2708
GET /api/product/category/{id}?lang=EN&countryCode=AE&lat=25.2048&lng=55.2708
GET /api/product/search?lang=EN&countryCode=AE&lat=25.2048&lng=55.2708
```

### How to get lat/lng

```js
navigator.geolocation.getCurrentPosition(position => {
  const { latitude, longitude } = position.coords;
  fetchProducts('AE', latitude, longitude);
});
```

Or use the coordinates from the customer's saved delivery address.

### New field in ProductResponse

Each product now includes `expressDelivery`:

```json
{
  "id": "uuid",
  "title": "iPhone 15 Pro",
  "storeId": "uuid",
  "storePrice": 4299.00,
  "currency": "AED",
  "availableInSelectedCountry": true,
  "expressDelivery": true
}
```

| Value | Meaning |
|---|---|
| `true` | The store for this product is within ≤12.5 km — express delivery (≤30 min) |
| `false` | The store is further away — standard/international shipping |
| `null` | No `lat`/`lng` was passed — badge not applicable |

### What to display

```jsx
{product.expressDelivery === true && (
  <Badge color="green">⚡ Express — under 30 min</Badge>
)}
{product.expressDelivery === false && (
  <Badge color="grey">🚢 Standard shipping</Badge>
)}
```

> The customer does NOT choose the delivery method — it is set automatically based on the store's distance from their address. Show it as read-only information.

---

## 2. Checkout Changes

### Do NOT send `deliveryMethod`

The backend determines it automatically. Remove it from the `POST /api/payments/initiate` payload if it was previously sent.

**Correct payload:**
```json
POST /api/payments/initiate
{
  "cartId": "uuid",
  "addressId": "uuid",
  "shippingFee": 5.00,
  "methodType": "CARD",
  "amount": 250.00,
  "currency": "AED",
  "customerId": "uuid",
  "customerEmail": "user@example.com",
  "billingName": "Jane Doe"
}
```

### After payment success

Clear the cart from local state and navigate to order confirmation:

```js
onPaymentSuccess(orderId) {
  clearStoredCartId();
  clearStoredCartItems();
  navigate('OrderConfirmation', { orderId });
}
```

The cart is automatically cleared on the backend after payment. Do not attempt to reuse the old `cartId`.

---

## 3. Order Confirmation Screen

After payment succeeds, call:

```
GET /api/orders/{orderId}
Authorization: Bearer <user_token>
```

### Fields to display

| Field | What to show |
|---|---|
| `status` | `PAID` → "Payment confirmed" |
| `deliveryMethod` | `LOCAL_EXPRESS` → "Express delivery (under 30 min)" / `INTERNATIONAL` → "Standard shipping" |
| `totalAmount` + `currency` | Order total |
| `recipientFirstName` + `recipientLastName` | Delivery recipient |
| `addressLine1`, `city`, `country` | Delivery address |
| `paidAt` | Payment timestamp |
| `trackingCode` | Show only when not null (set by admin for international orders) |
| `carrierName` | Show only when not null |
| `trackingHistory` | Optional — list of status updates with timestamps |

### Example confirmation display

```
✅ Order confirmed!

Order ID: 2abc94fa-...
Delivery: Express delivery (under 30 min)
Total: 25.00 AED
Paid at: 31 Mar 2026, 15:20

Delivering to:
Firdovsi Rzaev
Sulh 189, Apt 272, Sumgait
```

---

## 4. Order Status Tracking

Poll or display `GET /api/orders/{orderId}` to show real-time status.

### Status values and what to show the customer

| Status | Customer-facing message |
|---|---|
| `PENDING_PAYMENT` | Awaiting payment confirmation |
| `PAID` | Payment confirmed — preparing your order |
| `PROCESSING` | Your order is being prepared |
| `COURIER_ASSIGNED` | A courier has been assigned |
| `PICKED_UP` | Courier has picked up your order |
| `IN_TRANSIT` | Your order is on the way |
| `SHIPPED` | Order has been shipped (international) |
| `DELIVERED` | Delivered |
| `CANCELLED` | Order cancelled |
| `FAILED` | Delivery failed — contact support |

### Tracking history

`order.trackingHistory` is an array of events sorted oldest-first:

```json
[
  {
    "status": "PAID",
    "notes": "Payment confirmed",
    "actorRole": "SYSTEM",
    "createdAt": "2026-03-31T12:20:37Z"
  },
  {
    "status": "COURIER_ASSIGNED",
    "notes": "Assigned to courier A",
    "actorRole": "ADMIN",
    "createdAt": "2026-03-31T12:25:00Z"
  }
]
```

Use this to render a progress timeline on the order detail screen.

---

## 5. My Orders List

```
GET /api/orders?page=0&size=20
Authorization: Bearer <user_token>
```

Returns paginated `OrderSummaryResponse` items. Fields available:

- `id`, `status`, `deliveryMethod`
- `totalAmount`, `currency`
- `recipientFirstName`, `recipientLastName`, `city`, `country`
- `trackingCode`, `carrierName`
- `paidAt`, `deliveredAt`, `createdAt`, `updatedAt`

---

## Summary of What Changed

| Area | Before | Now |
|---|---|---|
| Delivery badge | Separate API call needed | Embedded in product response as `expressDelivery` field |
| How to get badge | `GET /api/stores/delivery-info` | Pass `lat` + `lng` to any product endpoint |
| Delivery method selection | Customer chose it | Auto-determined by backend — do not send in request |
| Cart after payment | Remained in state | Backend clears it — clear local state on payment success |
| Transaction status | Could stay PENDING | Now correctly becomes `SUCCESS` |
| Order creation | Manual or pre-created | Automatic on payment success |
| LOCAL_EXPRESS dispatch | Manual admin action | Automatic — sent to courier backend immediately |
