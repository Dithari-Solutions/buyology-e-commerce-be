# Payment & Checkout — Frontend Handoff

> **Date:** 2026-03-31
> **Scope:** Three breaking / behaviour-changing fixes to the payment and order flow.

---

## 1. Delivery method is no longer sent by the frontend

### What changed
The `deliveryMethod` field has been **removed as a required field** from the initiate-payment request. The backend now determines it automatically:

| Condition | Assigned method |
|---|---|
| **All** items in the cart come from stores within ≈12.5 km of the delivery address | `LOCAL_EXPRESS` (≤30 min courier) |
| Any item comes from a store outside that radius | `INTERNATIONAL` (cross-border shipping) |

### What you must change

**Before (remove this field):**
```json
POST /api/payments/initiate
{
  "cartId": "...",
  "addressId": "...",
  "deliveryMethod": "LOCAL_EXPRESS",   // ← DELETE THIS
  "shippingFee": 5.00,
  "methodType": "CARD",
  "amount": 250.00,
  "currency": "AED",
  "customerId": "...",
  "customerEmail": "user@example.com",
  "billingName": "Jane Doe"
}
```

**After (correct payload):**
```json
POST /api/payments/initiate
{
  "cartId": "...",
  "addressId": "...",
  "shippingFee": 5.00,
  "methodType": "CARD",
  "amount": 250.00,
  "currency": "AED",
  "customerId": "...",
  "customerEmail": "user@example.com",
  "billingName": "Jane Doe"
}
```

> **Note:** Sending `deliveryMethod` will not cause an error (the field is still accepted but silently ignored). However it is cleaner to remove it from the UI entirely since the customer no longer makes this choice.

### UI impact
- Remove any delivery-method selector (radio buttons, dropdowns, etc.) from the checkout screen.
- The assigned delivery method is visible on the order response after payment succeeds (`order.deliveryMethod`). You can display it on the order-confirmation screen as a read-only value.

---

## 2. Cart is automatically cleared after a successful payment

### What changed
After Paymob confirms payment success, the backend now:
1. Creates the order.
2. **Deletes all cart items** from the paid cart.
3. Marks that cart as `ABANDONED`.

The customer's next "add to cart" call will create a brand-new `ACTIVE` cart automatically — no action needed on the frontend.

### What you must change

#### Stop re-using the old cart ID after payment
Any flow that stores the `cartId` in local state (AsyncStorage, Redux, Context, etc.) must **clear it** when the payment success screen is shown.

```js
// Pseudocode — adapt to your state management
onPaymentSuccess() {
  clearStoredCartId();          // ← add this
  clearStoredCartItems();       // ← add this
  navigate('OrderConfirmation', { orderId });
}
```

#### Fetch a fresh cart when the user returns to shopping
The first call to `GET /api/cart` after payment will return a new empty cart (the backend creates one on demand). Do **not** attempt to reuse or reload the old cart ID — it will return a 404 or an ABANDONED cart.

#### Do not show a "cart still has items" badge after payment
Previously the cart was not cleared, so a badge counter could incorrectly show leftover items. With this fix, the cart is empty after payment. Ensure the cart badge re-fetches from the server rather than reading from a local cache.

---

## 3. Payment transaction status now correctly becomes `SUCCESS`

### What changed
A bug caused the `payment_transaction` record to stay in `PENDING` status even when Paymob confirmed the payment. This is now fixed. The status transitions are:

```
PENDING → PROCESSING → SUCCESS   (happy path)
PENDING → PROCESSING → FAILED    (payment declined)
```

### What you must change

#### Payment status polling / webhook redirect handling
If your app polls `GET /api/payments/transactions/{transactionId}` to check payment status, the response will now correctly return `"status": "SUCCESS"` once Paymob confirms. Previously it would stay `"PENDING"` indefinitely.

Update any timeout or "still pending" fallback logic that was added to work around this bug:

```js
// Before — you may have had a long timeout or "manual refresh" fallback
// because SUCCESS never arrived. Remove those workarounds.

// After — poll normally; SUCCESS will arrive within seconds of Paymob callback
const pollStatus = async (transactionId) => {
  const res = await api.get(`/payments/transactions/${transactionId}`);
  if (res.data.status === 'SUCCESS') {
    navigateToOrderConfirmation();
  } else if (res.data.status === 'FAILED') {
    showPaymentFailedScreen();
  }
  // else keep polling (PENDING / PROCESSING)
};
```

#### Order confirmation screen
The order is now reliably created and set to `PAID` status after successful payment. You can safely call `GET /api/orders/{orderId}` on the confirmation screen and expect `"status": "PAID"`.

---

## Quick reference — fields removed from request bodies

| Endpoint | Field removed | Action |
|---|---|---|
| `POST /api/payments/initiate` | `deliveryMethod` | Remove from request payload |

## Quick reference — new behaviour to handle

| Event | Old behaviour | New behaviour |
|---|---|---|
| Payment succeeds | Cart items remain; cart stays `CHECKED_OUT` | Cart items deleted; cart set to `ABANDONED` |
| Payment succeeds | Transaction stays `PENDING` | Transaction set to `SUCCESS` |
| User shops again after paying | May get constraint error on checkout | Fresh `ACTIVE` cart is created automatically |

---

## No changes to these endpoints

The following endpoints are **unchanged** — no frontend updates needed:

- `GET /api/cart` — still returns the active cart (will be a new empty cart after payment)
- `POST /api/cart/items` — still adds items (to the new active cart)
- `POST /api/cart/checkout` — unchanged
- `GET /api/orders` / `GET /api/orders/{id}` — unchanged
- Paymob redirect / callback URLs — unchanged
