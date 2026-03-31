# Admin Handoff — Store, Delivery & Order Changes

> **Date:** 2026-03-31
> **Scope:** New delivery-info endpoint, automatic courier dispatch, and order management flow.

---

## 1. New Endpoint — Store Delivery Info

### `GET /api/stores/delivery-info`

No authentication required. Call this before checkout to show the customer which stores are nearby and which offer express delivery.

**Query parameters:**

| Param | Type | Required | Description |
|---|---|---|---|
| `country` | string | yes | ISO 3-letter country code, e.g. `AED` → use `AE` |
| `lat` | double | yes | Customer's latitude |
| `lng` | double | yes | Customer's longitude |

**Example:**
```
GET /api/stores/delivery-info?country=AE&lat=25.2048&lng=55.2708
```

**Response:**
```json
{
  "success": true,
  "data": {
    "cities": ["Abu Dhabi", "Dubai", "Sharjah"],
    "stores": [
      {
        "locationId": "uuid",
        "storeId": "uuid",
        "branchName": "Dubai Mall Branch",
        "city": "Dubai",
        "address": "Dubai Mall, Ground Floor",
        "latitude": 25.1972,
        "longitude": 55.2796,
        "distanceKm": 3.2,
        "expressDelivery": true
      },
      {
        "locationId": "uuid",
        "storeId": "uuid",
        "branchName": "Abu Dhabi Main Branch",
        "city": "Abu Dhabi",
        "address": "Corniche Road",
        "latitude": 24.4539,
        "longitude": 54.3773,
        "distanceKm": 140.5,
        "expressDelivery": false
      }
    ]
  }
}
```

**Field meanings:**

| Field | Description |
|---|---|
| `cities` | All distinct cities that have at least one active store location in the given country |
| `distanceKm` | Straight-line distance from the customer's coordinates to the store branch (rounded to 1 decimal) |
| `expressDelivery: true` | Store is within ≈12.5 km — order will be assigned `LOCAL_EXPRESS` (≤30 min courier) |
| `expressDelivery: false` | Store is outside that radius — order will be `INTERNATIONAL` shipping |

---

## 2. Managing Store Locations (Admin)

### Add a branch to a store

```
POST /api/stores/{storeId}/locations
Authorization: Bearer <admin_token>

{
  "branchName": "Dubai Mall Branch",
  "address": "Dubai Mall, Ground Floor",
  "city": "Dubai",
  "state": "",
  "country": "AE",
  "postalCode": "",
  "latitude": 25.1972,
  "longitude": 55.2796,
  "isPrimary": false
}
```

> Setting `isPrimary: true` automatically demotes the previous primary branch.

### List all branches for a store

```
GET /api/stores/{storeId}/locations
```

### Update a branch

```
PATCH /api/stores/locations/{locationId}

{
  "branchName": "Updated Name",
  "isActive": false
}
```

> To disable a branch from appearing in delivery-info results, set `isActive: false`.

### Deactivate a branch

```
DELETE /api/stores/locations/{locationId}
```

> Soft-delete — sets `isActive = false`. Does not remove the record.

---

## 3. Order Flow Changes

### Delivery method is now auto-assigned

Orders no longer require `deliveryMethod` in the request. The backend assigns it automatically:

| Condition | Assigned method |
|---|---|
| All cart items' stores are within ≈12.5 km of delivery address | `LOCAL_EXPRESS` |
| Any store is outside that radius | `INTERNATIONAL` |

### LOCAL_EXPRESS orders are automatically sent to the courier backend

When a `LOCAL_EXPRESS` order payment succeeds, the backend **immediately POSTs the order** to the courier service at `POST /api/orders`. No admin action is needed to dispatch it.

The courier backend receives:

```json
{
  "orderId": "uuid",
  "customerId": "uuid",
  "recipientFirstName": "Firdovsi",
  "recipientLastName": "Rzaev",
  "recipientPhone": "+994103248325",
  "addressLine1": "Sulh 189",
  "addressLine2": "Apt 272",
  "city": "Sumgait",
  "country": "AZ",
  "deliveryLatitude": 40.5891,
  "deliveryLongitude": 49.6323,
  "totalAmount": 25.00,
  "currency": "AED"
}
```

---

## 4. Admin Order Management

### List all orders

```
GET /api/admin/orders?status=PAID&deliveryMethod=LOCAL_EXPRESS&page=0&size=20
Authorization: Bearer <admin_token>
```

Optional filters: `status`, `deliveryMethod`.

### Get full order detail

```
GET /api/admin/orders/{orderId}
```

### Update order status

```
PATCH /api/admin/orders/{orderId}/status

{
  "status": "COURIER_ASSIGNED",
  "courierUserId": "uuid",
  "notes": "Assigned to courier A"
}
```

**Allowed status transitions:**

```
PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → IN_TRANSIT → DELIVERED
                        ↓
                  COURIER_ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED
                                                             ↓
                                                           FAILED
Any state → CANCELLED (except terminal states)
```

> For `INTERNATIONAL` orders: use `PROCESSING → SHIPPED → IN_TRANSIT → DELIVERED`.
> For `LOCAL_EXPRESS` orders: use `COURIER_ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED`.

### Add tracking info (INTERNATIONAL only)

```
POST /api/admin/orders/{orderId}/tracking

{
  "status": "SHIPPED",
  "trackingCode": "DHL123456789",
  "carrierName": "DHL",
  "notes": "Dispatched from warehouse"
}
```

> `trackingCode` is required when setting status to `SHIPPED` for `INTERNATIONAL` orders.

---

## 5. Order Response Fields Reference

| Field | Description |
|---|---|
| `status` | Current order status |
| `deliveryMethod` | `LOCAL_EXPRESS` or `INTERNATIONAL` — set automatically at order creation |
| `courierUserId` | Assigned courier (LOCAL_EXPRESS only, set via admin) |
| `trackingCode` | Carrier tracking code (INTERNATIONAL only) |
| `carrierName` | Carrier name (INTERNATIONAL only) |
| `paidAt` | When payment was confirmed |
| `shippedAt` | When order was marked SHIPPED |
| `deliveredAt` | When order was marked DELIVERED |
| `trackingHistory` | Full audit trail of status changes with actor and timestamps |
