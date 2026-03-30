# Order Module — Frontend Integration Guide

> **Base URL**: `/api/orders`
> **Auth**: JWT Bearer token required for all customer endpoints
> **Order Creation**: Automatic — triggered by successful payment webhook

---

## Table of Contents

1. [Overview](#1-overview)
2. [Order Lifecycle](#2-order-lifecycle)
3. [API Reference](#3-api-reference)
4. [Request & Response Shapes](#4-request--response-shapes)
5. [Customer Orders Page](#5-customer-orders-page)
6. [Filters & Pagination](#6-filters--pagination)
7. [Active vs Past Orders](#7-active-vs-past-orders)
8. [Order Detail View](#8-order-detail-view)
9. [Tracking History Display](#9-tracking-history-display)
10. [Delivery Method Handling](#10-delivery-method-handling)
11. [Status Badge Reference](#11-status-badge-reference)
12. [Implementation Examples](#12-implementation-examples)

---

## 1. Overview

Orders are **never created by the customer or frontend directly**. They are auto-created by the backend after a successful payment webhook fires. Once an order appears in the API, it is already in `PAID` status and the customer can track it.

The frontend is responsible for:
- Listing the customer's active and past orders with filters
- Displaying full order details (items, delivery address, tracking history)
- Showing real-time tracking status and milestones
- Allowing filtering by status, delivery method, and date range

---

## 2. Order Lifecycle

### Status State Machine

```
PENDING_PAYMENT
      |
      ▼ (payment webhook SUCCESS)
    PAID
      |
      ├──── (admin) ──────────────► PROCESSING
      |                                  |
      |                           ┌──────┴──────┐
      |                     LOCAL_EXPRESS    INTERNATIONAL
      |                           |                |
      |                   COURIER_ASSIGNED      SHIPPED
      |                           |                |
      |                       PICKED_UP       IN_TRANSIT
      |                           |                |
      |                       IN_TRANSIT      DELIVERED / FAILED
      |                           |
      |                    DELIVERED / FAILED
      |
      └──── (admin/customer) ──► CANCELLED  (from PENDING_PAYMENT or PAID only)
```

### Active vs Past Orders

| Category | Statuses |
|----------|----------|
| **Active** | `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `COURIER_ASSIGNED`, `PICKED_UP`, `SHIPPED`, `IN_TRANSIT` |
| **Past / Completed** | `DELIVERED`, `CANCELLED`, `FAILED` |

---

## 3. API Reference

### Customer Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/orders` | Paginated list of the customer's orders |
| `GET` | `/api/orders/{orderId}` | Full detail of a single order |

> Note: `POST /api/orders` exists for edge cases (manual order creation from a `CHECKED_OUT` cart) but is not needed in the standard auto-order flow.

### Query Parameters — `GET /api/orders`

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | integer | `0` | Page index (0-based) |
| `size` | integer | `20` | Items per page (max `50`) |

> Status and delivery method filtering for the customer list is handled **client-side** from the paginated response, or request the backend team to add these as query params if the dataset grows large.

---

## 4. Request & Response Shapes

### 4.1 `GET /api/orders` — Response (Paginated)

```json
{
  "content": [
    {
      "id": "uuid",
      "userId": "uuid",
      "deliveryMethod": "LOCAL_EXPRESS",
      "status": "IN_TRANSIT",
      "totalAmount": 265.00,
      "currency": "AED",
      "countryCode": "AE",
      "trackingCode": null,
      "carrierName": null,
      "recipientFirstName": "John",
      "recipientLastName": "Doe",
      "city": "Dubai",
      "country": "AE",
      "paidAt": "2026-03-30T12:10:00Z",
      "deliveredAt": null,
      "createdAt": "2026-03-30T12:05:00Z",
      "updatedAt": "2026-03-30T13:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 42,
  "totalPages": 3,
  "last": false,
  "first": true
}
```

### 4.2 `GET /api/orders/{orderId}` — Response (Full Detail)

```json
{
  "id": "uuid",
  "userId": "uuid",
  "authCredentialId": "uuid",
  "cartId": "uuid",
  "paymentTransactionId": "uuid",
  "deliveryMethod": "LOCAL_EXPRESS",
  "status": "IN_TRANSIT",
  "recipientFirstName": "John",
  "recipientLastName": "Doe",
  "recipientPhone": "+971501234567",
  "addressLine1": "123 Main St",
  "addressLine2": "Floor 3, Apt 301",
  "city": "Dubai",
  "state": "Dubai",
  "country": "AE",
  "postalCode": "00000",
  "deliveryLatitude": 25.2048,
  "deliveryLongitude": 55.2708,
  "subtotal": 250.00,
  "shippingFee": 15.00,
  "discount": 0.00,
  "totalAmount": 265.00,
  "currency": "AED",
  "countryCode": "AE",
  "couponCode": null,
  "trackingCode": null,
  "carrierName": null,
  "paidAt": "2026-03-30T12:10:00Z",
  "shippedAt": null,
  "deliveredAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-30T12:05:00Z",
  "updatedAt": "2026-03-30T13:00:00Z",
  "items": [
    {
      "id": "uuid",
      "productId": "uuid",
      "variantId": "uuid",
      "storeId": "uuid",
      "productSku": "PROD-001",
      "variantSku": "PROD-001-RED-M",
      "quantity": 2,
      "unitPrice": 100.00,
      "totalPrice": 200.00,
      "createdAt": "2026-03-30T12:05:00Z"
    }
  ],
  "trackingHistory": [
    {
      "id": "uuid",
      "status": "PAID",
      "notes": "Payment confirmed",
      "latitude": null,
      "longitude": null,
      "locationDescription": null,
      "actorId": "system_uuid",
      "actorRole": "SYSTEM",
      "createdAt": "2026-03-30T12:10:00Z"
    },
    {
      "id": "uuid",
      "status": "IN_TRANSIT",
      "notes": "Package picked up by courier",
      "latitude": 25.2048,
      "longitude": 55.2708,
      "locationDescription": null,
      "actorId": "courier_uuid",
      "actorRole": "COURIER",
      "createdAt": "2026-03-30T13:00:00Z"
    }
  ]
}
```

---

## 5. Customer Orders Page

### Recommended Page Structure

```
/orders
  ├── Filter bar (Status tabs + Delivery method dropdown + Date range)
  ├── Active Orders section
  │     └── OrderCard × N
  └── Past Orders section
        └── OrderCard × N
```

### OrderCard — Minimum Fields to Display

```
┌─────────────────────────────────────────────────┐
│ Order #uuid (short: last 8 chars)               │
│ Placed: Mar 30, 2026                            │
│ Status: [IN_TRANSIT badge]                      │
│ Items: 2 × PROD-001-RED-M                       │
│ Total: AED 265.00                               │
│ Delivery: Local Express → Dubai                 │
│                           [View Details →]      │
└─────────────────────────────────────────────────┘
```

---

## 6. Filters & Pagination

### Filter State Shape (React/Vue example)

```ts
interface OrderFilters {
  statusGroup: 'all' | 'active' | 'past';    // tabs
  status: OrderStatus | null;                 // specific status dropdown
  deliveryMethod: 'LOCAL_EXPRESS' | 'INTERNATIONAL' | null;
  dateFrom: string | null;                    // ISO date string
  dateTo: string | null;
  page: number;
  size: number;
}
```

### Active Orders Filter (client-side)

```ts
const ACTIVE_STATUSES = [
  'PENDING_PAYMENT',
  'PAID',
  'PROCESSING',
  'COURIER_ASSIGNED',
  'PICKED_UP',
  'SHIPPED',
  'IN_TRANSIT'
];

const PAST_STATUSES = ['DELIVERED', 'CANCELLED', 'FAILED'];

function filterOrders(orders, filters) {
  return orders.filter(order => {
    if (filters.statusGroup === 'active') {
      return ACTIVE_STATUSES.includes(order.status);
    }
    if (filters.statusGroup === 'past') {
      return PAST_STATUSES.includes(order.status);
    }
    if (filters.status) {
      return order.status === filters.status;
    }
    return true;
  }).filter(order => {
    if (filters.deliveryMethod) {
      return order.deliveryMethod === filters.deliveryMethod;
    }
    return true;
  }).filter(order => {
    if (filters.dateFrom) {
      return new Date(order.createdAt) >= new Date(filters.dateFrom);
    }
    return true;
  }).filter(order => {
    if (filters.dateTo) {
      return new Date(order.createdAt) <= new Date(filters.dateTo);
    }
    return true;
  });
}
```

### Fetching Orders with Pagination

```ts
async function fetchOrders(page = 0, size = 20, token: string) {
  const res = await fetch(`/api/orders?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  return res.json(); // Spring Page<OrderSummaryResponse>
}
```

### Recommended UI Filter Controls

```
[All Orders] [Active] [Past/Completed]    ← Tab group

Status: [Any ▼]   Delivery: [Any ▼]   Date: [From] → [To]   [Clear Filters]
```

| Control | Options |
|---------|---------|
| Status tab | All, Active, Past |
| Status dropdown | All, Pending Payment, Paid, Processing, Courier Assigned, Picked Up, Shipped, In Transit, Delivered, Cancelled, Failed |
| Delivery dropdown | All, Local Express, International |
| Date range | Date picker (createdAt range) |

---

## 7. Active vs Past Orders

### Grouping Logic

```ts
function groupOrders(orders: OrderSummaryResponse[]) {
  const active = orders.filter(o => ACTIVE_STATUSES.includes(o.status));
  const past = orders.filter(o => PAST_STATUSES.includes(o.status));
  return { active, past };
}
```

### Displaying Grouped Orders

```tsx
// React example
const { active, past } = groupOrders(filteredOrders);

return (
  <>
    {active.length > 0 && (
      <section>
        <h2>Active Orders ({active.length})</h2>
        {active.map(order => <OrderCard key={order.id} order={order} />)}
      </section>
    )}
    {past.length > 0 && (
      <section>
        <h2>Past Orders</h2>
        {past.map(order => <OrderCard key={order.id} order={order} />)}
      </section>
    )}
    {active.length === 0 && past.length === 0 && (
      <EmptyState message="No orders found" />
    )}
  </>
);
```

---

## 8. Order Detail View

### Route

```
/orders/:orderId
```

### Fetching Order Detail

```ts
async function fetchOrderDetail(orderId: string, token: string) {
  const res = await fetch(`/api/orders/${orderId}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (res.status === 404) throw new Error('Order not found');
  if (!res.ok) throw new Error('Failed to load order');
  return res.json();
}
```

### Sections to Render

1. **Order Header**
   - Order ID (short display: `#${id.slice(-8).toUpperCase()}`)
   - Placed date
   - Status badge
   - Total amount + currency

2. **Delivery Address**
   - Recipient name & phone
   - Full address (line1, line2, city, state, country, postal code)

3. **Order Items Table**

   | Product SKU | Variant SKU | Qty | Unit Price | Total |
   |-------------|-------------|-----|------------|-------|
   | PROD-001 | PROD-001-RED-M | 2 | AED 100 | AED 200 |

4. **Pricing Summary**
   ```
   Subtotal:       AED 250.00
   Shipping:       AED  15.00
   Discount:       AED   0.00
   ─────────────────────────
   Total:          AED 265.00
   ```

5. **Delivery Info**
   - Method: Local Express / International
   - For INTERNATIONAL: Show `carrierName` and `trackingCode` once set by admin
   - Milestone timestamps: Paid at, Shipped at, Delivered at

6. **Tracking History** (see Section 9)

---

## 9. Tracking History Display

The `trackingHistory` array is append-only and sorted oldest-first. Display as a vertical timeline.

### Timeline Item Structure

```
● [Status Label]          [Date / Time]
  [Notes]
  [Location description if present]
  [Actor role: System / Admin / Courier]
```

### Example Timeline

```
✓ PAID                    Mar 30, 2026 12:10
  Payment confirmed
  — System

✓ PROCESSING              Mar 30, 2026 14:00
  Order confirmed, preparing for courier
  — Admin

✓ COURIER_ASSIGNED        Mar 30, 2026 15:30
  Courier assigned to delivery
  — Admin

● IN_TRANSIT              Mar 30, 2026 16:00   ← Current
  Package picked up
  — Courier
```

### Rendering Logic

```ts
const statusLabels: Record<string, string> = {
  PENDING_PAYMENT: 'Pending Payment',
  PAID: 'Payment Confirmed',
  PROCESSING: 'Processing',
  COURIER_ASSIGNED: 'Courier Assigned',
  PICKED_UP: 'Picked Up',
  SHIPPED: 'Shipped',
  IN_TRANSIT: 'In Transit',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed'
};

// Sort tracking history (should already be ascending)
const sortedHistory = [...order.trackingHistory].sort(
  (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
);

// Last item = current status
const currentStep = sortedHistory[sortedHistory.length - 1];
```

### GPS Tracking (LOCAL_EXPRESS)

If the tracking event has `latitude` and `longitude`, you can show a map pin for the courier's last location:

```ts
if (event.actorRole === 'COURIER' && event.latitude && event.longitude) {
  // Show map with pin at { lat: event.latitude, lng: event.longitude }
}
```

---

## 10. Delivery Method Handling

### LOCAL_EXPRESS

- Courier is assigned by admin
- Real-time GPS coordinates available on tracking events
- No external tracking code
- Statuses: `COURIER_ASSIGNED` → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED`

### INTERNATIONAL

- Ships via external carrier
- Admin sets `trackingCode` and `carrierName` on the order
- Display a "Track on carrier site" link if you know the carrier's tracking URL
- Statuses: `SHIPPED` → `IN_TRANSIT` → `DELIVERED`

```tsx
{order.deliveryMethod === 'INTERNATIONAL' && order.trackingCode && (
  <div>
    <span>Carrier: {order.carrierName}</span>
    <span>Tracking: {order.trackingCode}</span>
    {/* Optionally link to carrier tracking page */}
  </div>
)}
```

---

## 11. Status Badge Reference

| Status | Color | Label |
|--------|-------|-------|
| `PENDING_PAYMENT` | Yellow | Pending Payment |
| `PAID` | Blue | Paid |
| `PROCESSING` | Blue | Processing |
| `COURIER_ASSIGNED` | Purple | Courier Assigned |
| `PICKED_UP` | Purple | Picked Up |
| `SHIPPED` | Indigo | Shipped |
| `IN_TRANSIT` | Orange | In Transit |
| `DELIVERED` | Green | Delivered |
| `CANCELLED` | Gray | Cancelled |
| `FAILED` | Red | Failed |

### CSS Classes (Tailwind example)

```ts
const statusColors: Record<string, string> = {
  PENDING_PAYMENT: 'bg-yellow-100 text-yellow-800',
  PAID: 'bg-blue-100 text-blue-800',
  PROCESSING: 'bg-blue-100 text-blue-800',
  COURIER_ASSIGNED: 'bg-purple-100 text-purple-800',
  PICKED_UP: 'bg-purple-100 text-purple-800',
  SHIPPED: 'bg-indigo-100 text-indigo-800',
  IN_TRANSIT: 'bg-orange-100 text-orange-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-gray-100 text-gray-700',
  FAILED: 'bg-red-100 text-red-800',
};
```

---

## 12. Implementation Examples

### Full Orders Page (React + fetch)

```tsx
import { useState, useEffect } from 'react';

const ACTIVE_STATUSES = ['PENDING_PAYMENT','PAID','PROCESSING','COURIER_ASSIGNED','PICKED_UP','SHIPPED','IN_TRANSIT'];
const PAST_STATUSES = ['DELIVERED','CANCELLED','FAILED'];

export function OrdersPage() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Filter state
  const [statusGroup, setStatusGroup] = useState('all');
  const [deliveryMethod, setDeliveryMethod] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const token = localStorage.getItem('token');

  useEffect(() => {
    setLoading(true);
    fetch(`/api/orders?page=${page}&size=20`, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(r => r.json())
      .then(data => {
        setOrders(data.content);
        setTotalPages(data.totalPages);
      })
      .finally(() => setLoading(false));
  }, [page]);

  const filtered = orders.filter(o => {
    if (statusGroup === 'active' && !ACTIVE_STATUSES.includes(o.status)) return false;
    if (statusGroup === 'past' && !PAST_STATUSES.includes(o.status)) return false;
    if (deliveryMethod && o.deliveryMethod !== deliveryMethod) return false;
    if (dateFrom && new Date(o.createdAt) < new Date(dateFrom)) return false;
    if (dateTo && new Date(o.createdAt) > new Date(dateTo)) return false;
    return true;
  });

  const active = filtered.filter(o => ACTIVE_STATUSES.includes(o.status));
  const past = filtered.filter(o => PAST_STATUSES.includes(o.status));

  return (
    <div>
      {/* Tab filters */}
      <div>
        {['all', 'active', 'past'].map(tab => (
          <button
            key={tab}
            onClick={() => setStatusGroup(tab)}
            className={statusGroup === tab ? 'active' : ''}
          >
            {tab === 'all' ? 'All Orders' : tab === 'active' ? 'Active' : 'Past Orders'}
          </button>
        ))}
      </div>

      {/* Secondary filters */}
      <div>
        <select value={deliveryMethod} onChange={e => setDeliveryMethod(e.target.value)}>
          <option value="">All Delivery Methods</option>
          <option value="LOCAL_EXPRESS">Local Express</option>
          <option value="INTERNATIONAL">International</option>
        </select>
        <input type="date" value={dateFrom} onChange={e => setDateFrom(e.target.value)} placeholder="From" />
        <input type="date" value={dateTo} onChange={e => setDateTo(e.target.value)} placeholder="To" />
        <button onClick={() => { setDeliveryMethod(''); setDateFrom(''); setDateTo(''); }}>
          Clear
        </button>
      </div>

      {loading ? (
        <p>Loading orders...</p>
      ) : (
        <>
          {active.length > 0 && (
            <section>
              <h2>Active Orders ({active.length})</h2>
              {active.map(order => <OrderCard key={order.id} order={order} />)}
            </section>
          )}
          {past.length > 0 && (
            <section>
              <h2>Past Orders</h2>
              {past.map(order => <OrderCard key={order.id} order={order} />)}
            </section>
          )}
          {filtered.length === 0 && <p>No orders match your filters.</p>}

          {/* Pagination */}
          <div>
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>
              Previous
            </button>
            <span>Page {page + 1} of {totalPages}</span>
            <button onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}
```

### Order Detail Page

```tsx
export function OrderDetailPage({ orderId }: { orderId: string }) {
  const [order, setOrder] = useState(null);
  const token = localStorage.getItem('token');

  useEffect(() => {
    fetch(`/api/orders/${orderId}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(r => {
        if (!r.ok) throw new Error('Not found');
        return r.json();
      })
      .then(setOrder)
      .catch(() => {/* handle error */});
  }, [orderId]);

  if (!order) return <p>Loading...</p>;

  const sortedHistory = [...order.trackingHistory].sort(
    (a, b) => new Date(a.createdAt) - new Date(b.createdAt)
  );

  return (
    <div>
      <h1>Order #{order.id.slice(-8).toUpperCase()}</h1>
      <StatusBadge status={order.status} />

      <section>
        <h2>Delivery Address</h2>
        <p>{order.recipientFirstName} {order.recipientLastName} · {order.recipientPhone}</p>
        <p>{order.addressLine1}, {order.addressLine2}</p>
        <p>{order.city}, {order.state}, {order.country} {order.postalCode}</p>
      </section>

      <section>
        <h2>Items</h2>
        <table>
          <thead>
            <tr><th>SKU</th><th>Qty</th><th>Unit Price</th><th>Total</th></tr>
          </thead>
          <tbody>
            {order.items.map(item => (
              <tr key={item.id}>
                <td>{item.variantSku || item.productSku}</td>
                <td>{item.quantity}</td>
                <td>{order.currency} {item.unitPrice}</td>
                <td>{order.currency} {item.totalPrice}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>Subtotal: {order.currency} {order.subtotal}</p>
        <p>Shipping: {order.currency} {order.shippingFee}</p>
        <p><strong>Total: {order.currency} {order.totalAmount}</strong></p>
      </section>

      {order.deliveryMethod === 'INTERNATIONAL' && order.trackingCode && (
        <section>
          <h2>Carrier Tracking</h2>
          <p>{order.carrierName} — {order.trackingCode}</p>
        </section>
      )}

      <section>
        <h2>Tracking History</h2>
        <ol>
          {sortedHistory.map((event, i) => (
            <li key={event.id} className={i === sortedHistory.length - 1 ? 'current' : ''}>
              <strong>{statusLabels[event.status]}</strong>
              <time>{new Date(event.createdAt).toLocaleString()}</time>
              {event.notes && <p>{event.notes}</p>}
              {event.locationDescription && <p>{event.locationDescription}</p>}
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
```

---

*Last updated: 2026-03-30*
