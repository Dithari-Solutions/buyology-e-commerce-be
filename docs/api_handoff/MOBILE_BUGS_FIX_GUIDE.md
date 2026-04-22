# Mobile App Bug Fix & Data Integration Guide

This document provides the backend details, API endpoints, and field names required to fix the reported bugs in the Buyology mobile application.

---

## 1. Header & Navigation

### Email Icon in Header
- **Fix**: Remove from frontend UI code. This is a local UI change.

### Arabic Layout (RTL) - Bottom Nav Circle
- **Fix**: This is a CSS/Layout issue in the mobile frontend. 
- **Data**: No backend change required. Ensure the active item indicator follows the RTL direction.

---

## 2. Screens & Directions

### Notification Screen (Missing)
- **New API - Get History**: `GET /api/v1/notifications/history`
- **New API - Unread Count**: `GET /api/v1/notifications/unread-count`
- **New API - Mark as Read**: `PUT /api/v1/notifications/history/{id}/read`
- **Response**: List of `NotificationHistory` objects (`id`, `title`, `body`, `type`, `isRead`, `createdAt`).

### What We Offer & Category Redirects
- **Repair, Rent, Sell**: These should redirect to a **"Coming Soon"** page in the app.
- **Categories Redirect**:
  - **API**: `GET /api/product/category/{categoryId}`
  - **Logic**: Use the `categoryId` from the clicked container to fetch products.

### Continue Shopping (Cart Screen)
- **Fix**: Update the button listener to navigate to the **All Products** screen.
- **API**: Use `GET /api/product?lang=EN` to show the full catalog.

---

## 3. Home Screen Data

### Super Deals
- **API**: `GET /api/product/search-elastic?query=&lang=EN&isSuperDeal=true` (or use the search API with `isSuperDeal=true`).
- **Data Field**: Use `storePrice` for the price and `title` for the name.

### Flash Sale
- **Data Source**: Currently, Flash Sales use the `isSuperDeal=true` flag.
- **API**: `GET /api/product/search?isSuperDeal=true&lang=EN`.

### Popular For You
- **Requirement**: Only 4 products.
- **Fix**: Call any product list API and limit the result to the first 4 items locally or use a limit parameter if supported.

---

## 4. Product Details Screen (PDP)

### Price & Spec Selection
- **Issue**: Price not updating when changing spec.
- **Field**: `specs[].options[].additionalPrice`.
- **Logic**: `DisplayedPrice = BasePrice + additionalPrice`. 
- **Missing Name**: The spec group name is in `specs[].name`. The option name is in `specs[].options[].value`.

### Related Products (Mock Data)
- **API**: `GET /api/product/{productId}/related`
- **Parameters**: `lang`, `countryCode` (optional).
- **Note**: This returns 4 products from the same category.

### Share Button
- **Logic**: Use the `slug` from the product response to generate a deep link: `buyology://product/{slug}` or a web URL `https://buyology.com/product/{slug}`.

### Price Visibility & Cart Availability
- **Fix**: The backend now always returns a `storePrice` and `currency` for the Product Details Page. If the product is not available in the user's selected country, it falls back to the globally cheapest available price.
- **Frontend Logic**:
  - **Always show the price** from `storePrice`.
  - Check the `availableInSelectedCountry` boolean.
  - If `availableInSelectedCountry` is `false`: Disable the **"Add to Cart"** button and show a status message "Not available in your country".
  - If `true`: Enable the cart button.
- **Goal**: Allow users to browse all products and see their value regardless of their current location.

---

## 5. Favorites & Cart

### Favorite Screen (Sign-in Message)
- **Issue**: Shows sign-in message when empty.
- **Backend Behavior**: `GET /api/favorites/{authCredentialId}` returns `200 OK` with an empty `items` list if the user has no favorites. It returns `404` only if the `authCredentialId` is invalid.
- **Fix**: Frontend should check `items.length === 0` to show "No favorites yet" instead of "Please sign in".

---

## 6. Mini Game Integration

### Game Logic & APIs
- **Get Daily Game**: `GET /api/game/daily-type` (Returns `QUIZ` or `MINI_GAME`).
- **Submit Result**: `POST /api/game/submit`
  - **Body**: 
    ```json
    {
      "gameType": "MINI_GAME",
      "score": 100,
      "success": true
    }
    ```
- **Rewards**: Each success grants **10 tokens** to the user profile.

---

## 7. Field Mapping Reference
| UI Component | Backend Field |
| :--- | :--- |
| Product Title | `title` |
| Product Price | `storePrice` (localized) |
| Currency | `currency` (e.g., "AZN") |
| Spec Group Name | `specs[].name` |
| Spec Option Value | `specs[].options[].value` |
| Additional Price | `specs[].options[].additionalPrice` |
| Notification Title | `title` |
| Notification Body | `body` |
