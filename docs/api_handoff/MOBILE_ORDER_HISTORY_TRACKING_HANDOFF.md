# Mobile Order History & Real-Time Tracking Integration Guide

This document provides the necessary API endpoints and integration steps for implementing the Order History and Live Delivery Tracking features in the mobile application.

---

## 1. Order History API

### 1.1 List My Orders
Fetches a paginated list of the customer's orders, sorted by newest first.

**Endpoint:** `GET /api/orders`
**Auth:** Bearer Token required.

**Query Parameters:**
| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `page` | integer | `0` | Page index (0-based) |
| `size` | integer | `20` | Items per page |

**Response (Summary):**
```json
{
  "content": [
    {
      "id": "uuid",
      "status": "IN_TRANSIT",
      "totalAmount": 150.00,
      "currency": "AED",
      "createdAt": "2026-04-19T10:00:00Z"
    }
  ],
  "totalPages": 5,
  "totalElements": 100
}
```

### 1.2 Get Order Details
Fetches full details of a specific order, including tracking history and items.

**Endpoint:** `GET /api/orders/{orderId}`
**Auth:** Bearer Token required.

**Key Response Fields for Tracking:**
- `status`: Current order status.
- `deliveryLatitude` / `deliveryLongitude`: Customer's destination coordinates.
- `courierName` / `courierPhone`: Assigned courier details (visible after `COURIER_ASSIGNED`).
- `deliveryOrderId`: Used for Chat and WebSocket topics.
- `trackingHistory`: Array of milestone events (see below).

---

## 2. Tracking History & Proof Images

Each order contains a `trackingHistory` array. Each event in this history provides status updates and potentially proof of delivery.

### 2.1 Tracking Event Object (`TrackingEventResponse`)
| Field | Description |
| :--- | :--- |
| `status` | The status at this milestone (e.g., `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`). |
| `notes` | Optional message from the courier or system. |
| `proofImageUrl` | **Absolute URL** to the proof image (e.g., photo of package at store, photo of package at door). |
| `latitude` / `longitude` | GPS coordinates where the status was updated. |
| `createdAt` | Timestamp of the event. |

**Example Timeline Implementation:**
- **Proof Images:** Check for `proofImageUrl` at each milestone. Couriers may upload images during `PICKED_UP` (to show package condition) and `DELIVERED` (as proof of drop-off).
- Show history as a vertical timeline from `PAID` to the current status.

---

## 3. Real-Time Map & Courier Tracking

For `LOCAL_EXPRESS` deliveries, the app should show a live map when the status is between `COURIER_ASSIGNED` and `DELIVERED`.

### 3.1 WebSocket Connection
Connect to the WebSocket server (URL provided in `WebSocketConfig`) and subscribe to the following topics.

#### A. Courier Live Location
**Topic:** `/topic/orders/{orderId}/location`
**Payload:**
```json
{
    "latitude": 25.2048,
    "longitude": 55.2708,
    "heading": 180.5,
    "speed": 35.2
}
```
**Action:** Update the Courier Marker position on the map in real-time. Use the `heading` to rotate the marker (e.g., a car or scooter icon).

#### B. Order Status Transitions
**Topic:** `/topic/orders/{orderId}/status`
**Payload:**
```json
{
    "orderId": "uuid",
    "status": "PICKED_UP",
    "proofImageUrl": null,
    "timestamp": "..."
}
```
**Action:** Transition the UI state and trigger a refresh of the order details if necessary.

### 3.2 Map UI Requirements
1.  **Destination Marker:** Static marker at `deliveryLatitude` / `deliveryLongitude` (from Order Details).
2.  **Courier Marker:** Dynamic marker updated via WebSocket.
3.  **Route Polyline:** (Optional) Draw a line between the courier and the destination.
4.  **Auto-Center:** Keep both markers in view while the courier is moving.

---

## 4. Delivery Lifecycle Checklist for Mobile

1.  **Status: PAID / PROCESSING**
    - Show static order details.
    - Status: "Preparing your order".

2.  **Status: COURIER_ASSIGNED**
    - Show "Call Courier" and "Chat" buttons (using `courierPhone` and `deliveryOrderId`).
    - Initialize Map View / Show Map Button.

3.  **Status: PICKED_UP / IN_TRANSIT**
    - Enable WebSocket location tracking.
    - Show courier moving on the map.
    - Status: "Courier is on the way".

4.  **Status: DELIVERED**
    - **Remove/Hide Chat, Call, and Map buttons.**
    - Disable WebSocket map tracking.
    - **Crucial:** Show the `proofImageUrl` from the last item in `trackingHistory`.
    - Status: "Delivered".

---

## 5. UI/UX Constraints

- **Bottom Navigation:** Ensure "Courier Tracking" is **not** present in the main bottom navigation bar. Tracking is accessible only via the Order Detail/History screen.
- **Post-Delivery:** Once an order is `DELIVERED`, all real-time communication (Chat/Call) and location tracking (Map) features must be disabled or removed for that order.

---

## 6. Summary of Topics for Order Tracking

| Feature | Topic / Endpoint | Use Case |
| :--- | :--- | :--- |
| **History List** | `GET /api/orders` | Profile > My Orders |
| **Live Location** | `/topic/orders/{orderId}/location` | Live Map Tracking |
| **Live Status** | `/topic/orders/{orderId}/status` | Instant UI Updates |
| **Proof Image** | `trackingHistory[].proofImageUrl` | Delivery Confirmation UI |
