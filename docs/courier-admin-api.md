# Courier Admin API — Frontend Integration Guide

All courier management calls go through the ecommerce backend at `/api/admin/couriers`.
**Never call the courier service directly from the browser.**

---

## Authentication

Every request requires a valid admin JWT in the `Authorization` header:

```
Authorization: Bearer <token>
```

The backend generates a separate RS256 service-to-service JWT and forwards it to the
courier service — your admin Bearer token is used only to authenticate with this backend.

Required role for all endpoints: `ADMIN` or `COURIER_ADMIN`.

---

## Base URL

```
/api/admin/couriers
```

---

## Endpoints

### 1. Create a courier

```
POST /api/admin/couriers
Content-Type: multipart/form-data
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

This endpoint accepts a `multipart/form-data` request — **not** JSON. Send the fields
JSON as a `data` part and any images as separate file parts.

#### Form parts

| Part name | Type | Required | Notes |
|---|---|---|---|
| `data` | JSON string | Yes | See field reference below |
| `profileImage` | file | No | JPEG, PNG, or WebP — max 10 MB |
| `vehicleRegistration` | file | No | JPEG, PNG, or WebP — max 10 MB |
| `drivingLicenceFront` | file | Yes if SCOOTER/CAR | JPEG, PNG, or WebP — max 10 MB |
| `drivingLicenceBack` | file | Yes if SCOOTER/CAR | JPEG, PNG, or WebP — max 10 MB |

#### `data` JSON field reference

| Field | Type | Required | Notes |
|---|---|---|---|
| `firstName` | string | Yes | max 100 chars |
| `lastName` | string | Yes | max 100 chars |
| `phone` | string | Yes | max 30 chars |
| `email` | string | No | valid email, max 150 chars |
| `initialPassword` | string | Yes | 8–100 chars |
| `vehicleType` | enum | Yes | `BICYCLE`, `FOOT`, `SCOOTER`, `CAR` |
| `vehicleMake` | string | No | max 100 chars |
| `vehicleModel` | string | No | max 100 chars |
| `vehicleYear` | integer | No | 1900–2100 |
| `vehicleColor` | string | No | max 50 chars |
| `licensePlate` | string | No | max 50 chars — required if `SCOOTER` or `CAR` |
| `drivingLicenseNumber` | string | No | max 100 chars — required if `SCOOTER` or `CAR` |
| `drivingLicenseExpiry` | date | No | `YYYY-MM-DD` — required if `SCOOTER` or `CAR` |

**Driving licence rule** — apply in your form UI:

```ts
const motorized = vehicleType === 'SCOOTER' || vehicleType === 'CAR';

if (motorized) {
  // show and require: licensePlate, drivingLicenseNumber, drivingLicenseExpiry
  // show and require file inputs: drivingLicenceFront, drivingLicenceBack
} else {
  // hide all driving licence fields and file inputs
}
```

#### Success response — `201 Created`

```json
{
  "courierId":              "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "firstName":              "John",
  "lastName":               "Smith",
  "phone":                  "+994501234567",
  "accountStatus":          "ACTIVE",
  "vehicleType":            "SCOOTER",
  "requiresDrivingLicense": true
}
```

---

### 2. List couriers

```
GET /api/admin/couriers
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

#### Query parameters (all optional, forwarded as-is to courier service)

| Param | Type | Example | Description |
|---|---|---|---|
| `status` | enum | `ACTIVE` | Filter: `ACTIVE`, `OFFLINE`, `SUSPENDED` |
| `vehicleType` | enum | `SCOOTER` | Filter: `BICYCLE`, `FOOT`, `SCOOTER`, `CAR` |
| `isAvailable` | boolean | `true` | Filter by availability |
| `page` | integer | `0` | 0-based page number |
| `size` | integer | `20` | Page size |
| `sort` | string | `createdAt,desc` | Sort field and direction |

#### Success response — `200 OK`

```json
{
  "content": [
    {
      "id":          "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "firstName":   "John",
      "lastName":    "Smith",
      "phone":       "+994501234567",
      "status":      "ACTIVE",
      "isAvailable": false,
      "vehicleType": "SCOOTER"
    }
  ],
  "totalElements": 42,
  "totalPages":    3,
  "size":          20,
  "number":        0
}
```

---

### 3. Get courier by ID

```
GET /api/admin/couriers/{id}
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

#### Success response — `200 OK`

```json
{
  "id":                     "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "firstName":              "John",
  "lastName":               "Smith",
  "phone":                  "+994501234567",
  "email":                  "john@example.com",
  "vehicleType":            "SCOOTER",
  "status":                 "OFFLINE",
  "isAvailable":            false,
  "rating":                 null,
  "profileImageUrl":        "/uploads/couriers/profile/a1b2c3.jpg",
  "drivingLicenceImageUrl": "/uploads/couriers/licence/d4e5f6.jpg",
  "createdAt":              "2026-03-25T10:00:00Z",
  "updatedAt":              "2026-03-25T10:00:00Z"
}
```

#### Displaying images

`profileImageUrl` and `drivingLicenceImageUrl` are **relative paths** served by the
courier service. Build the full URL with the courier service base URL, which your
backend team will provide. **Do not put `COURIER_SERVICE_URL` in frontend config** —
ask the backend for a dedicated image-proxy endpoint if needed.

```ts
// Example — confirm the correct public base URL with the backend team
const imgSrc = courier.profileImageUrl
  ? `${COURIER_IMAGE_BASE_URL}${courier.profileImageUrl}`
  : null;
```

---

### 4. Update courier profile

```
PATCH /api/admin/couriers/{id}
Content-Type: multipart/form-data
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

All parts are optional — omit any part you do not want to change.

#### Form parts

| Part name | Type | Required | Notes |
|---|---|---|---|
| `data` | JSON string | Yes | See field reference below — omit or null any field to leave it unchanged |
| `profileImage` | file | No | Replaces existing profile photo — JPEG, PNG, or WebP, max 10 MB |
| `drivingLicenceImage` | file | No | Replaces existing driving licence image — JPEG, PNG, or WebP, max 10 MB |

#### `data` JSON field reference

| Field | Type | Notes |
|---|---|---|
| `firstName` | string | max 100 chars |
| `lastName` | string | max 100 chars |
| `email` | string | valid email, max 150 chars |
| `vehicleType` | enum | `BICYCLE`, `FOOT`, `SCOOTER`, `CAR` |

#### Success response — `200 OK`

Same shape as the GET /{id} response above.

---

### 5. Update courier operational status

```
PATCH /api/admin/couriers/{id}/status
Content-Type: application/json
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

Use this to activate, suspend, or take a courier offline without deleting their account.

#### Request body

```json
{ "status": "SUSPENDED" }
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `status` | enum | Yes | `ACTIVE`, `OFFLINE`, `SUSPENDED` |

#### Success response — `200 OK`

```json
{
  "id":        "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status":    "SUSPENDED",
  "updatedAt": "2026-03-25T12:00:00Z"
}
```

---

### 6. Toggle courier availability

```
PATCH /api/admin/couriers/{id}/availability
Content-Type: application/json
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

Availability can only be set to `true` when the courier's status is `ACTIVE`. The
courier service enforces this rule.

#### Request body

```json
{ "available": true }
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `available` | boolean | Yes | `true` or `false` |

#### Success response — `200 OK`

```json
{
  "id":          "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "isAvailable": true,
  "updatedAt":   "2026-03-25T12:00:00Z"
}
```

---

### 7. Delete a courier (soft delete)

```
DELETE /api/admin/couriers/{id}
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

Soft-deletes the courier account. The account is deactivated on the courier service
and all records are retained for auditing.

#### Success response — `204 No Content`

No body.

---

## Error responses

All errors are passed through from the courier service without modification.

| Status | Meaning |
|---|---|
| `400 Bad Request` | Validation failed — check `errors` array in response body |
| `401 Unauthorized` | Admin token missing or expired — redirect to login |
| `403 Forbidden` | Token valid but insufficient role |
| `404 Not Found` | Courier ID does not exist |
| `409 Conflict` | Phone number or license plate already registered |
| `429 Too Many Requests` | Admin rate limit exceeded (50 couriers/hour on create) |
| `502 Bad Gateway` | Courier service is unreachable — show a retry prompt |

### Error body shape

```json
{
  "status":  400,
  "error":   "Bad Request",
  "message": "drivingLicenseNumber is required for SCOOTER vehicles",
  "errors": [
    { "field": "drivingLicenseNumber", "message": "must not be blank" }
  ]
}
```

---

## TypeScript types

```ts
export type VehicleType   = 'BICYCLE' | 'FOOT' | 'SCOOTER' | 'CAR';
export type CourierStatus = 'ACTIVE' | 'OFFLINE' | 'SUSPENDED';

// ── Create ────────────────────────────────────────────────────────────────────

export interface CreateCourierData {
  firstName: string;
  lastName: string;
  phone: string;
  email?: string;
  initialPassword: string;
  vehicleType: VehicleType;
  vehicleMake?: string;
  vehicleModel?: string;
  vehicleYear?: number;
  vehicleColor?: string;
  licensePlate?: string;          // required for SCOOTER / CAR
  drivingLicenseNumber?: string;  // required for SCOOTER / CAR
  drivingLicenseExpiry?: string;  // "YYYY-MM-DD" — required for SCOOTER / CAR
}

// ── Update profile ────────────────────────────────────────────────────────────

export interface UpdateCourierData {
  firstName?: string;
  lastName?: string;
  email?: string;
  vehicleType?: VehicleType;
}

// ── Status / availability ─────────────────────────────────────────────────────

export interface UpdateStatusRequest    { status: CourierStatus; }
export interface UpdateAvailabilityRequest { available: boolean; }

// ── Responses ─────────────────────────────────────────────────────────────────

export interface CourierSummary {
  id:          string;
  firstName:   string;
  lastName:    string;
  phone:       string;
  status:      CourierStatus;
  isAvailable: boolean;
  vehicleType: VehicleType;
}

export interface CourierDetail extends CourierSummary {
  email?:                  string;
  rating?:                 number;
  profileImageUrl?:        string;  // relative path — see "Displaying images" above
  drivingLicenceImageUrl?: string;  // relative path — see "Displaying images" above
  createdAt:               string;
  updatedAt:               string;
}

export interface CourierListResponse {
  content:       CourierSummary[];
  totalElements: number;
  totalPages:    number;
  size:          number;
  number:        number;
}
```

---

## Fetch examples

```ts
const BASE = '/api/admin/couriers';
const headers = { Authorization: `Bearer ${token}` };

// ── 1. Create courier (multipart) ─────────────────────────────────────────────
const data: CreateCourierData = {
  firstName: 'John', lastName: 'Smith',
  phone: '+994501234567', email: 'john@example.com',
  initialPassword: 'Secure#Pass1',
  vehicleType: 'SCOOTER',
  licensePlate: '10 BB 456',
  drivingLicenseNumber: 'DL-7654321',
  drivingLicenseExpiry: '2029-03-15',
};

const form = new FormData();
form.append('data', JSON.stringify(data));
if (profileImageFile)        form.append('profileImage',        profileImageFile);
if (vehicleRegFile)          form.append('vehicleRegistration', vehicleRegFile);
if (licenceFrontFile)        form.append('drivingLicenceFront', licenceFrontFile);
if (licenceBackFile)         form.append('drivingLicenceBack',  licenceBackFile);

const res = await fetch(BASE, { method: 'POST', headers, body: form });

// ── 2. List couriers ──────────────────────────────────────────────────────────
const params = new URLSearchParams({ page: '0', size: '20', status: 'ACTIVE' });
const list: CourierListResponse = await fetch(`${BASE}?${params}`, { headers }).then(r => r.json());

// ── 3. Get courier by ID ──────────────────────────────────────────────────────
const courier: CourierDetail = await fetch(`${BASE}/${id}`, { headers }).then(r => r.json());

// ── 4. Update courier profile (multipart) ────────────────────────────────────
const updateData: UpdateCourierData = { firstName: 'Johnny' };
const updateForm = new FormData();
updateForm.append('data', JSON.stringify(updateData));
if (newProfileImage) updateForm.append('profileImage', newProfileImage);
if (newLicenceImage) updateForm.append('drivingLicenceImage', newLicenceImage);

const updated = await fetch(`${BASE}/${id}`, { method: 'PATCH', headers, body: updateForm });

// ── 5. Update status ──────────────────────────────────────────────────────────
await fetch(`${BASE}/${id}/status`, {
  method: 'PATCH',
  headers: { ...headers, 'Content-Type': 'application/json' },
  body: JSON.stringify({ status: 'SUSPENDED' } satisfies UpdateStatusRequest),
});

// ── 6. Toggle availability ────────────────────────────────────────────────────
await fetch(`${BASE}/${id}/availability`, {
  method: 'PATCH',
  headers: { ...headers, 'Content-Type': 'application/json' },
  body: JSON.stringify({ available: true } satisfies UpdateAvailabilityRequest),
});

// ── 7. Delete courier ─────────────────────────────────────────────────────────
await fetch(`${BASE}/${id}`, { method: 'DELETE', headers });
```

---

## Environment variables (backend only)

These are set on the backend server — **never expose them to the frontend**:

| Variable | Dev default | Description |
|---|---|---|
| `COURIER_SERVICE_URL` | `http://localhost:8081` | Internal URL of buyology-courier-service |
| `COURIER_SERVICE_TIMEOUT_MS` | `10000` | Max ms to wait for a courier service response |
| `COURIER_SERVICE_PRIVATE_KEY` | — | PKCS8 RSA private key (PEM) — signs service JWTs |
| `COURIER_SERVICE_JWT_EXPIRY` | `60` | JWT TTL in seconds |

Docker Compose:
```yaml
environment:
  COURIER_SERVICE_URL: http://buyology-courier-service:8081
  COURIER_SERVICE_PRIVATE_KEY: |
    -----BEGIN PRIVATE KEY-----
    MIIEvQ...
    -----END PRIVATE KEY-----
```
