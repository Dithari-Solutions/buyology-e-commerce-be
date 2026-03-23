# Special Products API — Frontend Integration Handoff

**Base URL:** `/api`
**Auth:** No authentication required. These are public endpoints.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Data Model](#2-data-model)
3. [Endpoints](#3-endpoints)
   - [3.1 Get Super-Deal Products](#31-get-super-deal-products)
   - [3.2 Get Limited-Stock Products](#32-get-limited-stock-products)
4. [Error Reference](#4-error-reference)
5. [Frontend Integration Guide](#5-frontend-integration-guide)

---

## 1. Overview

Two dedicated endpoints surface products that have been flagged by admins for special merchandising sections:

| Flag             | Meaning                                                                 |
|------------------|-------------------------------------------------------------------------|
| `isSuperDeal`    | Product is on a promotional deal — use for a "Super Deals" carousel/section |
| `isLimitedStock` | Product has limited remaining inventory — use for urgency-driven sections    |

Both flags live on the `Product` entity and are set by admins at product creation or edit time. Only **`ACTIVE`** products are returned by these endpoints.

---

## 2. Data Model

Both endpoints return the standard `ProductResponse` envelope. The fields relevant to these sections are highlighted below.

### ProductResponse

```json
{
  "id": "uuid",
  "categoryId": "uuid",
  "brandId": "uuid | null",
  "brandName": "string | null",
  "productType": "SIMPLE | DIY | ACCESSORY",
  "isRefurbished": false,
  "refurbGrade": "A | B | C | null",
  "sku": "string",
  "availabilityStatus": "string",
  "isSuperDeal": true,
  "isLimitedStock": false,
  "createdAt": "2024-03-23T10:15:00Z",
  "updatedAt": "2024-03-23T10:15:00Z",
  "title": "string",
  "description": "string",
  "slug": "string",
  "media": [ /* MediaDto[] */ ],
  "specs": [ /* SpecGroupDto[] */ ],
  "colors": [ /* ColorOptionDto[] */ ],
  "variants": [ /* VariantDto[] */ ],
  "accessoryIds": [ /* uuid[] */ ]
}
```

> **Note:** `status` and `deletedAt` are **omitted** from public responses — they only appear in admin responses.

### MediaDto

```json
{
  "id": "uuid",
  "mediaType": "IMAGE | VIDEO",
  "url": "string",
  "thumbnailUrl": "string | null",
  "isPrimary": true,
  "orderIndex": 0
}
```

### SpecGroupDto

```json
{
  "id": "uuid",
  "code": "string",
  "name": "string",
  "options": [
    {
      "id": "uuid",
      "value": "string",
      "unit": "string | null",
      "additionalPrice": 0.00
    }
  ]
}
```

> `additionalPrice` is `0` for the base spec tier. Values `> 0` indicate an upgrade option that adds to the base product price.

### ColorOptionDto

```json
{
  "id": "uuid",
  "value": "string",
  "colorCode": "#RRGGBB",
  "media": [ /* MediaDto[] */ ]
}
```

### VariantDto

```json
{
  "id": "uuid",
  "sku": "string",
  "specOptionIds": [ "uuid", "uuid" ]
}
```

> Each variant is defined by the combination of `specOptionIds` it references. Match these IDs against the `specs[].options[].id` values to determine which configuration a variant represents.

---

## 3. Endpoints

### 3.1 Get Super-Deal Products

Returns all active products flagged as super deals, ordered by most recently updated.

```
GET /api/product/super-deals?lang={lang}
```

**Query parameters:**

| Parameter | Type   | Required | Description                              |
|-----------|--------|----------|------------------------------------------|
| `lang`    | string | Yes      | Language code for translations: `AZ`, `EN`, `AR` |

**Response — 200 OK**

```json
{
  "statusCode": 200,
  "message": "Super deal products fetched successfully",
  "data": [
    {
      "id": "prod-uuid-1",
      "categoryId": "cat-uuid-1",
      "brandId": "brand-uuid-1",
      "brandName": "Apple",
      "productType": "SIMPLE",
      "isRefurbished": false,
      "refurbGrade": null,
      "sku": "SMP-00042",
      "availabilityStatus": "IN_STOCK",
      "isSuperDeal": true,
      "isLimitedStock": false,
      "createdAt": "2024-03-01T08:00:00Z",
      "updatedAt": "2024-03-22T14:00:00Z",
      "title": "MacBook Pro 14\"",
      "description": "...",
      "slug": "macbook-pro-14",
      "media": [
        {
          "id": "media-uuid-1",
          "mediaType": "IMAGE",
          "url": "/uploads/product/abc123.jpg",
          "thumbnailUrl": "/uploads/product/abc123_thumb.jpg",
          "isPrimary": true,
          "orderIndex": 0
        }
      ],
      "specs": [ /* ... */ ],
      "colors": [ /* ... */ ],
      "variants": [ /* ... */ ],
      "accessoryIds": []
    }
  ]
}
```

---

### 3.2 Get Limited-Stock Products

Returns all active products flagged as limited stock.

```
GET /api/product/limited-stock?lang={lang}
```

**Query parameters:**

| Parameter | Type   | Required | Description                              |
|-----------|--------|----------|------------------------------------------|
| `lang`    | string | Yes      | Language code for translations: `AZ`, `EN`, `AR` |

**Response — 200 OK**

```json
{
  "statusCode": 200,
  "message": "Limited stock products fetched successfully",
  "data": [
    {
      "id": "prod-uuid-2",
      "categoryId": "cat-uuid-1",
      "brandId": "brand-uuid-2",
      "brandName": "Samsung",
      "productType": "SIMPLE",
      "isRefurbished": false,
      "refurbGrade": null,
      "sku": "SMP-00087",
      "availabilityStatus": "LOW_STOCK",
      "isSuperDeal": false,
      "isLimitedStock": true,
      "createdAt": "2024-02-15T10:00:00Z",
      "updatedAt": "2024-03-20T11:30:00Z",
      "title": "Galaxy S24 Ultra",
      "description": "...",
      "slug": "galaxy-s24-ultra",
      "media": [ /* ... */ ],
      "specs": [ /* ... */ ],
      "colors": [ /* ... */ ],
      "variants": [ /* ... */ ],
      "accessoryIds": []
    }
  ]
}
```

> **Note:** A product can have both `isSuperDeal: true` and `isLimitedStock: true` simultaneously. It will appear in both lists. Handle this in your UI by deciding whether to show both badges or prioritise one.

---

## 4. Error Reference

| HTTP Status | Message                                    | When it happens                            |
|-------------|--------------------------------------------|--------------------------------------------|
| 400         | `No translation found for language: {lang}` | A product exists but has no translation for the requested `lang`. Contact the admin to add the missing translation. |

All error responses follow the standard envelope:

```json
{
  "statusCode": 400,
  "message": "No translation found for language: AR",
  "data": null
}
```

---

## 5. Frontend Integration Guide

### Homepage / landing page carousels

1. On page load, fire both requests **in parallel**:
   ```
   GET /api/product/super-deals?lang=EN
   GET /api/product/limited-stock?lang=EN
   ```
2. Render each as a horizontal scroll carousel or grid section.
3. Use `media` → find the item where `isPrimary: true` as the card thumbnail. Fall back to `orderIndex: 0` if no primary is set.

### Product cards

Each card should display at minimum:
- `title`
- Primary image from `media`
- `brandName`
- `availabilityStatus` — use `LOW_STOCK` or `OUT_OF_STOCK` to render stock-level indicators
- Badge logic:
  - `isSuperDeal: true` → show a "Super Deal" badge
  - `isLimitedStock: true` → show a "Limited Stock" or "Only a few left" badge
  - Both `true` → show both badges, or apply a priority rule (e.g. "Super Deal" takes precedence visually)

### Navigating to the product detail page

Use `slug` for SEO-friendly URLs (e.g. `/products/macbook-pro-14`) or `id` for direct lookups. Fetch full details from the standard product endpoint:

```
GET /api/product/{productId}?lang=EN
```

### Variants and spec upgrades

If a product has `variants`, each variant maps to a combination of spec options via `specOptionIds`. To resolve which configuration a variant represents:

1. Build a lookup map: `specOptionId → { groupCode, value, additionalPrice }`
2. For each variant, join its `specOptionIds` against the map to display the configuration label and total price modifier.

### Colors

If `colors` is non-empty, render a color swatch selector. On swatch selection, swap the displayed `media` array to the selected color's `media` list.

### Refurbished products

If `isRefurbished: true`, display the `refurbGrade` prominently (`A`, `B`, or `C`). Consider a tooltip explaining what each grade means.
