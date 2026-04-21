# Admin Product Management — Mobile Handoff

This document outlines the API integration for the Admin Mobile App to display, filter, and manage the product catalog.

## 1. Get Filter Options
Before displaying the list, fetch the available filter values to populate the filter drawer/bottom sheet.

- **Endpoint:** `GET /api/product/filters`
- **Query Params:** `lang`, `countryCode` (optional)
- **Response:** Returns price ranges, brands, categories, and dynamic spec filters (like "RAM", "Storage").

---

## 2. List & Filter Products
Fetches all products with full status visibility (including PENDING, INACTIVE, etc.).

- **Endpoint:** `GET /api/product/search`
- **Query Params (Filters):**
  - `lang`: (Required)
  - `categoryId`: Filter by category UUID
  - `brandId`: Filter by brand UUID
  - `availabilityStatus`: `IN_STOCK`, `OUT_OF_STOCK`, `PRE_ORDER`
  - `condition`: `NEW`, `REFURBISHED`
  - `isSuperDeal`: `true` | `false`
  - `isLimitedStock`: `true` | `false`
  - `search`: Full-text search string

### Admin-Only Response Fields
In the Admin app, the `ProductResponse` includes internal fields not shown to customers:
- `status`: `ACTIVE`, `INACTIVE`, `PENDING`, `TRASH`
- `deletedAt`: Timestamp (if in TRASH)
- `sku`: Internal SKU identifier

---

## 3. Administrative Actions
### 3.1 Create Product
- **Endpoint:** `POST /api/admin/product/create` (Multipart)
- **Security:** Requires `ROLE_ADMIN` or `ROLE_PRODUCT_ADMIN`.

### 3.2 Manage Trash
- **View Trash:** `GET /api/admin/product/trash?lang=EN`
- **Restore Product:** `PUT /api/admin/product/{productId}/restore`
- **Soft Delete:** `DELETE /api/admin/product/{productId}`

## 4. Search
For high-performance global search across all attributes, use:
- **Endpoint:** `GET /api/product/search-elastic?query=iphone&lang=EN`
