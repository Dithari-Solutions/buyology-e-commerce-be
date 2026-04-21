# Product Search & Filter Integration Guide

This document outlines the API endpoints and data structures required for integrating product search and filtering into the mobile applications.

## 1. Elasticsearch Search
Use this endpoint for full-text search. It is optimized for relevance and supports typos (fuzzy matching).

**Endpoint:** `GET /api/product/search-elastic`

### Request Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `query` | String | Yes | The search term (e.g., "iphone", "gaming"). |
| `lang` | String | Yes | Language code: `EN`, `AZ`, or `AR`. |
| `countryCode` | String | No | ISO 3166-1 alpha-3 code (e.g., `UAE`, `AZE`) for localized pricing. |
| `currency` | String | No | ISO 4217 code (e.g., `AED`, `AZN`) for price display. |
| `lat` / `lng` | Double | No | User coordinates to calculate `expressDelivery` availability. |

### Example
```http
GET /api/product/search-elastic?query=macbook&lang=EN&countryCode=UAE
```

---

## 2. Advanced Filtering (SQL-based)
Use this endpoint when users apply specific filters (Brand, Category, Specs, etc.) from a sidebar or filter sheet.

**Endpoint:** `GET /api/product/search`

### Request Parameters (Query Params)
| Parameter | Type | Description |
| :--- | :--- | :--- |
| `q` | String | Keyword search (SQL `LIKE` based). |
| `categoryId` | UUID | Filter by specific category. |
| `brandId` | UUID | Filter by specific brand. |
| `minPrice` | Decimal | Minimum price in the selected currency. |
| `maxPrice` | Decimal | Maximum price in the selected currency. |
| `condition` | String | `NEW` or `REFURBISHED`. |
| `availabilityStatus`| String | `IN_STOCK`, `OUT_OF_STOCK`, `PRE_ORDER`. |
| `isSuperDeal` | Boolean| Filter for "Super Deal" items. |
| `isLimitedStock` | Boolean| Filter for limited stock items. |

### Specification Filters (Dynamic)
These parameters correspond to the `code` field returned by the `/filters` API.
| Parameter | Type | Example Value |
| :--- | :--- | :--- |
| `ram` | String | "16GB", "32GB" |
| `storage` | String | "512GB", "1TB" |
| `processor` | String | "M3 Max", "Intel i9" |
| `screenSize` | String | "14 inch", "16 inch" |
| `operatingSystem`| String | "macOS", "Windows 11" |
| `keyboardLanguage`| String | "English/Arabic" |
| `touchableScreen` | String | "Yes", "No" |

---

## 3. Fetching Dynamic Filter Options
Before showing a filter UI, call this endpoint to get the available price ranges, categories, brands, and **dynamic specifications** present in the current catalog.

**Endpoint:** `GET /api/product/filters`

### Request Parameters
- `lang` (Required): `EN`, `AZ`, or `AR`.
- `countryCode` (Optional): Scopes the min/max price range to that country.

### Response Schema Highlights
```json
{
  "success": true,
  "data": {
    "priceRange": { "min": 100, "max": 15000 },
    "conditions": ["NEW", "REFURBISHED"],
    "categories": [{ "id": "...", "name": "Laptops" }],
    "brands": [{ "id": "...", "name": "Apple" }],
    "specs": [
      {
        "code": "ram",
        "label": "Memory (RAM)",
        "values": ["8GB", "16GB", "32GB"]
      },
      {
        "code": "storage",
        "label": "Storage Capacity",
        "values": ["256GB", "512GB", "1TB"]
      }
    ]
  }
}
```

---

## 4. Response Model (Common for all Search/List APIs)
All product list endpoints return a `List<ProductResponse>`.

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique product ID. |
| `title` | String | Localized name. |
| `storePrice` | Decimal | The lowest price available in the selected country. |
| `currency` | String | The currency code for the price. |
| `expressDelivery` | Boolean| `true` if a store within 12.5km of provided lat/lng has stock. |
| `media` | Array | List of images. Use the one where `isPrimary: true` for thumbnails. |
| `colors` | Array | Available color options (hex codes and specific images). |

### Implementation Tips
1. **Debouncing:** Implement a 300ms-500ms debounce on the search bar before calling the Elasticsearch API.
2. **Dynamic Specs:** Map the `specs` array from the `/filters` API directly to your UI (e.g., expandable sections in a filter drawer). Use the `code` as the key and `values` as the selectable chips/rows.
3. **Price Display:** Always check `availableInSelectedCountry`. If `false`, the product is not purchasable in the user's region.
