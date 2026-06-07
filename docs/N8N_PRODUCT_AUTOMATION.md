# n8n Automation Guide — Creating Products in the Buyology Backend

This document describes everything an n8n workflow needs to create products in the
`ecommerce-backend` Spring Boot service: authentication, the lookup endpoints used to
fetch valid IDs (categories, brands, spec library), the full product-creation request
body with every field and its rules, how images are uploaded, the success/error
response shapes, and a ready-to-use n8n node layout.

---

## 1. Base URL & Conventions

| Setting | Value |
|---|---|
| Application name | `ecommerce-backend` |
| Default port | `8080` |
| Context path | none (routes start at `/`) |
| API versioning | none — paths use `/api/...` directly |
| Local base URL | `http://localhost:8080` |
| Auth scheme | JWT Bearer token in `Authorization` header |

All request/response bodies are JSON **except product creation/update**, which is
`multipart/form-data`. Responses are wrapped in a standard envelope:

```json
{ "success": true, "message": "…", "data": { … } }
```

---

## 2. Authentication (get a JWT first)

Product creation requires an **ADMIN** account. Sign in to obtain an access token.

**`POST /auth/admin/signin`** — `Content-Type: application/json`

Request body:
```json
{ "email": "admin@example.com", "password": "yourPassword" }
```

Response:
```json
{
  "success": true,
  "message": "…",
  "data": { "accessToken": "eyJhbGci…", "expiresIn": 900 }
}
```

- Use `data.accessToken` as `Authorization: Bearer <accessToken>` on every protected call.
- **Access token lifetime is ~15 minutes** (`expiresIn` is in seconds). For long-running
  or scheduled n8n workflows, sign in at the start of each run (cheapest), or refresh.
- The refresh token is set as an HttpOnly cookie (`Path=/auth/refresh`, 7 days) — not
  usable directly in most n8n HTTP flows, so re-signing in per run is the simplest path.

> Endpoint: `src/main/java/com/buyology/ecommerce/auth/controller/AdminAuthController.java:59`

---

## 3. Pre-flight: fetch valid IDs before creating a product

A product references a **category** (required) and optionally a **brand**, **spec
library** entries, etc. Fetch these first to get the UUIDs to send.

### 3.1 Categories — `GET /api/category?lang=EN`  *(public, no auth)*
Returns `List<CategoryLocalizedResponse>`: `id`, `nameAz/En/Ar`, `descriptionAz/En/Ar`,
`slugAz/En/Ar`. Use a category `id` as `categoryId`.

`lang` accepts `AZ`, `EN`, or `AR` (case-insensitive).

### 3.2 Brands — `GET /api/brand`  *(public, no auth)*
Returns `List<BrandResponse>`: `id`, `status`, `translations[{ language, name }]`.
Use a brand `id` as `brandId` (optional).

### 3.3 Global spec library — `GET /api/admin/specs`  *(ADMIN auth required)*
Returns `List<GlobalSpecGroupResponse>`:
```jsonc
[
  {
    "id": "uuid",
    "code": "ram",
    "translations": [{ "language": "EN", "name": "RAM" }],
    "options": [
      {
        "id": "uuid",
        "unit": "GB",
        "displayOrder": 0,
        "translations": [{ "language": "EN", "value": "16 GB" }]
      }
    ]
  }
]
```
Use `group.id` as `globalSpecGroupId` and `option.id` as `globalOptionId` to **reuse**
existing specs instead of recreating them inline.

> Endpoints: `ProductCategoryController.java:37`, `BrandController.java:27`,
> `AdminGlobalSpecController.java:30`

---

## 4. Create a Product

**`POST /api/admin/product/create`**

| | |
|---|---|
| Auth | `Authorization: Bearer <token>` — role **ADMIN** |
| Content-Type | `multipart/form-data` |
| Returns | `201 Created`, `ApiResponse<ProductResponse>` |

> Endpoint: `AdminProductController.java:58`

### 4.1 Multipart parts

The request has **two parts** (exact names matter):

| Part name | Type | Required | Description |
|---|---|---|---|
| `request` | text/JSON | **yes** | A JSON string of `CreateProductRequest` (see §4.2). Send as a multipart field whose content is the stringified JSON. |
| `files` | binary[] | no | Zero or more media files (images/videos). 0-based order is referenced by `colors[].mediaIndices`. |

> The controller reads `request` as a raw string and deserializes it with Jackson, so it
> must be valid JSON. In n8n set this field’s value to a JSON string (e.g. from a Set/Code
> node), and attach binaries under the field name `files`.

### 4.2 `CreateProductRequest` — full field reference

> Source: `src/main/java/com/buyology/ecommerce/product/dto/CreateProductRequest.java`

| JSON field | Type | Required | Default | Rules / notes |
|---|---|---|---|---|
| `categoryId` | UUID | **yes** | — | Must be an existing category (`@NotNull`). |
| `brandId` | UUID | no | — | If provided, must exist. |
| `availabilityStatus` | enum | no | `PRE_ORDER` | `IN_STOCK` \| `OUT_OF_STOCK` \| `PRE_ORDER` |
| `isSuperDeal` | boolean | no | `false` | Shows in super-deals section. |
| `isLimitedStock` | boolean | no | `false` | Marks limited stock. |
| `productType` | enum | **yes** | — | `SIMPLE` \| `DIY` \| `ACCESSORY` (`@NotNull`). |
| `sku` | string | no | auto | If omitted, auto-generated (`DTAX-`/`DTDX-` prefix). If provided, must be **globally unique**. |
| `isRefurbished` | boolean | no | `false` | — |
| `refurbGrade` | enum | conditional | — | `A` \| `B` \| `C`. **Required when `isRefurbished=true`.** |
| `status` | string | no | `ACTIVE` | `ACTIVE` \| `INACTIVE` \| `DELETED`. |
| `translations` | object | **yes** | — | `@NotNull @Valid` — see §4.3. |
| `specs` | array | no | — | Spec groups, see §4.4. |
| `colors` | array | no | — | Color variants + media mapping, see §4.5. |
| `variants` | array | no | — | Product variants, see §4.6. |
| `accessoryIds` | UUID[] | no | — | Existing product UUIDs linked as accessories. |

### 4.3 `translations` (object) — **required**

> Source: `ProductTranslationRequest.java`. All six fields are `@NotBlank`. Titles are `@Size(max=255)`.

| JSON field | Type | Required | Rules |
|---|---|---|---|
| `titleAz` | string | **yes** | non-blank, ≤255 chars |
| `titleEn` | string | **yes** | non-blank, ≤255 chars |
| `titleAr` | string | **yes** | non-blank, ≤255 chars |
| `descriptionAz` | string | **yes** | non-blank |
| `descriptionEn` | string | **yes** | non-blank |
| `descriptionAr` | string | **yes** | non-blank |

> Slugs are generated automatically per language from the title; they must be unique.

### 4.4 `specs[]` (array of spec groups) — optional

> Source: `CreateSpecGroupRequest.java`. Two modes per group:
> **(a)** reference an existing group via `globalSpecGroupId`, or
> **(b)** create/reuse by `code` + names.

| JSON field | Type | Required | Rules |
|---|---|---|---|
| `globalSpecGroupId` | UUID | conditional | If set, the group is looked up by ID; `code`/names not needed. |
| `code` | string | conditional | e.g. `ram`, `battery_capacity`. Required if `globalSpecGroupId` omitted; reused if a group with this code exists. |
| `nameAz` | string | conditional | Required if `globalSpecGroupId` omitted. |
| `nameEn` | string | conditional | Required if `globalSpecGroupId` omitted. |
| `nameAr` | string | conditional | Required if `globalSpecGroupId` omitted. |
| `options` | array | **yes** | `@NotEmpty` — at least one option, see below. |

**`specs[].options[]`** — `CreateSpecOptionRequest.java`

| JSON field | Type | Required | Rules |
|---|---|---|---|
| `localKey` | string | **yes** | `@NotBlank`. Unique key within this request; variants reference it (e.g. `ram-16gb`). |
| `globalOptionId` | UUID | conditional | If set, value/unit read from the library; values not needed. |
| `valueAz` | string | conditional | Required if `globalOptionId` omitted. |
| `valueEn` | string | conditional | Required if `globalOptionId` omitted. |
| `valueAr` | string | conditional | Required if `globalOptionId` omitted. |
| `unit` | enum | no | One of: `KB, MB, GB, TB, MHz, GHz, mAh, Wh, W, INCH, MP, Mbps, Gbps, G, KG, HZ, RPM`. |

### 4.5 `colors[]` (array) — optional

> Source: `CreateColorRequest.java`. `localKey` + all three values are `@NotBlank`.

| JSON field | Type | Required | Rules |
|---|---|---|---|
| `localKey` | string | **yes** | Unique key within request; variants reference it (e.g. `color-silver`). |
| `valueAz` | string | **yes** | e.g. `Gümüşü` |
| `valueEn` | string | **yes** | e.g. `Silver` |
| `valueAr` | string | **yes** | e.g. `فضي` |
| `colorCode` | string | no | Hex code for UI, e.g. `#C0C0C0`. |
| `mediaIndices` | int[] | no | 0-based indices into the uploaded `files` list that belong to this color, e.g. `[0,1]`. Unclaimed files become product-level media. |

### 4.6 `variants[]` (array) — optional

> Source: `CreateVariantRequest.java`.

| JSON field | Type | Required | Rules |
|---|---|---|---|
| `sku` | string | **yes** | `@NotBlank`, ≤255 chars. Unique per variant, e.g. `PROD-RED-XL-001`. |
| `specOptionIds` | UUID[] | no | Existing `ProductSpecOption` UUIDs. |
| `specOptionLocalKeys` | string[] | no | `localKey`s of spec options defined inline in this same request (from §4.4 / §4.5). |

---

## 5. Example `request` JSON payload

Minimal valid product (only required fields):

```json
{
  "categoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "productType": "SIMPLE",
  "translations": {
    "titleAz": "Yeni Məhsul",
    "titleEn": "New Product",
    "titleAr": "منتج جديد",
    "descriptionAz": "Ətraflı təsvir",
    "descriptionEn": "Detailed description",
    "descriptionAr": "وصف تفصيلي"
  }
}
```

Full example with brand, specs (inline + reused), colors mapped to uploaded files,
and variants:

```json
{
  "categoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "brandId": "b1a85f64-5717-4562-b3fc-2c963f66afff",
  "availabilityStatus": "IN_STOCK",
  "isSuperDeal": false,
  "isLimitedStock": false,
  "productType": "SIMPLE",
  "sku": "DTAX0993",
  "isRefurbished": false,
  "status": "ACTIVE",
  "translations": {
    "titleAz": "Smartfon X", "titleEn": "Smartphone X", "titleAr": "هاتف X",
    "descriptionAz": "Təsvir", "descriptionEn": "Description", "descriptionAr": "وصف"
  },
  "specs": [
    {
      "code": "battery_capacity",
      "nameAz": "Batareya tutumu", "nameEn": "Battery Capacity", "nameAr": "سعة البطارية",
      "options": [
        { "localKey": "bat-5000", "valueAz": "5000", "valueEn": "5000", "valueAr": "5000", "unit": "mAh" }
      ]
    },
    {
      "globalSpecGroupId": "aaaaaaa1-1111-2222-3333-444444444444",
      "options": [
        { "localKey": "ram-16gb", "globalOptionId": "bbbbbbb2-1111-2222-3333-444444444444" }
      ]
    }
  ],
  "colors": [
    { "localKey": "color-silver", "valueAz": "Gümüşü", "valueEn": "Silver", "valueAr": "فضي", "colorCode": "#C0C0C0", "mediaIndices": [0, 1] },
    { "localKey": "color-black",  "valueAz": "Qara",   "valueEn": "Black",  "valueAr": "أسود", "colorCode": "#000000", "mediaIndices": [2] }
  ],
  "variants": [
    { "sku": "SMX-SILVER-16", "specOptionLocalKeys": ["color-silver", "ram-16gb"] },
    { "sku": "SMX-BLACK-16",  "specOptionLocalKeys": ["color-black",  "ram-16gb"] }
  ],
  "accessoryIds": []
}
```

For the example above, the `files` part would carry 3 binaries; indices `[0,1]` belong
to silver, `[2]` to black.

---

## 6. Success response (`ProductResponse`)

`201 Created`:

```jsonc
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": "uuid",
    "categoryId": "uuid",
    "brandId": "uuid",
    "brandName": "string",
    "productType": "SIMPLE",
    "isRefurbished": false,
    "refurbGrade": null,
    "sku": "DTAX0993",
    "availabilityStatus": "IN_STOCK",
    "isSuperDeal": false,
    "isLimitedStock": false,
    "status": "ACTIVE",
    "createdAt": "2026-06-07T10:00:00Z",
    "updatedAt": "2026-06-07T10:00:00Z",
    "title": "Smartphone X",
    "description": "Description",
    "slug": "smartphone-x",
    "media": [ { "id": "uuid", "mediaType": "…", "url": "https://…", "thumbnailUrl": "…", "isPrimary": true, "orderIndex": 0 } ],
    "specs":   [ { "id": "uuid", "code": "ram", "name": "RAM", "options": [ { "id": "uuid", "value": "16 GB", "unit": "GB" } ] } ],
    "colors":  [ { "id": "uuid", "value": "Silver", "colorCode": "#C0C0C0", "media": [ … ] } ],
    "variants":[ { "id": "uuid", "sku": "SMX-SILVER-16", "specOptionIds": ["uuid"] } ],
    "accessoryIds": []
  }
}
```

Capture `data.id` (and `data.sku`) for downstream steps in n8n.

---

## 7. Validation & business rules (will cause 4xx if violated)

- `categoryId`, `productType`, and `translations` are mandatory.
- All six translation fields must be non-blank; titles ≤ 255 chars.
- If `isRefurbished=true`, `refurbGrade` (A/B/C) is required.
- If `sku` is provided it must be globally unique; otherwise it is auto-generated.
- Each `variants[].sku` must be non-blank and ≤ 255 chars.
- Each spec group must have at least one option (`options` not empty).
- Inline spec options and colors must each have a unique `localKey` within the request;
  variants reference those keys via `specOptionLocalKeys`.
- `mediaIndices` must point to valid 0-based positions in the uploaded `files` list.

**Media upload limits** (from `application.properties`):
- Max **200 MB** per file, max **2 GB** per request.
- Accepts images (PNG/WebP/JPEG) and videos; stored in Contabo S3.

---

## 8. Related endpoints (handy for n8n)

| Purpose | Method & Path | Auth |
|---|---|---|
| List all products (all statuses) | `GET /api/admin/product?lang=EN` | ADMIN |
| Get product by ID | `GET /api/admin/product/{id}?lang=EN` | ADMIN |
| Partial update + add media | `PATCH /api/admin/product/{id}` (multipart, same shape; all fields optional) | ADMIN |
| Activate / deactivate | `PATCH /api/admin/product/{id}/status?status=ACTIVE` | ADMIN |
| Soft delete (to trash 30d) | `DELETE /api/admin/product/{id}` | ADMIN |
| Create category | `POST /api/category` | check config |
| Create brand | `POST /api/admin/brand` | ADMIN |
| Create global spec group | `POST /api/admin/specs` | ADMIN |

---

## 9. Suggested n8n workflow

1. **HTTP Request — Sign in**
   - `POST {{baseUrl}}/auth/admin/signin`, JSON body with admin email/password.
   - Save `{{$json.data.accessToken}}` (e.g. to a workflow variable or Set node).

2. **HTTP Request — Fetch categories** (and brands / specs if needed)
   - `GET {{baseUrl}}/api/category?lang=EN`.
   - Pick the target `categoryId` (map by name in a Code/Set node).

3. **Build the `request` JSON** (Set or Code node)
   - Assemble the `CreateProductRequest` object from your source data; `JSON.stringify` it.

4. **(Optional) Download images** (HTTP Request → Binary)
   - Pull each image as binary; merge so the create node has binary properties.

5. **HTTP Request — Create product**
   - `POST {{baseUrl}}/api/admin/product/create`
   - Header: `Authorization: Bearer {{accessToken}}`
   - Body type: **Form-Data (multipart)**:
     - Field `request` = the stringified JSON from step 3.
     - Field `files` = the binary image(s); add one entry per file in the same order
       your `mediaIndices` expect.
   - Let n8n set the multipart `Content-Type` / boundary automatically (do not hardcode it).

6. **Handle response**
   - On `success=true`, read `data.id` / `data.sku`.
   - On error, the envelope returns `success=false` with a `message` (and field errors for
     validation failures) — branch with an IF node and log/retry.

### n8n tips
- Token expires in ~15 min: sign in at the start o
 each execution rather than caching.
- When sending multipart in the HTTP Request node, set **Body Content Type = Form-Data**,
  add `request` as a *Form Field* (value = JSON string) and `files` as *Binary File(s)*.
- If you see a `400` complaining every field "is required" despite sending them, the
  `request` part likely wasn't sent as valid JSON text — verify it's a proper stringified
  object, not `[object Object]`.