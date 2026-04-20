# Promocode Frontend Integration Guide

This document describes how to integrate the promo code validation and application feature on the web frontend, typically within the Cart or Checkout pages.

---

## 1. Validate Promo Code

To verify if a promo code is valid and calculate the discount amount, use the following endpoint.

**Endpoint:** `POST /api/promo/validate`  
**Auth:** Bearer Token required.

### Request Body (`ValidatePromoCodeRequest`)
| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `code` | string | Yes | The promo code string (e.g., "SAVE20") |
| `orderAmount` | decimal | Yes | The current subtotal of the cart |
| `productIds` | UUID[] | No | List of product IDs in the cart (used if the promo is restricted to specific items) |

**Example Request:**
```json
{
  "code": "WELCOME2026",
  "orderAmount": 500.00,
  "productIds": [
    "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "47b85f64-5717-4562-b3fc-2c963f66afb2"
  ]
}
```

---

### Response Body (`ValidatePromoCodeResponse`)
| Field | Type | Description |
| :--- | :--- | :--- |
| `valid` | boolean | Whether the code can be applied to this order |
| `message` | string | Success or error message (e.g., "Expired", "Minimum amount not met") |
| `promoCodeId` | UUID | The internal ID of the promo code |
| `discountAmount` | decimal | The actual currency amount to be subtracted from the total |
| `discountType` | string | `FIXED` or `PERCENTAGE` |
| `discountValue` | decimal | The raw discount value (e.g., `20.00` for 20% or $20) |

**Example Success Response:**
```json
{
  "status": 200,
  "message": "Promo code validated",
  "data": {
    "valid": true,
    "message": "Promo code applied successfully",
    "promoCodeId": "8da85f64-5717-4562-b3fc-2c963f66afa0",
    "discountAmount": 50.00,
    "discountType": "PERCENTAGE",
    "discountValue": 10.0
  }
}
```

---

## 2. Implementation Flow

1.  **Input:** Customer enters code in a text field on the checkout page.
2.  **Trigger:** Customer clicks "Apply".
3.  **Call:** Frontend calls `POST /api/promo/validate`.
4.  **Success:**
    - If `valid: true`:
        - Subtract `discountAmount` from the order total in the UI.
        - Store the `code` in the local checkout state to be sent during the final order placement.
        - Show the success `message`.
    - If `valid: false`:
        - Show the error `message` (e.g., "This code is only for first-time orders").
5.  **Final Step:** When creating the order (`POST /api/orders`), include the `couponCode` field in the request.

---

## 3. UI/UX Suggestions

- **Visual Feedback:** Show the discount as a separate line item in the price summary:
  - `Subtotal: $500.00`
  - `Discount (WELCOME2026): -$50.00`
  - `Total: $450.00`
- **One at a time:** The system currently supports applying one promo code per order.
- **Persistence:** Ensure the promo code remains applied if the user navigates between cart and checkout, but re-validate if the cart items change significantly (since some codes are product-specific).
