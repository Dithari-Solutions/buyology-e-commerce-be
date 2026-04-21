# Customer Product Listing & Search — Mobile Handoff

This document covers the integration of the public product catalog with location-based features (Country pricing and Express Delivery).

## 1. Global vs. Local Listing
The app should ideally identify the user's country (via IP or selection) to provide a localized shopping experience.

### Localized Search (Recommended)
Fetches only products available in the user's country with accurate prices and delivery badges.

- **Endpoint:** `GET /api/product/search`
- **Mandatory Params for Localization:**
  - `lang`: `AZ` | `EN` | `AR`
  - `countryCode`: `UAE`, `AZE`, etc. (ISO 3166-1 alpha-3)
  - `lat` / `lng`: User's current coordinates (required to calculate Express Delivery)
  - `currency`: (Optional) e.g., `USD`, `AED`. Defaults to country's currency.

---

## 2. Location-Based Features

### 2.1 Express Delivery Badge
If `lat` and `lng` are passed, the API checks if the product is available in a store within **12.5 km** of the user.
- **Field to watch:** `expressDelivery: true`
- **UI Action:** Show a "30 min delivery" or "Express" badge on the product card in the list.

### 2.2 Local Pricing
- **Fields to watch:** `storePrice` and `currency`.
- **Logic:** 
  - If `countryCode` is passed, `storePrice` returns the lowest price available in that country.
  - If `availableInSelectedCountry` is `false`, show "Not available in your region".
  - If no country is passed, `storePrice` will be `null`.

---

## 3. Filters
Fetch filter options once per session or when the user opens the filter drawer.

- **Endpoint:** `GET /api/product/filters`
- **Query Params:** `lang`, `countryCode` (optional, used to scope the price range filter)
- **Response Structure:** Returns min/max prices, categories, brands, and dynamic specification filters (e.g., "Screen Size", "Memory").

---

## 4. Search Implementation
The `/api/product/search` endpoint supports multiple optional filters (AND logic).

**Example Request:**
`GET /api/product/search?lang=EN&countryCode=UAE&lat=25.2048&lng=55.2708&isSuperDeal=true`

**Common Query Parameters:**
- `categoryId`: UUID
- `brandId`: UUID
- `availabilityStatus`: `IN_STOCK`, `OUT_OF_STOCK`, `PRE_ORDER`
- `condition`: `NEW`, `REFURBISHED`
- `isSuperDeal`: `true`
- `isLimitedStock`: `true`
- `search`: Search query string
