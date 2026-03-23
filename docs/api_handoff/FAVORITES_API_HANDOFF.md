# Favorites API — Frontend Integration Handoff

**Base URL:** `/api`
**Auth:** All endpoints accept a JWT `Authorization: Bearer <token>` header (enforced at the application level). The `authCredentialId` in the path is the ID returned from the sign-in response.

---

## Table of Contents

1. [Data Model](#1-data-model)
2. [Customer Endpoints](#2-customer-endpoints)
   - [2.1 Get My Favorites](#21-get-my-favorites)
   - [2.2 Add Product to Favorites](#22-add-product-to-favorites)
   - [2.3 Remove Product from Favorites](#23-remove-product-from-favorites)
   - [2.4 Check if Product is Favorited](#24-check-if-product-is-favorited)
3. [Admin Endpoints](#3-admin-endpoints)
   - [3.1 List All Favorites (Paginated)](#31-list-all-favorites-paginated)
   - [3.2 Get Favorites for a Specific User](#32-get-favorites-for-a-specific-user)
4. [Error Reference](#4-error-reference)
5. [Frontend Integration Guide](#5-frontend-integration-guide)

---

## 1. Data Model

### FavoriteItem (returned in customer responses)

```json
{
  "id": "uuid",
  "productId": "uuid",
  "productSku": "string",
  "savedAt": "2024-03-23T10:15:00Z"
}
```

### FavoriteList (customer list response wrapper)

```json
{
  "authCredentialId": "uuid",
  "total": 3,
  "items": [ /* FavoriteItem[] */ ]
}
```

### AdminFavoriteEntry (returned in admin list)

```json
{
  "favoriteId": "uuid",
  "authCredentialId": "uuid",
  "userEmail": "string | null",
  "productId": "uuid",
  "productSku": "string",
  "savedAt": "2024-03-23T10:15:00Z"
}
```

### AdminFavoritePage (admin paginated wrapper)

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8,
  "items": [ /* AdminFavoriteEntry[] */ ]
}
```

---

## 2. Customer Endpoints

### 2.1 Get My Favorites

Fetches all products the user has saved to favorites, ordered by most recently added.

```
GET /api/favorites/{authCredentialId}
```

**Path parameters:**

| Parameter          | Type | Required | Description                           |
|--------------------|------|----------|---------------------------------------|
| `authCredentialId` | UUID | Yes      | The credential ID from the sign-in response |

**Response — 200 OK**

```json
{
  "statusCode": 200,
  "message": "Favorites retrieved",
  "data": {
    "authCredentialId": "a1b2c3d4-...",
    "total": 2,
    "items": [
      {
        "id": "fav-uuid-1",
        "productId": "prod-uuid-1",
        "productSku": "SKU-001",
        "savedAt": "2024-03-22T14:00:00Z"
      },
      {
        "id": "fav-uuid-2",
        "productId": "prod-uuid-2",
        "productSku": "SKU-002",
        "savedAt": "2024-03-21T09:30:00Z"
      }
    ]
  }
}
```

> **Note:** The response returns `productId` and `productSku`. Use the product ID to fetch full product details (name, image, price) from the product endpoints. This keeps the favorites list lean and avoids stale product data.

---

### 2.2 Add Product to Favorites

Adds a product to the user's favorites. Returns `409 Conflict` if the product is already favorited.

```
POST /api/favorites/{authCredentialId}/products/{productId}
```

**Path parameters:**

| Parameter          | Type | Required | Description                              |
|--------------------|------|----------|------------------------------------------|
| `authCredentialId` | UUID | Yes      | Credential ID from sign-in               |
| `productId`        | UUID | Yes      | The product to add to favorites          |

**Request body:** none

**Response — 201 Created**

```json
{
  "statusCode": 201,
  "message": "Product added to favorites",
  "data": {
    "id": "fav-uuid-1",
    "productId": "prod-uuid-1",
    "productSku": "SKU-001",
    "savedAt": "2024-03-23T10:15:00Z"
  }
}
```

**Response — 409 Conflict** (already favorited)

```json
{
  "statusCode": 409,
  "message": "Product is already in favorites",
  "data": null
}
```

---

### 2.3 Remove Product from Favorites

Removes a product from the user's favorites. Returns `404` if the product was not favorited.

```
DELETE /api/favorites/{authCredentialId}/products/{productId}
```

**Path parameters:**

| Parameter          | Type | Required | Description                              |
|--------------------|------|----------|------------------------------------------|
| `authCredentialId` | UUID | Yes      | Credential ID from sign-in               |
| `productId`        | UUID | Yes      | The product to remove from favorites     |

**Response — 200 OK**

```json
{
  "statusCode": 200,
  "message": "Product removed from favorites",
  "data": null
}
```

---

### 2.4 Check if Product is Favorited

Lightweight endpoint to check whether a single product is in the user's favorites. Useful for toggling a heart/bookmark icon without fetching the entire list.

```
GET /api/favorites/{authCredentialId}/products/{productId}/check
```

**Response — 200 OK**

```json
{
  "statusCode": 200,
  "message": "Check complete",
  "data": {
    "favorited": true
  }
}
```

---

## 3. Admin Endpoints

These endpoints are under `/api/admin/favorites` and should only be called from the admin dashboard.

### 3.1 List All Favorites (Paginated)

Returns a paginated list of all favorites across all users, sorted by most recently added.

```
GET /api/admin/favorites?page=0&size=20
```

**Query parameters:**

| Parameter | Type    | Default | Description                      |
|-----------|---------|---------|----------------------------------|
| `page`    | integer | `0`     | Zero-based page number           |
| `size`    | integer | `20`    | Items per page (max recommended: 100) |

**Response — 200 OK**

```json
{
  "statusCode": 200,
  "message": "Favorites retrieved",
  "data": {
    "page": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8,
    "items": [
      {
        "favoriteId": "fav-uuid-1",
        "authCredentialId": "cred-uuid-1",
        "userEmail": "john@example.com",
        "productId": "prod-uuid-1",
        "productSku": "SKU-001",
        "savedAt": "2024-03-23T10:15:00Z"
      }
    ]
  }
}
```

> **Note:** `userEmail` may be `null` for OAuth-only accounts that have no email address stored.

---

### 3.2 Get Favorites for a Specific User

Returns the full favorites list for one user. Useful when an admin opens a user's profile and wants to inspect their saved products.

```
GET /api/admin/favorites/users/{authCredentialId}
```

**Path parameters:**

| Parameter          | Type | Required | Description               |
|--------------------|------|----------|---------------------------|
| `authCredentialId` | UUID | Yes      | The user's credential ID  |

**Response — 200 OK**

```json
{
  "statusCode": 200,
  "message": "User favorites retrieved",
  "data": {
    "authCredentialId": "cred-uuid-1",
    "total": 3,
    "items": [
      {
        "id": "fav-uuid-1",
        "productId": "prod-uuid-1",
        "productSku": "SKU-001",
        "savedAt": "2024-03-23T10:15:00Z"
      }
    ]
  }
}
```

---

## 4. Error Reference

| HTTP Status | Message                            | When it happens                                   |
|-------------|------------------------------------|---------------------------------------------------|
| 404         | `Auth credential not found`        | The `authCredentialId` does not exist             |
| 404         | `Product not found`                | The `productId` does not exist or is deleted      |
| 404         | `Product is not in favorites`      | DELETE called for a product that was not favorited|
| 409         | `Product is already in favorites`  | POST called for a product already in favorites    |

All error responses follow the standard envelope:

```json
{
  "statusCode": 404,
  "message": "Product not found",
  "data": null
}
```

---

## 5. Frontend Integration Guide

### Favorites toggle button (product cards / product detail page)

1. On page load, call **2.4 Check** (`GET /check`) for each visible product to set the initial heart icon state.
   - For product list pages with many cards, prefer fetching the full favorites list (**2.1 Get My Favorites**) once and building a local Set of favorited product IDs to avoid N+1 requests.

2. On heart icon click:
   - If `favorited = false` → call **2.2 Add** (`POST`). On `201`, flip the icon to filled.
   - If `favorited = true` → call **2.3 Remove** (`DELETE`). On `200`, flip the icon to empty.
   - On `409` from Add, the item was already saved — treat it as already-favorited and flip the icon.

### Favorites page

- Call **2.1 Get My Favorites** to get the list of `productId` values.
- Fetch product details (name, image, price) in parallel using the product endpoints.
- Show a count badge using `data.total`.

### Admin dashboard — user profile view

- Call **3.2 Get Favorites for Specific User** when the admin opens a user's profile to show a "Saved Products" section.

### Admin dashboard — favorites analytics

- Call **3.1 List All Favorites** with pagination to render a table of all saved products with user emails and timestamps.
- Use the `totalElements` field to display a summary count.
