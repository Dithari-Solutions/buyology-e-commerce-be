# Warranty API Handoff

**Base URL:** `https://api-dev.dithari.com` (dev) / `https://api.buyology.online` (production)
**Content-Type:** `application/json`

All responses follow the usual envelope:

```json
{ "statusCode": 200, "message": "...", "data": { ... } }
```

---

## How it works

1. Every product has an optional warranty length in whole months (`warrantyMonths`, 1–120). It is
   either **linked to a preset** (e.g. "Laptop — 12 months") or a **custom** length typed for that
   product. Editing a preset's length updates every product linked to it.
2. When an order moves to **DELIVERED** (admin, courier app, Quiqup or supplier portal), each unit of
   each item whose product has a warranty gets its own **warranty card** with a serial number. An
   item with quantity 2 gets two cards.
   - Cover starts on the delivery date (UTC) and runs for the product's length at that moment.
   - The end date is the last covered day.
   - The card keeps a snapshot of the product: English name, brand, category, condition and up to
     six main specs. For a variant, the specs are the ones of the variant that was sold.
3. Cards are issued after the delivery commits. An hourly sweep issues any that were missed, looking
   back 7 days. Issuing is idempotent.
4. Customers see only the serial on buyology.online, with a link to
   `https://v2.dithari.com/warranty?serial=SERIAL`. The card itself is shown on Dithari's checker,
   which calls the public lookup below.

**Serial format:** `DTH-XXXX-XXXX-XXXX`, made of twelve random characters from Crockford base32.
There is no I, L, O or U. Lookups ignore case, spaces and dashes, and read O as 0 and I/L as 1.

---

## 1. Public lookup (Dithari's checker)

```
GET /api/warranty/{serial}
```

- No authentication.
- CORS: allowed from `https://v2.dithari.com`, `https://dithari.com`, `https://www.dithari.com` and
  the main allowlist. GET only, **without credentials**.
- Rate limit: 60 requests per minute per client (its own tier).

**Response 200:**

```json
{
  "statusCode": 200,
  "message": "Warranty card found",
  "data": {
    "serialNumber": "DTH-7K4Q-2M9X-QH3T",
    "status": "ACTIVE",
    "durationMonths": 12,
    "startDate": "2026-06-24",
    "endDate": "2027-06-24",
    "soldBy": "Buyology",
    "coverage": null,
    "product": {
      "name": "Dell Latitude 7420",
      "brand": "Dell",
      "category": "Laptop",
      "condition": "Refurbished · Grade A",
      "specs": [
        { "label": "Memory", "value": "16 GB" },
        { "label": "Storage", "value": "512 GB" }
      ]
    }
  }
}
```

- `status` is computed on the day of the request:
  - `VOID`: an admin withdrew the card.
  - `EXPIRED`: today is after `endDate`.
  - `EXPIRING_SOON`: 30 days or fewer remain.
  - `ACTIVE`: otherwise.
- `coverage` is the "What's covered" text from `WARRANTY_COVERAGE_TEXT`. It is null when that is
  not set.
- **404** when no card matches. No customer or order data is ever returned.

---

## 2. Warranty presets (admin)

| Method | Path | Permission |
|---|---|---|
| GET | `/api/admin/warranty-presets` | `product:warranty:read`, `product:create` or `product:update` |
| POST | `/api/admin/warranty-presets` | `product:warranty:create` |
| PUT | `/api/admin/warranty-presets/{id}` | `product:warranty:update` |
| DELETE | `/api/admin/warranty-presets/{id}` | `product:warranty:delete` |

**Request (POST / PUT):** `{ "name": "Laptop", "durationMonths": 12 }`. The name must be unique
ignoring case. Send 24 months for two years.

**Response item:**
`{ "id", "name", "durationMonths", "productCount", "createdAt", "updatedAt" }`. `productCount` is
the number of products linked to the preset.

- **PUT** with a new length moves every linked product to it. Issued cards keep their dates.
- **DELETE** keeps each linked product's months as a custom length and returns how many were
  switched (`data` is a number).

---

## 3. Product fields

The existing product endpoints gain these fields.

| Endpoint | Field | Meaning |
|---|---|---|
| `POST /api/admin/product/create` | `warrantyPresetId` | Follow this preset. Wins over `warrantyMonths`. |
| | `warrantyMonths` | Custom length, 1–120. |
| | *(neither)* | No warranty, so no card is issued. Importers, the supplier portal and n8n rely on this. |
| `PATCH /api/admin/product/{id}` | `warrantyPresetId` | Switch to this preset. |
| | `warrantyMonths` | Switch to a custom length, unlinking any preset. |
| | `removeWarranty: true` | Remove the warranty. Wins over both. |
| | *(none)* | Warranty unchanged. |
| Product responses (admin and storefront) | `warrantyMonths`, `warrantyPresetId` | Both omitted when null. |

The dashboard requires a warranty when an admin creates a product. The API does not, so the other
creation paths keep working.

---

## 4. Orders

`GET /api/orders/{id}` (customer) and the admin order endpoints now include `warranties` on each
item once the order is delivered. The field is omitted when there are none.

```json
"warranties": [
  { "serialNumber": "DTH-7K4Q-2M9X-QH3T", "status": "ACTIVE", "startDate": "2026-06-24", "endDate": "2027-06-24" }
]
```

### Void / restore a card (admin)

```
POST /api/admin/warranty-cards/{serial}/void      body: { "reason": "Unit returned" }
POST /api/admin/warranty-cards/{serial}/restore
```

- Permission: `order:warranty:update`.
- Returns the updated item warranty.
- The reason is kept for staff and is never shown publicly.
- Refunds are per order and carry no item list, so nothing voids cards automatically.
- **409** when the card is already void (void) or not void (restore).

---

## Deploy notes

- **Migration** `V69__warranty_presets_and_cards.sql` adds:
  - the `warranty_presets` table
  - `products.warranty_months` and `products.warranty_preset_id`
  - the `warranty_cards` table
- **Permissions:** the new codes are seeded at boot.
  - Superadmin gets them automatically.
  - Existing admin roles keep access through `legacyAdmin()` unless `RBAC_STRICT_PERMISSIONS=true`.
    In that case, grant the codes in Roles.
  - Custom staff roles need them granted explicitly.
- **Optional:** set `WARRANTY_COVERAGE_TEXT` to show a "What's covered" paragraph on every card.
