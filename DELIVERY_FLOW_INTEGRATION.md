# Delivery Flow Integration Guide

**For:** Web Frontend & Mobile App developers  
**Covers:** Courier assignment, real-time location tracking, delivery proof, post-delivery rating  
**Backend version:** Implemented April 2026

---

## Table of Contents

1. [Overview — What Changed](#1-overview--what-changed)
2. [Order Status Reference](#2-order-status-reference)
3. [Flow 1 — "Looking for Courier" & Assignment Notification](#3-flow-1--looking-for-courier--assignment-notification)
4. [Flow 2 — Real-Time Courier Location Tracking](#4-flow-2--real-time-courier-location-tracking)
5. [Flow 3 — Delivery Progress Updates](#5-flow-3--delivery-progress-updates)
6. [Flow 4 — Post-Delivery Rating](#6-flow-4--post-delivery-rating)
7. [FCM Push Notifications](#7-fcm-push-notifications)
8. [New API Endpoints Reference](#8-new-api-endpoints-reference)
9. [WebSocket Reference](#9-websocket-reference)
10. [Email Notifications (No Action Needed)](#10-email-notifications-no-action-needed)

---

## 1. Overview — What Changed

When a customer pays for an **EXPRESS** order, the order goes through a full courier delivery lifecycle. Previously the frontend had no visibility into what happened after payment. Here is what is now fully implemented:

| # | Event | How customer is notified |
|---|---|---|
| 1 | Courier assigned | Email (automatic) + Push notification + Order status updates |
| 2 | Courier accepted order | Push notification |
| 3 | Package picked up | Push notification + Order status updates |
| 4 | Courier on the way | Push notification + Real-time GPS on map |
| 5 | Package delivered | Email (automatic) + Push notification |
| 6 | Rating prompt | Email (automatic) + in-app prompt on DELIVERED status |

**For REGULAR orders** (2-3 day shipping), the admin manually updates tracking. No courier location tracking applies.

---

## 2. Order Status Reference

The `status` field in the `GET /api/orders/{id}` response tells you exactly what to show the customer.

| `status` | What to display | Show courier map? |
|---|---|---|
| `PENDING_PAYMENT` | Awaiting payment | No |
| `PAID` | Looking for courier... | No (show spinner/searching animation) |
| `COURIER_ASSIGNED` | Courier found! Heading to pick up | Yes (show courier on map) |
| `PICKED_UP` | Package collected, on the way | Yes |
| `IN_TRANSIT` | Almost there! | Yes |
| `DELIVERED` | Delivered ✓ — prompt to rate | No |
| `FAILED` | Delivery failed — contact support | No |
| `CANCELLED` | Order cancelled | No |
| `PROCESSING` | Being prepared (REGULAR orders only) | No |
| `SHIPPED` | Shipped via carrier (REGULAR orders only) | No |

### New fields on `OrderResponse`

When an order is `COURIER_ASSIGNED` or later, these fields are populated:

```json
{
  "id": "...",
  "status": "COURIER_ASSIGNED",
  "courierUserId": "uuid-of-courier",
  "courierName": "John Smith",
  "courierPhone": "+971501234567",
  "deliveryOrderId": "uuid-on-courier-backend",
  ...
}
```

| Field | Type | Use |
|---|---|---|
| `courierName` | `string` | Display courier name to customer |
| `courierPhone` | `string` | Show "Call courier" button |
| `deliveryOrderId` | `UUID` | Use to build WebSocket location topic (see §4) |

---

## 3. Flow 1 — "Looking for Courier" & Assignment Notification

### What the UI should do

After the customer pays for an EXPRESS order, the `status` becomes `PAID`. Show a **"Looking for courier…"** loading state immediately.

```
PAID → (backend finds courier) → COURIER_ASSIGNED
```

This transition happens automatically — the backend sends the order to the courier service and assigns the nearest available courier. The customer does **not** need to do anything.

### Detecting the transition (3 options — pick one per platform)

**Option A — Poll the order status (simplest)**

Poll `GET /api/orders/{orderId}` every 5 seconds while `status === 'PAID'`.

```javascript
// Example: poll until courier is assigned or timeout
const pollForCourier = async (orderId) => {
  const MAX_WAIT_MS = 5 * 60 * 1000; // 5 minutes
  const start = Date.now();

  while (Date.now() - start < MAX_WAIT_MS) {
    const order = await api.getOrder(orderId);
    if (order.status !== 'PAID') return order; // assigned or failed
    await sleep(5000);
  }
  throw new Error('Timed out waiting for courier');
};
```

**Option B — WebSocket order status topic (recommended for mobile)**

Subscribe to `/topic/orders/{orderId}/status` after payment. The backend will push a message whenever the status changes.

See §9 for WebSocket connection instructions.

**Option C — FCM push notification (mobile only)**

The backend sends a push with `type: "COURIER_ASSIGNED"` when a courier is found. Handle this in your notification handler — navigate to the order tracking screen.

```json
{
  "notification": {
    "title": "Courier assigned!",
    "body": "Your courier has been assigned and is heading to pick up your order."
  },
  "data": {
    "type": "COURIER_ASSIGNED",
    "orderId": "<ecommerce-order-uuid>",
    "deliveryOrderId": "<courier-backend-delivery-uuid>"
  }
}
```

### "Looking for courier" UI state

Show this screen while `status === 'PAID'` on an EXPRESS order:

```
┌─────────────────────────────┐
│   🔍  Finding your courier  │
│                             │
│   We're matching you with   │
│   the nearest available     │
│   courier…                  │
│                             │
│   ● ● ●  (animated dots)   │
└─────────────────────────────┘
```

When `status` becomes `COURIER_ASSIGNED`, show:

```
┌─────────────────────────────┐
│   ✅  Courier found!         │
│                             │
│   John Smith                │
│   ☎ +971 50 123 4567        │
│   ETA: ~18 minutes          │
│                             │
│   [Track on map ▶]          │
└─────────────────────────────┘
```

---

## 4. Flow 2 — Real-Time Courier Location Tracking

### How it works

Once `status === 'COURIER_ASSIGNED'`, the courier app sends GPS pings to the backend every few seconds. These pings are pushed to the customer via WebSocket.

**WebSocket topic to subscribe to:**

```
/topic/orders/{ecommerceOrderId}/location
```

Use `order.id` (the ecommerce order UUID) as `ecommerceOrderId`.

### Connecting (Mobile — React Native example)

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const connectToTracking = (orderId, jwtToken, onLocation) => {
  const client = new Client({
    webSocketFactory: () => new SockJS('https://api.buyology.com/ws'),
    connectHeaders: {
      Authorization: `Bearer ${jwtToken}`,
    },
    onConnect: () => {
      client.subscribe(`/topic/orders/${orderId}/location`, (message) => {
        const location = JSON.parse(message.body);
        onLocation(location);
      });
    },
    onStompError: (frame) => {
      console.error('STOMP error', frame);
    },
  });

  client.activate();
  return client; // call client.deactivate() on unmount
};
```

### Location payload

Each message on the topic has this shape:

```typescript
interface LocationUpdate {
  courierId: string;       // UUID
  latitude: number;        // e.g. 25.2048
  longitude: number;       // e.g. 55.2708
  heading: number | null;  // degrees 0-360, null if unavailable
  speed: number | null;    // km/h, null if unavailable
  recordedAt: string;      // ISO-8601 timestamp
}
```

### Map implementation guide

1. When `status` transitions to `COURIER_ASSIGNED` (or on app open if already assigned):
   - Show a map with the customer's delivery address pin
   - Place the courier marker at the first received location
2. On each `LocationUpdate`:
   - Animate the courier marker to the new coordinates
   - Optionally draw a route line from courier → delivery address (use Google Maps Directions API or similar)
   - Show ETA in the top bar
3. Stop showing the map when `status === 'DELIVERED'` or `status === 'FAILED'`

### When to show/hide the map

```javascript
const shouldShowCourierMap = (status) =>
  ['COURIER_ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'].includes(status);
```

---

## 5. Flow 3 — Delivery Progress Updates

The order status progresses in this sequence for EXPRESS orders:

```
PAID
  └─► COURIER_ASSIGNED   ← courier found, heading to store
        └─► PICKED_UP    ← courier took photo at pickup, package collected
              └─► IN_TRANSIT ← courier driving to customer
                    └─► DELIVERED  ← photo proof submitted at dropoff
```

Each transition triggers:
- A WebSocket message on `/topic/orders/{orderId}/status` (if subscribed)
- A FCM push notification to the customer's device
- An email for DELIVERED

### Status push notification types

| `data.type` in push | Status that triggered it | Suggested UX |
|---|---|---|
| `COURIER_ASSIGNED` | `COURIER_ASSIGNED` | Navigate to tracking screen |
| `ORDER_STATUS_PICKED_UP` | `PICKED_UP` | Show "Package collected" banner |
| `ORDER_STATUS_ON_THE_WAY` | `IN_TRANSIT` | Show "Almost there!" banner |
| `ORDER_STATUS_DELIVERED` | `DELIVERED` | Show rating prompt modal |
| `ORDER_STATUS_FAILED` | `FAILED` | Show error with support CTA |
| `ORDER_STATUS_CANCELLED` | `CANCELLED` | Show cancellation message |
| `ASSIGNMENT_EXHAUSTED` | `FAILED` | Show "No courier available" with support CTA |

### Handling push in background (Mobile)

```javascript
// React Native — handle FCM message when app is in background/killed
messaging().setBackgroundMessageHandler(async (remoteMessage) => {
  const { type, orderId } = remoteMessage.data;

  switch (type) {
    case 'COURIER_ASSIGNED':
      // Deep link to: /orders/{orderId}/tracking
      break;
    case 'ORDER_STATUS_DELIVERED':
      // Deep link to: /orders/{orderId}/rate
      break;
    case 'ORDER_STATUS_FAILED':
    case 'ASSIGNMENT_EXHAUSTED':
      // Deep link to: /orders/{orderId} with support banner
      break;
  }
});
```

---

## 6. Flow 4 — Post-Delivery Rating

After `status === 'DELIVERED'`, show a rating modal. The customer can rate:
1. **The courier** — 5 stars (required)
2. **Each product in the order** — 5 stars + comment (optional)

### Triggering the rating prompt

The rating prompt should appear when:
- The customer opens an order with `status === 'DELIVERED'` and `hasRated === false`
- A push notification with `type: "ORDER_STATUS_DELIVERED"` is received
- The customer receives the "How was your delivery?" email and taps the deeplink

Check whether the customer has already rated by calling `GET /api/orders/{orderId}` — if the backend returns `409 Conflict` on submit, they already rated (handle gracefully).

### API call

**`POST /api/v1/orders/{orderId}/rate`**

Header: `Authorization: Bearer <jwt>`  
Body:

```json
{
  "courierStars": 5,
  "courierComment": "Super fast and friendly!",
  "productRatings": [
    {
      "productId": "uuid-of-product-1",
      "stars": 4,
      "body": "Great quality, exactly as described."
    },
    {
      "productId": "uuid-of-product-2",
      "stars": 5,
      "body": null
    }
  ]
}
```

**Response — success `200`:**

```json
{
  "success": true,
  "message": "Rating submitted successfully",
  "data": null
}
```

**Response — already rated `409`:**

```json
{
  "success": false,
  "message": "You have already submitted a rating for this order."
}
```

**Response — order not delivered `400`:**

```json
{
  "success": false,
  "message": "Ratings can only be submitted after the order has been delivered."
}
```

### Rating UI

```
┌──────────────────────────────────────┐
│  Rate your delivery                   │
│                                      │
│  Courier: John Smith                 │
│  ★ ★ ★ ★ ☆   (tap stars)            │
│  ┌──────────────────────────────┐   │
│  │ Leave a comment (optional)   │   │
│  └──────────────────────────────┘   │
│                                      │
│  ── Your products ──────────────     │
│                                      │
│  [Product image] Nike Air Max        │
│  ★ ★ ★ ★ ★                          │
│                                      │
│  [Product image] Adidas Hoodie       │
│  ★ ★ ★ ☆ ☆                          │
│                                      │
│  [ Submit Rating ]                   │
│  [ Skip for now  ]                   │
└──────────────────────────────────────┘
```

**Rules:**
- `courierStars` is required (1–5). Disable submit until set.
- Product ratings are optional — customer can skip individual products.
- One submission per order — hide the rating CTA after successful submit.
- Submit produces immediate feedback ("Thanks for your rating!"), then navigate to order detail.

---

## 7. FCM Push Notifications

### Setup — Register device token

Call this endpoint **after every login** and when the FCM token refreshes:

**`POST /api/v1/notifications/register-token`**

Header: `Authorization: Bearer <jwt>`  
Body:

```json
{
  "fcmToken": "fcm-device-token-from-firebase",
  "deviceType": "IOS"
}
```

`deviceType` is `"IOS"` or `"ANDROID"`.

**`DELETE /api/v1/notifications/register-token?deviceType=IOS`**

Call this on **logout** to stop receiving push notifications on the device.

### React Native / Expo — Complete setup

```javascript
import messaging from '@react-native-firebase/messaging';

// 1. Request permission (iOS only)
const requestPermission = async () => {
  const authStatus = await messaging().requestPermission();
  return (
    authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
    authStatus === messaging.AuthorizationStatus.PROVISIONAL
  );
};

// 2. Get token and register with backend
const registerPushToken = async (jwtToken) => {
  const granted = await requestPermission();
  if (!granted) return;

  const fcmToken = await messaging().getToken();
  await api.post('/api/v1/notifications/register-token', {
    fcmToken,
    deviceType: Platform.OS === 'ios' ? 'IOS' : 'ANDROID',
  }, { headers: { Authorization: `Bearer ${jwtToken}` } });
};

// 3. Refresh token listener (call once in App.js)
messaging().onTokenRefresh(async (newToken) => {
  const jwtToken = await getStoredJwt();
  await registerPushToken(jwtToken);
});

// 4. Handle foreground messages
messaging().onMessage(async (remoteMessage) => {
  const { type, orderId } = remoteMessage.data ?? {};

  switch (type) {
    case 'COURIER_ASSIGNED':
      showToast(`Courier assigned to order!`);
      refreshOrder(orderId); // re-fetch order to get courierName, deliveryOrderId
      break;
    case 'ORDER_STATUS_DELIVERED':
      showRatingPrompt(orderId);
      break;
    case 'ORDER_STATUS_FAILED':
    case 'ASSIGNMENT_EXHAUSTED':
      showAlert('Delivery issue', 'Please contact support.');
      break;
  }
});
```

---

## 8. New API Endpoints Reference

### Register FCM push token

```
POST /api/v1/notifications/register-token
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "fcmToken": "string",      // required
  "deviceType": "IOS" | "ANDROID"  // required
}
```

Response `200 OK`:
```json
{ "success": true, "message": "Push token registered", "data": null }
```

---

### Remove FCM push token (logout)

```
DELETE /api/v1/notifications/register-token?deviceType=IOS
Authorization: Bearer <jwt>
```

Response `200 OK`:
```json
{ "success": true, "message": "Push token removed", "data": null }
```

---

### Get order (updated response)

```
GET /api/orders/{orderId}
Authorization: Bearer <jwt>
```

New fields in response (populated after courier assignment):

```json
{
  "id": "uuid",
  "status": "COURIER_ASSIGNED",
  "courierUserId": "uuid",
  "courierName": "John Smith",
  "courierPhone": "+971501234567",
  "deliveryOrderId": "uuid",
  ...
}
```

---

### Submit post-delivery rating

```
POST /api/v1/orders/{orderId}/rate
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "courierStars": 5,              // required, 1-5
  "courierComment": "string",     // optional, max 1000 chars
  "productRatings": [             // optional list
    {
      "productId": "uuid",        // required per entry
      "stars": 4,                 // required, 1-5
      "body": "string"            // optional, max 2000 chars
    }
  ]
}
```

| Status | Meaning |
|---|---|
| `200 OK` | Rating saved |
| `400 Bad Request` | Order not delivered yet, or validation error |
| `404 Not Found` | Order not found or doesn't belong to user |
| `409 Conflict` | Already rated this order |

---

## 9. WebSocket Reference

### Connection

```
wss://api.buyology.com/ws
```

Uses SockJS fallback. Pass the JWT in the STOMP `CONNECT` frame:

```
CONNECT
Authorization:Bearer <jwt>
accept-version:1.2
heart-beat:10000,10000
```

### Topics

#### Courier GPS location

```
SUBSCRIBE /topic/orders/{orderId}/location
```

Payload:
```typescript
{
  courierId: string;    // UUID
  latitude: number;
  longitude: number;
  heading: number | null;  // degrees 0-360
  speed: number | null;    // km/h
  recordedAt: string;      // ISO-8601
}
```

**When to subscribe:** When `order.status` becomes `COURIER_ASSIGNED`.  
**When to unsubscribe:** When `order.status` becomes `DELIVERED`, `FAILED`, or `CANCELLED`.

#### Order status updates

```
SUBSCRIBE /topic/orders/{orderId}/status
```

Payload:
```typescript
{
  orderId: string;     // UUID
  status: string;      // new OrderStatus value
  timestamp: string;   // ISO-8601
}
```

**When to subscribe:** Right after payment confirmation, while waiting for courier.

### React Native WebSocket lifecycle

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class DeliveryTracker {
  constructor(orderId, token) {
    this.orderId = orderId;
    this.client = new Client({
      webSocketFactory: () => new SockJS('https://api.buyology.com/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
    });
  }

  start(onLocation, onStatus) {
    this.client.onConnect = () => {
      // Subscribe to location updates
      this.locSub = this.client.subscribe(
        `/topic/orders/${this.orderId}/location`,
        (msg) => onLocation(JSON.parse(msg.body))
      );
      // Subscribe to status updates
      this.statusSub = this.client.subscribe(
        `/topic/orders/${this.orderId}/status`,
        (msg) => onStatus(JSON.parse(msg.body))
      );
    };
    this.client.activate();
  }

  stop() {
    this.locSub?.unsubscribe();
    this.statusSub?.unsubscribe();
    this.client.deactivate();
  }
}

// Usage in a React component
useEffect(() => {
  if (!['COURIER_ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'].includes(order.status)) return;

  const tracker = new DeliveryTracker(order.id, jwt);
  tracker.start(
    (loc) => setCourierLocation(loc),
    (update) => setOrderStatus(update.status)
  );
  return () => tracker.stop();
}, [order.id, order.status]);
```

---

## 10. Email Notifications (No Action Needed)

These emails are sent automatically by the backend. You do **not** need to trigger them — just ensure the customer's email is saved on their account.

| Trigger | Email subject |
|---|---|
| Courier assigned to order | "Your courier has been assigned — Buyology" |
| Order delivered | "Your order has been delivered — Buyology" |
| Delivery failed | "We couldn't deliver your order — Buyology" |
| Post-delivery rating request | "How was your delivery? Rate your experience — Buyology" |

The rating-request email is sent ~immediately after delivery is confirmed. It contains a message pointing the customer to open the app and go to **My Orders** to rate.

---

## Quick Checklist

### Web Frontend

- [ ] Show "Looking for courier…" spinner while `status === 'PAID'` on EXPRESS orders
- [ ] Poll `GET /api/orders/{orderId}` every 5s while waiting for courier
- [ ] Display `courierName` + `courierPhone` (click-to-call) when status ≥ `COURIER_ASSIGNED`
- [ ] Show delivery progress steps matching the status flow
- [ ] Show rating modal after `status === 'DELIVERED'`
- [ ] Call `POST /api/v1/orders/{orderId}/rate` on submit
- [ ] Handle `409` gracefully (already rated)

### Mobile App (React Native / Expo)

- [ ] Call `POST /api/v1/notifications/register-token` after login with FCM token
- [ ] Call `DELETE /api/v1/notifications/register-token` after logout
- [ ] Refresh FCM token with `messaging().onTokenRefresh()`
- [ ] Handle foreground push: `COURIER_ASSIGNED`, `ORDER_STATUS_*`
- [ ] Handle background push: deep link to tracking / rating screen
- [ ] Connect to WebSocket when tracking screen opens
- [ ] Animate courier marker on map from `LocationUpdate` events
- [ ] Unsubscribe from WebSocket on delivery completion
- [ ] Show post-delivery rating modal (triggered by push or on order open)
- [ ] Submit combined courier + product rating
