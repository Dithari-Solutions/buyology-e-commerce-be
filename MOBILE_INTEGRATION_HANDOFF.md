# Mobile Integration Handoff — Products & Countries

**Backend Base URL:** `https://api-dev.dithari.com`  
**Auth:** JWT Bearer Token — include `Authorization: Bearer <access_token>` on protected routes.  
**All responses follow the envelope:**
```json
{
  "success": true,
  "message": "...",
  "data": { ... }
}
```

---

## Table of Contents

1. [Countries](#1-countries)
2. [Categories](#2-categories)
3. [Brands](#3-brands)
4. [Products](#4-products)
5. [Localization](#5-localization)
6. [Error Handling](#6-error-handling)
7. [Full Flow Walkthrough](#7-full-flow-walkthrough)

---

## 1. Countries

> **Used in:** country/region selector on the user profile or app-start screen. The selected country drives product pricing and availability.

### 1.1 Get All Active Countries

```
GET /api/countries/active
Auth: NOT required
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "AE",
      "name": "United Arab Emirates",
      "currency": "AED",
      "isActive": true,
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    },
    {
      "id": "...",
      "code": "AZ",
      "name": "Azerbaijan",
      "currency": "AZN",
      "isActive": true,
      ...
    }
  ]
}
```

| Field      | Type    | Description                              |
|------------|---------|------------------------------------------|
| `id`       | UUID    | Country identifier                       |
| `code`     | String  | ISO 3166-1 alpha-2/3 code (e.g., `"AE"`) |
| `name`     | String  | Display name                             |
| `currency` | String  | ISO 4217 currency code (e.g., `"AED"`)   |
| `isActive` | Boolean | Only active ones are returned here       |

### 1.2 Get Country by ID

```
GET /api/countries/{id}
Auth: NOT required
```

**Path params:** `id` — UUID of the country.

---

### Country Integration Guide (Mobile)

1. **On first app launch (or account creation):** Call `GET /api/countries/active` and present a picker/list so the user selects their country.
2. **Persist** the selected country's `code` (e.g., `"AE"`) and `currency` (e.g., `"AED"`) in local storage / user preferences.
3. **Pass `countryCode` and `currency`** as query parameters on every product request (see Section 4). The backend uses these to return country-specific prices and filter out unavailable products.
4. **Allow the user to change country** from the profile/settings screen — re-fetch products after a change.

---

## 2. Categories

### 2.1 Get All Categories (Localized)

```
GET /api/category/
Auth: NOT required
Query params:
  lang  (required)  — one of: EN | AZ | AR
```

**Example:**
```
GET /api/category/?lang=EN
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "parentId": null,
      "name": "Electronics",
      "description": "All electronic products",
      "slug": "electronics",
      "status": "ACTIVE"
    },
    {
      "id": "uuid",
      "parentId": "parent-uuid",
      "name": "Laptops",
      "description": "...",
      "slug": "laptops",
      "status": "ACTIVE"
    }
  ]
}
```

| Field        | Type   | Description                                      |
|--------------|--------|--------------------------------------------------|
| `id`         | UUID   | Category ID — use this to filter products        |
| `parentId`   | UUID?  | `null` for root categories; otherwise subcategory |
| `name`       | String | Localized name based on `lang` param             |
| `description`| String | Localized description                            |
| `slug`       | String | URL-friendly identifier                          |

**Category tree:** build a hierarchy by grouping items where `parentId == null` as roots and nesting children by matching `parentId`.

---

## 3. Brands

### 3.1 Get All Brands

```
GET /api/brand
Auth: NOT required
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "status": "ACTIVE",
      "translations": [
        { "language": "EN", "name": "Apple" },
        { "language": "AZ", "name": "Apple" },
        { "language": "AR", "name": "آبل" }
      ]
    }
  ]
}
```

Use the `id` as the `brandId` filter when searching products (Section 4.5).

---

## 4. Products

All public product endpoints follow the same optional context query parameters:

| Query Param   | Type   | Required | Description                                      |
|---------------|--------|----------|--------------------------------------------------|
| `lang`        | String | YES      | `EN`, `AZ`, or `AR`                             |
| `countryCode` | String | NO       | ISO country code (e.g., `AE`). Enables country-specific pricing. |
| `currency`    | String | NO       | ISO currency code (e.g., `AED`). Used for price conversion. |
| `lat`         | Double | NO       | User latitude (required for express delivery)    |
| `lng`         | Double | NO       | User longitude (required for express delivery)   |

> **Recommendation:** Always pass `lang`, `countryCode`, and `currency`. Pass `lat`/`lng` if location permission is granted.

---

### 4.1 Get All Active Products

```
GET /api/product/
Auth: NOT required
```

**Example:**
```
GET /api/product/?lang=EN&countryCode=AE&currency=AED&lat=25.2048&lng=55.2708
```

**Response — array of ProductResponse:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "categoryId": "uuid",
      "brandId": "uuid",
      "brandName": "Apple",
      "productType": "SIMPLE",
      "isRefurbished": false,
      "refurbGrade": null,
      "sku": "APL-IPH15-128",
      "availabilityStatus": "IN_STOCK",
      "isSuperDeal": false,
      "isLimitedStock": false,
      "title": "iPhone 15",
      "description": "Latest Apple flagship.",
      "slug": "iphone-15",
      "storeId": "uuid",
      "storePrice": 3999.00,
      "currency": "AED",
      "availableInSelectedCountry": true,
      "expressDelivery": true,
      "storeOptions": [
        {
          "storeId": "uuid",
          "storePrice": 3999.00,
          "currency": "AED",
          "expressDelivery": true
        }
      ],
      "media": [
        {
          "id": "uuid",
          "mediaType": "IMAGE",
          "url": "https://...",
          "thumbnailUrl": "https://...",
          "isPrimary": true,
          "orderIndex": 0
        }
      ],
      "specs": [
        {
          "groupName": "Storage",
          "options": [
            { "label": "128 GB", "unit": "GB", "additionalPrice": 0 }
          ]
        }
      ],
      "colors": [
        {
          "localKey": "midnight",
          "value": "Midnight",
          "colorCode": "#1c1c1e",
          "mediaIndices": [0, 1]
        }
      ],
      "variants": [
        {
          "sku": "APL-IPH15-128-MDN",
          "specOptionIds": ["uuid"]
        }
      ],
      "accessoryIds": [],
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### ProductResponse Field Reference

| Field                        | Type          | Notes                                                                    |
|------------------------------|---------------|--------------------------------------------------------------------------|
| `id`                         | UUID          | Unique product ID                                                        |
| `title`                      | String        | Localized based on `lang`                                                |
| `description`                | String        | Localized based on `lang`                                                |
| `productType`                | String        | `SIMPLE`, `DIY`, `ACCESSORY`                                             |
| `isRefurbished`              | Boolean       | Whether the product is refurbished                                       |
| `refurbGrade`                | String?       | `A`, `B`, `C` — only present if `isRefurbished=true`                    |
| `availabilityStatus`         | String        | `IN_STOCK`, `OUT_OF_STOCK`, `PRE_ORDER`                                  |
| `isSuperDeal`                | Boolean       | Flag for Super Deal badge                                                |
| `isLimitedStock`             | Boolean       | Flag for Limited Stock badge                                             |
| `storePrice`                 | Decimal?      | Best price in selected currency. `null` if no country context sent.      |
| `currency`                   | String?       | Currency of `storePrice`. `null` if no country context sent.             |
| `availableInSelectedCountry` | Boolean?      | `false` = product exists but not sold in selected country               |
| `expressDelivery`            | Boolean?      | `true` = store is within ~12.5 km of user's location                   |
| `storeOptions`               | Array?        | All store options with price/express info                                |
| `media`                      | Array         | Images/videos. Sort by `orderIndex`. Use `isPrimary=true` for thumbnail |
| `specs`                      | Array         | Specification groups and their options                                   |
| `colors`                     | Array         | Color variants with hex codes and linked media indices                  |
| `variants`                   | Array         | SKU-level variants (e.g., 128GB Midnight)                               |

---

### 4.2 Get Product by ID

```
GET /api/product/{productId}
Auth: NOT required
Query params: lang (required), countryCode, currency, lat, lng
```

**Example:**
```
GET /api/product/3fa85f64-5717-4562-b3fc-2c963f66afa6?lang=EN&countryCode=AE&currency=AED
```

Response is a single `ProductResponse` object (same structure as above).

---

### 4.3 Get Products by Category

```
GET /api/product/category/{categoryId}
Auth: NOT required
Query params: lang (required), countryCode, currency, lat, lng
```

**Example:**
```
GET /api/product/category/uuid?lang=EN&countryCode=AE&currency=AED
```

Response is an array of `ProductResponse`.

---

### 4.4 Get Super Deal Products

```
GET /api/product/super-deals
Auth: NOT required
Query params: lang (required), countryCode, currency
```

Returns products where `isSuperDeal = true`. Display with a "Super Deal" badge.

---

### 4.5 Get Limited Stock Products

```
GET /api/product/limited-stock
Auth: NOT required
Query params: lang (required), countryCode, currency
```

Returns products where `isLimitedStock = true`. Display with a "Limited Stock" badge.

---

### 4.6 Get Quick Delivery Products

```
GET /api/product/quick-delivery
Auth: NOT required
Query params:
  lat  (required, -90 to 90)
  lng  (required, -180 to 180)
  lang (required)
  countryCode, currency (optional)
```

Returns products from stores within ~12.5 km of the user. Only call this when the user has granted location permission.

---

### 4.7 Search & Filter Products

```
GET /api/product/search
Auth: NOT required
Query params (all optional filters + lang/countryCode/currency/lat/lng):
```

| Filter Param         | Type    | Values / Notes                            |
|----------------------|---------|-------------------------------------------|
| `lang`               | String  | `EN`, `AZ`, `AR` (required)              |
| `countryCode`        | String  | e.g., `AE`                               |
| `currency`           | String  | e.g., `AED`                              |
| `lat` / `lng`        | Double  | User location for express delivery        |
| `condition`          | String  | `NEW` or `REFURBISHED`                   |
| `brandId`            | UUID    | Filter by brand                           |
| `availabilityStatus` | String  | `IN_STOCK`, `OUT_OF_STOCK`, `PRE_ORDER`  |
| `categoryId`         | UUID    | Filter by category                        |
| `isSuperDeal`        | Boolean | `true` / `false`                         |
| `isLimitedStock`     | Boolean | `true` / `false`                         |
| `ram`                | String  | e.g., `8 GB`                             |
| `storage`            | String  | e.g., `256 GB`                           |
| `processor`          | String  | e.g., `Apple M2`                         |
| `screenSize`         | String  | e.g., `6.1`                              |
| `touchableScreen`    | String  | `Yes` or `No`                            |
| `operatingSystem`    | String  | e.g., `iOS`                              |
| `keyboardLanguage`   | String  | e.g., `Arabic`                           |

**Example:**
```
GET /api/product/search?lang=EN&countryCode=AE&currency=AED&condition=NEW&brandId=uuid&categoryId=uuid&ram=8+GB
```

---

## 5. Localization

The API supports three languages. Always pass the `lang` query parameter.

| Code | Language    |
|------|-------------|
| `EN` | English     |
| `AZ` | Azerbaijani |
| `AR` | Arabic      |

- Detect device locale and map to the closest supported language, defaulting to `EN`.
- For RTL layout, apply when `lang=AR`.
- `title`, `description`, `slug`, category `name`/`description` are all returned pre-localized — no client-side mapping needed.

---

## 6. Error Handling

**HTTP Status Codes:**

| Code | Meaning                          |
|------|----------------------------------|
| 200  | Success                          |
| 400  | Bad request / validation error   |
| 401  | Unauthorized (missing/bad token) |
| 403  | Forbidden (insufficient role)    |
| 404  | Resource not found               |
| 500  | Internal server error            |

**Error response structure:**
```json
{
  "success": false,
  "message": "Product not found",
  "data": null
}
```

Handle `availableInSelectedCountry: false` gracefully — show a "Not available in your country" message rather than hiding the product entirely (UX decision).

---

## 7. Full Flow Walkthrough

### App Launch / Onboarding

```
1. GET /api/countries/active
   → Present country picker to user
   → Save: countryCode = "AE", currency = "AED"

2. GET /api/category/?lang=EN
   → Build category navigation (tabs / sidebar)

3. GET /api/brand
   → Populate brand filter options
```

### Home Screen

```
4. GET /api/product/?lang=EN&countryCode=AE&currency=AED&lat=25.2&lng=55.3
   → Show all products

5. GET /api/product/super-deals?lang=EN&countryCode=AE&currency=AED
   → "Super Deals" carousel

6. GET /api/product/limited-stock?lang=EN&countryCode=AE&currency=AED
   → "Limited Stock" section

7. GET /api/product/quick-delivery?lang=EN&countryCode=AE&currency=AED&lat=25.2&lng=55.3
   → "Near You / Express Delivery" section (if location granted)
```

### Category / Browse Screen

```
8. GET /api/product/category/{categoryId}?lang=EN&countryCode=AE&currency=AED
   → Products filtered by selected category

9. GET /api/product/search?lang=EN&countryCode=AE&currency=AED&brandId=uuid&condition=NEW
   → Apply filter panel selections
```

### Product Detail Screen

```
10. GET /api/product/{productId}?lang=EN&countryCode=AE&currency=AED&lat=25.2&lng=55.3
    → Full product detail

    Display logic:
    - media[isPrimary=true].thumbnailUrl  → hero image
    - media sorted by orderIndex          → image gallery
    - storePrice + currency               → price display
    - availabilityStatus                  → stock badge
    - isSuperDeal                         → "Super Deal" badge
    - isLimitedStock                      → "Limited Stock" badge
    - expressDelivery                     → "Express / Same-day" badge
    - specs                               → spec table grouped by groupName
    - colors                              → color picker; use mediaIndices to
                                           show color-matched gallery images
    - variants                            → variant selector (e.g., storage size)
    - availableInSelectedCountry=false    → "Not available in <countryName>"
```

### User Profile / Settings Screen

```
11. GET /api/countries/active
    → Re-show country picker when user wants to change country
    → On change: save new countryCode/currency, refresh product screens
```

---

## Notes for the Mobile Dev

- **No auth required** for any of the product or country endpoints listed in this doc. JWT is only needed for orders, stores, and admin endpoints.
- **`storePrice` can be `null`** if you don't send `countryCode`/`currency` — always send them once the user has selected a country.
- **`storeOptions`** contains all stores selling the product with their individual prices. If you need price comparison UI, use this array.
- **Express delivery** requires `lat`/`lng` — fall back gracefully when location is denied (omit those params; `expressDelivery` will be `null`).
- **Refurbished products** — show grade badge (`A`, `B`, `C`) when `isRefurbished=true`. Grade A = like new, B = good, C = acceptable.
- **Variants + colors** — a variant is a specific combination of spec options (e.g., 128GB + Midnight). Colors link to specific media via `mediaIndices` (0-indexed into the `media` array).
- **Category tree** — build from the flat list: items with `parentId=null` are roots; nest others under their parent.
