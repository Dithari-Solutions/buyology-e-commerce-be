# Courier Admin API — Frontend Integration Guide

All courier management calls go through the ecommerce backend at `/api/admin/couriers`.
**Never call the courier service directly from the browser.**

---

## Authentication

Every request requires a valid admin JWT in the `Authorization` header:

```
Authorization: Bearer <token>
```

The token is forwarded as-is to the courier service for audit logging. Endpoints that
mutate data (POST, PATCH, DELETE) require the `ADMIN` or `COURIER_ADMIN` role. The
`DELETE` endpoint requires the `ADMIN` role exclusively.

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
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

#### Request body

```json
{
  "firstName":            "John",
  "lastName":             "Smith",
  "phone":                "+994501234567",
  "email":                "john.smith@example.com",
  "initialPassword":      "Secure#Pass1",
  "vehicleType":          "SCOOTER",
  "licensePlate":         "10 BB 456",
  "drivingLicenseNumber": "DL-7654321",
  "drivingLicenseExpiry": "2029-03-15"
}
```

#### Field reference

| Field | Type | Required | Notes |
|---|---|---|---|
| `firstName` | string | Yes | max 100 chars |
| `lastName` | string | Yes | max 100 chars |
| `phone` | string | Yes | max 30 chars |
| `email` | string | No | valid email, max 150 chars |
| `profileImageUrl` | string | No | valid URL, max 2048 chars |
| `initialPassword` | string | Yes | 8–100 chars |
| `vehicleType` | enum | Yes | `BICYCLE`, `FOOT`, `SCOOTER`, `CAR` |
| `vehicleMake` | string | No | max 100 chars |
| `vehicleModel` | string | No | max 100 chars |
| `vehicleYear` | integer | No | 1900–2100 |
| `vehicleColor` | string | No | max 50 chars |
| `licensePlate` | string | No | max 50 chars — required if `SCOOTER` or `CAR` |
| `vehicleRegistrationUrl` | string | No | valid URL |
| `drivingLicenseNumber` | string | No | max 100 chars — required if `SCOOTER` or `CAR` |
| `drivingLicenseExpiry` | date | No | `YYYY-MM-DD` — required if `SCOOTER` or `CAR` |
| `drivingLicenseFrontUrl` | string | No | valid URL |
| `drivingLicenseBackUrl` | string | No | valid URL |

**Driving licence rule** — apply this in your form UI:

```ts
if (vehicleType === 'SCOOTER' || vehicleType === 'CAR') {
  // show and require: licensePlate, drivingLicenseNumber, drivingLicenseExpiry
} else {
  // hide all driving licence + license plate fields
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

#### Query parameters

| Param | Type | Default | Description |
|---|---|---|---|
| `page` | integer | `0` | 0-based page number |
| `size` | integer | `20` | Page size |
| `status` | string | — | Filter: `ACTIVE`, `SUSPENDED`, `INACTIVE`, `PENDING` |
| `vehicleType` | string | — | Filter: `BICYCLE`, `FOOT`, `SCOOTER`, `CAR` |
| `search` | string | — | Search by name or phone |

#### Example

```
GET /api/admin/couriers?page=0&size=20&status=ACTIVE&vehicleType=SCOOTER
```

#### Success response — `200 OK`

```json
{
  "content": [
    {
      "courierId":     "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "firstName":     "John",
      "lastName":      "Smith",
      "phone":         "+994501234567",
      "accountStatus": "ACTIVE",
      "vehicleType":   "SCOOTER"
    }
  ],
  "page":          0,
  "size":          20,
  "totalElements": 1,
  "totalPages":    1
}
```

---

### 3. Get courier by ID

```
GET /api/admin/couriers/{courierId}
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

#### Path parameter

| Param | Type | Description |
|---|---|---|
| `courierId` | UUID string | The courier's unique ID |

#### Success response — `200 OK`

```json
{
  "courierId":              "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "firstName":              "John",
  "lastName":               "Smith",
  "phone":                  "+994501234567",
  "email":                  "john.smith@example.com",
  "profileImageUrl":        null,
  "accountStatus":          "ACTIVE",
  "vehicleType":            "SCOOTER",
  "vehicleMake":            null,
  "vehicleModel":           null,
  "vehicleYear":            null,
  "licensePlate":           "10 BB 456",
  "drivingLicenseNumber":   "DL-7654321",
  "drivingLicenseExpiry":   "2029-03-15",
  "requiresDrivingLicense": true,
  "createdAt":              "2026-03-24T10:00:00Z"
}
```

---

### 4. Update courier status

```
PATCH /api/admin/couriers/{courierId}/status
```

**Required role:** `ADMIN` or `COURIER_ADMIN`

Use this to activate, suspend, or deactivate a courier without deleting their account.

#### Path parameter

| Param | Type | Description |
|---|---|---|
| `courierId` | UUID string | The courier's unique ID |

#### Request body

```json
{
  "status": "SUSPENDED",
  "reason": "Repeated delivery complaints"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `status` | enum | Yes | `ACTIVE`, `SUSPENDED`, `INACTIVE`, `PENDING` |
| `reason` | string | No | Max 500 chars — shown in courier service audit log |

#### Success response — `200 OK`

```json
{
  "courierId":     "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "accountStatus": "SUSPENDED",
  "updatedAt":     "2026-03-24T12:00:00Z"
}
```

---

### 5. Delete a courier

```
DELETE /api/admin/couriers/{courierId}
```

**Required role:** `ADMIN` only (`COURIER_ADMIN` cannot delete)

Permanently deactivates the courier account. This action is forwarded to and recorded
by the courier service audit log.

#### Path parameter

| Param | Type | Description |
|---|---|---|
| `courierId` | UUID string | The courier's unique ID |

#### Success response — `204 No Content` or `200 OK`

No body on `204`. On `200`, the courier service may return a confirmation object.

---

## Error responses

All errors are passed through from the courier service without modification.

| Status | Meaning |
|---|---|
| `400 Bad Request` | Validation failed — check `errors` array in response body |
| `401 Unauthorized` | Admin token missing or expired — redirect to login |
| `403 Forbidden` | Token valid but insufficient role |
| `404 Not Found` | Courier ID does not exist |
| `409 Conflict` | Phone number already registered (create only) |
| `429 Too Many Requests` | Admin rate limit exceeded (50 couriers/hour on create) |
| `502 Bad Gateway` | Courier service is unreachable — show retry prompt |

### Error body shape (from courier service)

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
export type VehicleType = 'BICYCLE' | 'FOOT' | 'SCOOTER' | 'CAR';
export type CourierStatus = 'ACTIVE' | 'SUSPENDED' | 'INACTIVE' | 'PENDING';

export interface CreateCourierRequest {
  firstName: string;
  lastName: string;
  phone: string;
  email?: string;
  profileImageUrl?: string;
  initialPassword: string;
  vehicleType: VehicleType;
  vehicleMake?: string;
  vehicleModel?: string;
  vehicleYear?: number;
  vehicleColor?: string;
  licensePlate?: string;
  vehicleRegistrationUrl?: string;
  drivingLicenseNumber?: string;
  drivingLicenseExpiry?: string;       // "YYYY-MM-DD"
  drivingLicenseFrontUrl?: string;
  drivingLicenseBackUrl?: string;
}

export interface UpdateCourierStatusRequest {
  status: CourierStatus;
  reason?: string;
}

export interface CourierSummary {
  courierId: string;
  firstName: string;
  lastName: string;
  phone: string;
  accountStatus: CourierStatus;
  vehicleType: VehicleType;
}

export interface CourierDetail extends CourierSummary {
  email?: string;
  profileImageUrl?: string;
  vehicleMake?: string;
  vehicleModel?: string;
  vehicleYear?: number;
  vehicleColor?: string;
  licensePlate?: string;
  vehicleRegistrationUrl?: string;
  drivingLicenseNumber?: string;
  drivingLicenseExpiry?: string;
  drivingLicenseFrontUrl?: string;
  drivingLicenseBackUrl?: string;
  requiresDrivingLicense: boolean;
  createdAt: string;
}

export interface CourierListResponse {
  content: CourierSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
```

---

## Fetch examples

```ts
const BASE = '/api/admin/couriers';

// 1. Create
const res = await fetch(BASE, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  body: JSON.stringify(createCourierRequest),
});

// 2. List (with filters)
const params = new URLSearchParams({ page: '0', size: '20', status: 'ACTIVE' });
const res = await fetch(`${BASE}?${params}`, {
  headers: { Authorization: `Bearer ${token}` },
});

// 3. Get by ID
const res = await fetch(`${BASE}/${courierId}`, {
  headers: { Authorization: `Bearer ${token}` },
});

// 4. Update status
const res = await fetch(`${BASE}/${courierId}/status`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  body: JSON.stringify({ status: 'SUSPENDED', reason: 'Policy violation' }),
});

// 5. Delete
const res = await fetch(`${BASE}/${courierId}`, {
  method: 'DELETE',
  headers: { Authorization: `Bearer ${token}` },
});
```

---

## Environment variables

These are set on the **backend** only — never expose them to the frontend:

| Variable | Dev default | Description |
|---|---|---|
| `COURIER_SERVICE_URL` | `http://localhost:8081` | Internal URL of buyology-courier-service |
| `COURIER_SERVICE_TIMEOUT_MS` | `5000` | Max ms to wait for a response |

Docker Compose:
```yaml
environment:
  COURIER_SERVICE_URL: http://buyology-courier-service:8081
```
