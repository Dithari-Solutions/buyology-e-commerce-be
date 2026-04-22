# Product Details Page (PDP) - Mobile Integration Guide

This document provides a comprehensive guide for mobile developers to integrate the Product Details Page (PDP), including core data, specifications, reviews, questions, and related products.

---

## 1. Product Core Data & Components
Use this endpoint to fetch the primary product data.

**Endpoint:** `GET /api/product/{productId}`

### Query Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `lang` | String | Yes | `EN`, `AZ`, or `AR` |
| `countryCode` | String | No | ISO 3166-1 alpha-3 (e.g., `UAE`, `AZE`) |
| `currency` | String | No | ISO 4217 code (e.g., `AED`, `AZN`) |
| `lat` / `lng` | Double | No | Customer coordinates for Express Delivery badge calculation |

### Components to Build
1.  **Image Gallery**: Use the `media` array. Swap the source list if a user selects a different color from the `colors` array.
2.  **Price & Shipping**:
    *   `storePrice` + `currency`: Base price. This is always provided (falls back to global cheapest if not available in the user's specific country).
    *   `availableInSelectedCountry`: **CRITICAL**: Use this field to enable/disable the "Add to Cart" button. If `false`, the product cannot be purchased in the user's region.
    *   `expressDelivery`: Show "Express Delivery (30 min)" badge if `true`.
3.  **Variant Selector**:
    *   The `colors` array provides color circles (using `colorCode`) and localized `value`.
    *   Selecting a color should update the gallery with that color's specific `media` items.
4.  **Specifications & Upgrades**:
    *   The `specs` array contains grouped specifications (e.g., Memory, Storage).
    *   **Selection Logic**: If an option in `specs` has `additionalPrice > 0`, it's an upgrade. Update the total price in the UI: `Total = storePrice + selectedUpgradePrice`.

---

## 2. Ratings & Reviews
Manage customer feedback and ratings.

### Get Review Summary (Stats)
**Endpoint:** `GET /api/reviews/product/{productId}/stats`
*   **Use for**: Showing the star breakdown (5-star count, 4-star count, etc.) and average rating.

### Get Approved Reviews
**Endpoint:** `GET /api/reviews/product/{productId}?page=0&size=10`
*   **Response**: `List<ReviewResponse>`
*   **Key Fields**: `userFirstName`, `rating`, `body`, `isVerifiedPurchase`, `helpfulCount`, `media` (user-uploaded images).

### Create a Review
**Endpoint:** `POST /api/reviews` (Multipart Request)
*   **JSON Part (`request`)**:
    ```json
    {
      "productId": "uuid",
      "authCredentialId": "user-uuid",
      "rating": 5,
      "body": "Excellent product!"
    }
    ```
*   **Files Part (`images`)**: Up to 2 images (optional).

### Vote on Review Helpfulness
**Endpoint:** `POST /api/reviews/{reviewId}/vote`
*   **Body**: `{ "authCredentialId": "uuid", "isHelpful": true }`

---

## 3. Product Q&A (Questions & Answers)
Handle customer inquiries about the product.

### Get Approved Questions
**Endpoint:** `GET /api/questions/product/{productId}?page=0&size=10`
*   **Response**: `List<QuestionResponse>`
*   **Key Fields**: `body` (the question), `answer` (the `body` of the official store response), `helpfulCount`.

### Ask a Question
**Endpoint:** `POST /api/questions`
*   **Body**:
    ```json
    {
      "productId": "uuid",
      "authCredentialId": "user-uuid",
      "body": "Does this model include a charger?"
    }
    ```

### Mark Question as Helpful
**Endpoint:** `POST /api/questions/{questionId}/vote`
*   **Body**: `{ "authCredentialId": "uuid" }`

---

## 4. Related Products
Show a "You may also like" section at the bottom of the PDP.

**Endpoint:** `GET /api/product/{productId}/related`

### Behavior
- **Data**: Returns up to 4 popular products from the same category.
- **Parameters**: Supports same `lang`, `countryCode`, and `lat`/`lng` for consistent pricing/badges.
- **Excluded**: The current product is automatically excluded from the results.

---

## 5. PDP Lifecycle Checklist
1.  **Initial Load**:
    *   Execute `GET /api/product/{productId}`
    *   Execute `GET /api/reviews/product/{productId}/stats`
    *   Execute `GET /api/product/{productId}/related`
2.  **User Action - Change Color**: Filter `media` gallery by selected color's UUID.
3.  **User Action - Change Spec**: If option has `additionalPrice`, update display price.
4.  **User Action - Add to Cart**: Use the `storeId` returned in the core product response when calling the Cart API.
5.  **User Action - Load Reviews/Questions**: Call the paginated `GET` endpoints when the user taps on the respective tabs.
