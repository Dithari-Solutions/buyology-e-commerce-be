# Store Module — API Handoff

**Base URL:** `https://api-dev.dithari.com`
**Auth:** All write endpoints require a valid JWT access token in the `Authorization` header.

```
Authorization: Bearer <access_token>
```

---

## Response Envelope

Every endpoint returns the same wrapper:

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

On failure, `data` is `null` and `statusCode` + `message` describe the error.

---

## Enums

### StoreStatus
| Value | Meaning |
|---|---|
| `ACTIVE` | Store is live and visible |
| `INACTIVE` | Store is disabled |
| `SUSPENDED` | Store has been suspended |
| `PENDING_APPROVAL` | Awaiting admin approval (default on creation) |

### StoreAdminRole
| Value | Meaning |
|---|---|
| `STORE_OWNER` | Full control over the store |
| `STORE_MANAGER` | Manage operations |
| `STORE_STAFF` | Limited access |

### DayOfWeek (Java enum, sent as string)
`MONDAY` `TUESDAY` `WEDNESDAY` `THURSDAY` `FRIDAY` `SATURDAY` `SUNDAY`

---

## 1. Country

> Reference data. Must create countries before creating stores.

### Create Country
`POST /api/countries`

**Request Body:**
```json
{
  "code": "AZ",
  "name": "Azerbaijan",
  "currency": "AZN",
  "isActive": true
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `code` | string | Yes | ISO 3166-1 alpha-2 or alpha-3 (2–3 chars) |
| `name` | string | Yes | Max 100 chars |
| `currency` | string | Yes | ISO 4217, exactly 3 chars (e.g. `USD`, `AZN`) |
| `isActive` | boolean | No | Defaults to `true` |

**Response `data`:**
```json
{
  "id": "uuid",
  "code": "AZ",
  "name": "Azerbaijan",
  "currency": "AZN",
  "isActive": true,
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:00:00Z"
}
```

---

### Get All Countries
`GET /api/countries`

**Response `data`:** Array of country objects (same shape as above).

---

### Get Active Countries
`GET /api/countries/active`

**Response `data`:** Array of country objects where `isActive: true`.

---

### Get Country by ID
`GET /api/countries/{id}`

**Response `data`:** Single country object.

---

### Update Country
`PATCH /api/countries/{id}`

All fields optional. Only send what needs to change.

```json
{
  "name": "Republic of Azerbaijan",
  "isActive": false
}
```

---

### Deactivate Country
`DELETE /api/countries/{id}`

Soft deactivation (sets `isActive` to false). `data` is `null` on success.

---

## 2. Store

### Create Store
`POST /api/stores`

**Content-Type:** `multipart/form-data`

| Part | Type | Required | Notes |
|---|---|---|---|
| `request` | JSON string | Yes | Store fields (see below) |
| `banner` | file | No | Image file for the store banner |

**`request` part (JSON):**
```json
{
  "countryId": "uuid",
  "name": "Buyology Baku",
  "slug": "buyology-baku",
  "status": "PENDING_APPROVAL",
  "contactEmail": "baku@buyology.com",
  "contactPhone": "+994501234567",
  "translations": [
    {
      "language": "az",
      "name": "Buyology Bakı",
      "description": "Azərbaycanda onlayn mağaza"
    },
    {
      "language": "en",
      "name": "Buyology Baku",
      "description": "Online store in Azerbaijan"
    }
  ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `countryId` | UUID | Yes | Must match an existing country |
| `name` | string | Yes | Max 255 chars |
| `slug` | string | Yes | Max 255 chars, must be unique |
| `status` | StoreStatus | No | Defaults to `PENDING_APPROVAL` |
| `contactEmail` | string | No | Max 255 chars |
| `contactPhone` | string | No | Max 50 chars |
| `translations` | array | No | See Translation shape below |

**Translation object:**
| Field | Type | Required | Notes |
|---|---|---|---|
| `language` | string | Yes | Language code, e.g. `en`, `az`, `ar` (max 5 chars) |
| `name` | string | Yes | Max 255 chars |
| `description` | string | No | Free text |

**Response `data`:**
```json
{
  "id": "uuid",
  "countryId": "uuid",
  "countryName": "Azerbaijan",
  "name": "Buyology Baku",
  "slug": "buyology-baku",
  "status": "PENDING_APPROVAL",
  "bannerUrl": "/store/{storeId}/banner.png",
  "contactEmail": "baku@buyology.com",
  "contactPhone": "+994501234567",
  "translations": [
    {
      "id": "uuid",
      "language": "az",
      "name": "Buyology Bakı",
      "description": "Azərbaycanda onlayn mağaza",
      "createdAt": "2026-03-21T10:00:00Z",
      "updatedAt": "2026-03-21T10:00:00Z"
    }
  ],
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:00:00Z"
}
```

---

### Get All Stores
`GET /api/stores`

**Response `data`:** Array of store objects.

---

### Get Store by ID
`GET /api/stores/{id}`

**Response `data`:** Single store object.

---

### Get Store by Slug
`GET /api/stores/slug/{slug}`

**Response `data`:** Single store object.

---

### Update Store
`PATCH /api/stores/{id}`

**Content-Type:** `multipart/form-data`

| Part | Type | Required | Notes |
|---|---|---|---|
| `request` | JSON string | Yes | Fields to update (all optional) |
| `banner` | file | No | New banner image — replaces the existing one |

**`request` part (JSON) — all fields optional:**
```json
{
  "status": "ACTIVE",
  "contactEmail": "newemail@buyology.com"
}
```

---

### Delete Store
`DELETE /api/stores/{id}`

Soft-delete. `data` is `null` on success.

---

## 3. Store Translations

### Add Translation
`POST /api/stores/{storeId}/translations`

```json
{
  "language": "ar",
  "name": "بويولوجي باكو",
  "description": "متجر في أذربيجان"
}
```

**Response `data`:** Translation object.

---

### Update Translation
`PATCH /api/stores/{storeId}/translations/{language}`

`language` path param is the language code (e.g. `az`, `en`).

```json
{
  "language": "az",
  "name": "Buyology Bakı (Yeni)",
  "description": "Yenilənmiş təsvir"
}
```

**Response `data`:** Updated translation object.

---

### Delete Translation
`DELETE /api/stores/{storeId}/translations/{language}`

`data` is `null` on success.

---

## 4. Store Locations (Branches)

### Add Location to Store
`POST /api/stores/{storeId}/locations`

> When `isPrimary: true` is set, the existing primary branch is automatically demoted.

**Request Body:**
```json
{
  "branchName": "Baku Main Branch",
  "address": "28 May Street, Building 5",
  "city": "Baku",
  "state": "Baku",
  "country": "AZ",
  "postalCode": "AZ1000",
  "latitude": 40.4093,
  "longitude": 49.8671,
  "isPrimary": true
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `branchName` | string | Yes | Max 255 chars |
| `address` | string | Yes | Full street address |
| `city` | string | Yes | Max 100 chars |
| `state` | string | No | Max 100 chars |
| `country` | string | Yes | ISO 3-letter or 2-letter code (2–3 chars) |
| `postalCode` | string | No | Max 20 chars |
| `latitude` | double | Yes | Decimal degrees |
| `longitude` | double | Yes | Decimal degrees |
| `isPrimary` | boolean | No | Defaults to `false` |

**Response `data`:**
```json
{
  "id": "uuid",
  "storeId": "uuid",
  "branchName": "Baku Main Branch",
  "address": "28 May Street, Building 5",
  "city": "Baku",
  "state": "Baku",
  "country": "AZ",
  "postalCode": "AZ1000",
  "latitude": 40.4093,
  "longitude": 49.8671,
  "isPrimary": true,
  "isActive": true,
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:00:00Z"
}
```

---

### Get All Locations for a Store
`GET /api/stores/{storeId}/locations`

**Response `data`:** Array of location objects.

---

### Get Location by ID
`GET /api/stores/locations/{locationId}`

**Response `data`:** Single location object.

---

### Update Location
`PATCH /api/stores/locations/{locationId}`

All fields optional.

```json
{
  "branchName": "Baku South Branch",
  "isPrimary": false,
  "isActive": false
}
```

---

### Deactivate Location
`DELETE /api/stores/locations/{locationId}`

Sets `isActive` to `false`. `data` is `null` on success.

---

## 5. Operating Hours

### Set Operating Hours for a Location
`POST /api/stores/locations/{locationId}/hours`

One entry per day. Call this endpoint once per day of the week.

**Request Body:**
```json
{
  "dayOfWeek": "MONDAY",
  "openTime": "09:00:00",
  "closeTime": "18:00:00",
  "isClosed": false
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `dayOfWeek` | DayOfWeek | Yes | `MONDAY` – `SUNDAY` |
| `openTime` | LocalTime | No | `HH:mm:ss` format. Omit if `isClosed: true` |
| `closeTime` | LocalTime | No | `HH:mm:ss` format. Omit if `isClosed: true` |
| `isClosed` | boolean | No | Defaults to `false`. Set `true` for days off |

**Closed day example:**
```json
{
  "dayOfWeek": "SUNDAY",
  "isClosed": true
}
```

**Response `data`:**
```json
{
  "id": "uuid",
  "locationId": "uuid",
  "dayOfWeek": "MONDAY",
  "openTime": "09:00:00",
  "closeTime": "18:00:00",
  "isClosed": false,
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:00:00Z"
}
```

---

### Get All Operating Hours for a Location
`GET /api/stores/locations/{locationId}/hours`

**Response `data`:** Array of operating hours objects (up to 7 entries, one per day).

---

### Get Operating Hours Entry by ID
`GET /api/stores/hours/{hoursId}`

**Response `data`:** Single operating hours object.

---

### Update Operating Hours Entry
`PATCH /api/stores/hours/{hoursId}`

All fields optional.

```json
{
  "openTime": "10:00:00",
  "closeTime": "20:00:00"
}
```

---

### Delete Operating Hours Entry
`DELETE /api/stores/hours/{hoursId}`

Removes the entry entirely. `data` is `null` on success.

---

## 6. Store Admins

> Assigns existing platform users (with `userType: ADMIN`) to a store with a specific role.

### Assign Admin to Store
`POST /api/stores/{storeId}/admins`

```json
{
  "userId": "uuid",
  "storeRole": "STORE_MANAGER",
  "assignedById": "uuid"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `userId` | UUID | Yes | Must be an existing user's `users.id` |
| `storeRole` | StoreAdminRole | Yes | `STORE_OWNER`, `STORE_MANAGER`, or `STORE_STAFF` |
| `assignedById` | UUID | No | UUID of the admin who performed the assignment |

**Response `data`:**
```json
{
  "id": "uuid",
  "storeId": "uuid",
  "storeName": "Buyology Baku",
  "userId": "uuid",
  "userFirstName": "John",
  "userLastName": "Doe",
  "storeRole": "STORE_MANAGER",
  "isActive": true,
  "assignedById": "uuid",
  "assignedAt": "2026-03-21T10:00:00Z",
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:00:00Z"
}
```

---

### Get All Admins of a Store
`GET /api/stores/{storeId}/admins`

**Response `data`:** Array of store admin objects (includes inactive).

---

### Get Active Admins of a Store
`GET /api/stores/{storeId}/admins/active`

**Response `data`:** Array of store admin objects where `isActive: true`.

---

### Get Store Admin by Assignment ID
`GET /api/stores/admins/{adminId}`

`adminId` is the `id` of the store admin assignment record, not the user's ID.

**Response `data`:** Single store admin object.

---

### Update Store Admin Role / Status
`PATCH /api/stores/admins/{adminId}`

All fields optional.

```json
{
  "storeRole": "STORE_OWNER",
  "isActive": true
}
```

**Response `data`:** Updated store admin object.

---

### Remove Store Admin
`DELETE /api/stores/admins/{adminId}`

Sets `isActive` to `false`. Does not delete the record. `data` is `null` on success.

---

## Common Error Responses

| HTTP Status | Typical Cause |
|---|---|
| `400` | Validation failure — missing required field or bad format |
| `404` | Resource not found (store, location, country, admin) |
| `409` | Conflict — duplicate slug, duplicate translation language |
| `500` | Unexpected server error |

**Error response shape:**
```json
{
  "statusCode": 404,
  "message": "Store not found",
  "data": null
}
```

---

## Typical Admin Dashboard Flow

```
1. GET  /api/countries/active          → populate country dropdown
2. POST /api/stores                    → create store (status: PENDING_APPROVAL)
3. PATCH /api/stores/{id}              → approve store (status: ACTIVE)
4. POST /api/stores/{storeId}/locations → add branch
5. POST /api/stores/locations/{id}/hours (x7) → set weekly hours
6. POST /api/stores/{storeId}/admins   → assign store manager
```
