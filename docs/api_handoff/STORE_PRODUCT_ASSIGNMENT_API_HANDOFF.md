# Store Product Assignment API — Frontend Handoff

This document covers the APIs for assigning global products to a store, setting store-specific prices (in the store's local currency), managing stock per variant, and applying store-level discounts.

> **How it fits the architecture:**
> 1. A super admin creates a product globally (no price, no stock) — see `PRODUCT_ADMIN_API_FRONTEND_HANDOFF.md`
> 2. A store admin assigns that product to their store and sets the price in their local currency + stock per variant
> 3. Customers see the store-specific price when browsing that store

---

## Table of Contents

1. [Base URL & Auth](#1-base-url--auth)
2. [Assign Product to Store](#2-assign-product-to-store)
3. [List Store Products](#3-list-store-products)
4. [Update Store Product](#4-update-store-product)
5. [Remove Product from Store](#5-remove-product-from-store)
6. [Assign Variant to Store Product](#6-assign-variant-to-store-product)
7. [Update Store Variant](#7-update-store-variant)
8. [Remove Variant from Store](#8-remove-variant-from-store)
9. [Response Shape](#9-response-shape)
10. [Discount Logic](#10-discount-logic)
11. [Enums & Allowed Values](#11-enums--allowed-values)
12. [UI Guide — Store Admin Dashboard](#12-ui-guide--store-admin-dashboard)
13. [Endpoint Summary](#13-endpoint-summary)

---

## 1. Base URL & Auth

All endpoints are under:

```
/api/stores/{storeId}/products
```

Auth required: store admin JWT in `Authorization: Bearer <token>` header.

The `storeId` in the path must match the store the authenticated admin manages.

---

## 2. Assign Product to Store

```
POST /api/stores/{storeId}/products
Content-Type: application/json
```

Links a global product to the store. Sets the store price and optionally assigns variants inline.

### Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `productId` | UUID | **Yes** | UUID of the global product — from `GET /api/admin/product` |
| `storePrice` | decimal | **Yes** | Price in the store's local currency, e.g. `45000.00` (EGP) |
| `discountType` | enum | No | `FIXED` or `PERCENTAGE` — omit for no discount |
| `discountValue` | decimal | Conditional | Required when `discountType` is set |
| `isActive` | boolean | No | Default `true` — set `false` to add but not yet show on storefront |
| `variants` | array | No | Assign variants inline. Can also be done separately via [section 6](#6-assign-variant-to-store-product) |

### `variants[]` object

| Field | Type | Required | Notes |
|---|---|---|---|
| `variantId` | UUID | **Yes** | UUID of the global `ProductVariant` |
| `storePrice` | decimal | **Yes** | Variant price in the store's local currency |
| `stock` | integer | **Yes** | Available stock quantity in this store |
| `isActive` | boolean | No | Default `true` |

### Example — product without variants

```json
{
  "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "storePrice": 45000.00,
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "isActive": true
}
```

### Example — product with inline variant assignment

```json
{
  "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "storePrice": 45000.00,
  "isActive": true,
  "variants": [
    {
      "variantId": "v1v1v1v1-0000-0000-0000-000000000001",
      "storePrice": 45000.00,
      "stock": 12,
      "isActive": true
    },
    {
      "variantId": "v2v2v2v2-0000-0000-0000-000000000002",
      "storePrice": 49000.00,
      "stock": 5,
      "isActive": true
    }
  ]
}
```

### Response

`201 Created` — returns the full [StoreProductResponse](#9-response-shape).

### Error cases

| HTTP | Reason |
|---|---|
| `400` | Missing required fields, invalid discount, `discountValue` without `discountType` |
| `400` | Product is already assigned to this store |
| `400` | `variantId` does not belong to the assigned product |
| `404` | Store or product not found |
| `400` | Product is in DELETED status |

---

## 3. List Store Products

```
GET /api/stores/{storeId}/products
```

Returns all active and inactive product assignments for the store (not soft-deleted ones).

### Response

`200 OK` — array of [StoreProductResponse](#9-response-shape).

```json
{
  "statusCode": 200,
  "message": "Store products fetched successfully",
  "data": [ ... ]
}
```

---

## 4. Update Store Product

```
PATCH /api/stores/{storeId}/products/{storeProductId}
Content-Type: application/json
```

Update the price, discount, or active status of an assigned product. All fields are optional — only send what needs to change.

### Request body

| Field | Type | Notes |
|---|---|---|
| `storePrice` | decimal | New price in local currency |
| `discountType` | enum | `FIXED`, `PERCENTAGE`, or `null` to remove discount |
| `discountValue` | decimal | Required when `discountType` is set. Send `null` to remove. |
| `isActive` | boolean | `true` to show on storefront, `false` to hide |

### Example — change price only

```json
{
  "storePrice": 42000.00
}
```

### Example — add a discount

```json
{
  "discountType": "FIXED",
  "discountValue": 38000.00
}
```

### Example — remove discount

```json
{
  "discountType": null,
  "discountValue": null
}
```

### Example — deactivate

```json
{
  "isActive": false
}
```

### Response

`200 OK` — updated [StoreProductResponse](#9-response-shape).

---

## 5. Remove Product from Store

```
DELETE /api/stores/{storeId}/products/{storeProductId}
```

Soft-removes the store product assignment. The global product catalog is unaffected. The product will no longer appear in this store's listings.

### Response

`200 OK`

```json
{
  "statusCode": 200,
  "message": "Product removed from store",
  "data": null
}
```

---

## 6. Assign Variant to Store Product

```
POST /api/stores/{storeId}/products/{storeProductId}/variants
Content-Type: application/json
```

Adds a single variant to an existing store product assignment. Use this when variants need to be added after the initial product assignment.

### Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `variantId` | UUID | **Yes** | UUID of the global `ProductVariant` — must belong to the assigned product |
| `storePrice` | decimal | **Yes** | Variant price in the store's local currency |
| `stock` | integer | **Yes** | Available stock quantity in this store |
| `isActive` | boolean | No | Default `true` |

### Example

```json
{
  "variantId": "v3v3v3v3-0000-0000-0000-000000000003",
  "storePrice": 52000.00,
  "stock": 8,
  "isActive": true
}
```

### Response

`201 Created`

```json
{
  "statusCode": 201,
  "message": "Variant assigned to store product",
  "data": {
    "id": "uuid",
    "variantId": "v3v3v3v3-0000-0000-0000-000000000003",
    "variantSku": "HP-15-V3-1TB",
    "storePrice": 52000.00,
    "stock": 8,
    "isActive": true,
    "updatedAt": "2026-03-22T14:00:00Z"
  }
}
```

### Error cases

| HTTP | Reason |
|---|---|
| `400` | Variant already assigned to this store product |
| `400` | Variant does not belong to the product |
| `404` | Variant or store product not found |

---

## 7. Update Store Variant

```
PATCH /api/stores/{storeId}/products/{storeProductId}/variants/{storeVariantId}
Content-Type: application/json
```

Update the price, stock, or active status of an assigned variant. All fields are optional.

### Request body

| Field | Type | Notes |
|---|---|---|
| `storePrice` | decimal | New variant price |
| `stock` | integer | New stock quantity |
| `isActive` | boolean | Activate or deactivate this variant |

### Example — restock

```json
{
  "stock": 25
}
```

### Example — price change + restock

```json
{
  "storePrice": 50000.00,
  "stock": 10
}
```

### Response

`200 OK` — updated `StoreVariantResponse` object.

---

## 8. Remove Variant from Store

```
DELETE /api/stores/{storeId}/products/{storeProductId}/variants/{storeVariantId}
```

Deactivates the variant in this store. The global variant definition is unaffected.

### Response

`200 OK`

```json
{
  "statusCode": 200,
  "message": "Variant removed from store product",
  "data": null
}
```

---

## 9. Response Shape

### `StoreProductResponse`

```json
{
  "id": "sp-uuid",
  "storeId": "store-uuid",
  "productId": "product-uuid",
  "productSku": "DTDX-482931",
  "productTitle": "HP ProBook 15",
  "storePrice": 45000.00,
  "effectivePrice": 40500.00,
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "isActive": true,
  "createdAt": "2026-03-22T10:00:00Z",
  "updatedAt": "2026-03-22T10:00:00Z",
  "variants": [
    {
      "id": "spv-uuid",
      "variantId": "variant-uuid",
      "variantSku": "HP-15-V1-512",
      "storePrice": 45000.00,
      "stock": 12,
      "isActive": true,
      "updatedAt": "2026-03-22T10:00:00Z"
    }
  ]
}
```

### Field notes

| Field | Notes |
|---|---|
| `id` | ID of the store product assignment — use this as `storeProductId` in PATCH/DELETE URLs |
| `productSku` | Auto-generated global SKU, e.g. `DTDX-482931` |
| `productTitle` | EN title from the global product translations |
| `storePrice` | The base price before any discount |
| `effectivePrice` | **Always use this as the displayed sell price.** Computed by the backend: `storePrice` after applying the discount |
| `discountType` | `"FIXED"`, `"PERCENTAGE"`, or `null` |
| `variants[].id` | ID of the store variant assignment — use as `storeVariantId` in PATCH/DELETE URLs |
| `variants[].variantId` | ID of the global variant |
| `variants[].stock` | Live stock count in this store only |

---

## 10. Discount Logic

The backend computes `effectivePrice` automatically. You never need to calculate it on the frontend.

| `discountType` | `discountValue` | How `effectivePrice` is computed |
|---|---|---|
| `null` | `null` | `effectivePrice = storePrice` |
| `PERCENTAGE` | e.g. `10` | `effectivePrice = storePrice × (1 − 10/100)` |
| `FIXED` | e.g. `38000` | `effectivePrice = discountValue` (i.e. the fixed sale price) |

### Validation rules enforced by the backend

- If `discountType` is set, `discountValue` is required (and vice versa)
- `FIXED` discount value must be **less than** `storePrice`
- `PERCENTAGE` discount value must be between **0 and 100** (exclusive)

---

## 11. Enums & Allowed Values

### `discountType`
| Value | Meaning |
|---|---|
| `FIXED` | `discountValue` is the final sale price, e.g. `38000.00` |
| `PERCENTAGE` | `discountValue` is the % off, e.g. `10` means 10% off |

---

## 12. UI Guide — Store Admin Dashboard

### 12.1 Assign Product Page

The store admin flow:

```
1. Store admin navigates to "Products" → "Add Product"
2. Search / browse the global product catalog
   → GET /api/admin/product  (or public endpoint filtered by lang)
3. Select a product — show its name, SKU, specs, variants
4. Fill in the assignment form (see below)
5. Submit → POST /api/stores/{storeId}/products
6. On success → redirect to the store product list
```

**Assignment form fields:**

```
┌─────────────────────────────────────────────────────┐
│  Assign Product to Store                            │
│                                                     │
│  Product     [HP ProBook 15 — DTDX-482931]  (read) │
│                                                     │
│  Store Price  [__________]  EGP                    │
│               (price in your local currency)        │
│                                                     │
│  Discount     [None ▾]  ← FIXED / PERCENTAGE       │
│  [shown when discount selected]                     │
│  Value        [__________]                         │
│                                                     │
│  Active       ●────  (toggle, default ON)           │
│                                                     │
└─────────────────────────────────────────────────────┘
```

Show a live preview of `effectivePrice` as the admin types, using the client-side formula from [section 10](#10-discount-logic).

---

### 12.2 Assign Variants (inline or separate)

If the product has variants, show a variant section below the main form:

```
┌─────────────────────────────────────────────────────┐
│  Variants                                           │
│                                                     │
│  SKU              Price     Stock    Active         │
│  HP-15-V1-512     [_____]   [____]   [●]           │
│  HP-15-V2-1TB     [_____]   [____]   [●]           │
│                                                     │
│  [+ Add another variant]                            │
└─────────────────────────────────────────────────────┘
```

- The variant SKU and global variant ID come from the global product response (`product.variants[]`)
- Only `storePrice` and `stock` need to be filled in — the rest is already known from the global product

---

### 12.3 Store Product List

Display assigned products in a table:

| Column | Source | Render as |
|---|---|---|
| Product | `productTitle` + `productSku` | Name on first line, SKU badge below |
| Price | `storePrice` | Currency amount |
| Sale Price | `effectivePrice` | Shown in green if different from `storePrice` |
| Discount | `discountType` + `discountValue` | e.g. "10% OFF" or "Sale: 38,000" |
| Variants | `variants.length` | "3 variants" link that expands |
| Status | `isActive` | Green badge = Active, Grey = Inactive |
| Actions | — | Edit / Remove buttons |

---

### 12.4 Edit Store Product

On Edit click → open a form pre-filled from the existing `StoreProductResponse`:

- Change `storePrice` → `PATCH /api/stores/{storeId}/products/{storeProductId}`
- Toggle discount → same PATCH
- Toggle `isActive` → same PATCH

For each variant row:
- Change `storePrice` / `stock` → `PATCH /api/stores/{storeId}/products/{storeProductId}/variants/{storeVariantId}`
- Toggle `isActive` → same PATCH
- Add new variant → `POST /api/stores/{storeId}/products/{storeProductId}/variants`

---

### 12.5 Remove Product

Confirm dialog: *"Remove HP ProBook 15 from your store? This will hide the product from customers. The global product is not affected."*

On confirm → `DELETE /api/stores/{storeId}/products/{storeProductId}`

---

## 13. Endpoint Summary

| Method | URL | Description |
|---|---|---|
| `POST` | `/api/stores/{storeId}/products` | Assign a product to the store |
| `GET` | `/api/stores/{storeId}/products` | List all store product assignments |
| `PATCH` | `/api/stores/{storeId}/products/{storeProductId}` | Update price / discount / active |
| `DELETE` | `/api/stores/{storeId}/products/{storeProductId}` | Remove product from store |
| `POST` | `/api/stores/{storeId}/products/{storeProductId}/variants` | Assign a variant with price + stock |
| `PATCH` | `/api/stores/{storeId}/products/{storeProductId}/variants/{storeVariantId}` | Update variant price / stock / active |
| `DELETE` | `/api/stores/{storeId}/products/{storeProductId}/variants/{storeVariantId}` | Remove variant from store |
