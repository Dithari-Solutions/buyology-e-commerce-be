# Order Tracking (Live Map) - Frontend Integration Guide

This guide describes how to implement the "Track Order" button and live map view for customers to track their courier in real-time.

---

## 1. UI Components

### Track Order Button
- **Placement**: Order Details Page.
- **Visibility**: Only show this button if `order.deliveryMethod === 'EXPRESS'` AND `order.status` is either `PICKED_UP` or `IN_TRANSIT`.
- **Action**: Opens a full-screen or modal map view.

### Live Tracking Map
The map should display three key markers:
1.  **Store Location** (Blue Marker): Use `order.storeLatitude` and `order.storeLongitude`.
2.  **Customer Location** (Green Marker): Use `order.deliveryLatitude` and `order.deliveryLongitude`.
3.  **Courier Location** (Motorcycle/Courier Icon): Initial coordinates can be taken from the latest entry in `order.trackingHistory` (if present), but MUST be updated live via WebSocket.

---

## 2. WebSocket Implementation (Live GPS)

To see the courier move on the map, subscribe to the following WebSocket topic:

**Topic**: `/topic/orders/{orderId}/location`

### Connection Setup (using Stomp/SockJS)
```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, (frame) => {
    stompClient.subscribe(`/topic/orders/${orderId}/location`, (message) => {
        const courierLocation = JSON.parse(message.body);
        updateCourierMarker(courierLocation.latitude, courierLocation.longitude);
    });
});
```

### Location Payload Schema
```json
{
  "courierId": "uuid",
  "latitude": 25.2048,
  "longitude": 55.2708,
  "heading": 180,
  "speed": 45.5,
  "recordedAt": "2026-04-22T10:00:00Z"
}
```

---

## 3. Order Status Updates
Additionally, subscribe to status updates to automatically close the tracking view when the order is delivered.

**Topic**: `/topic/orders/{orderId}/status`

```javascript
stompClient.subscribe(`/topic/orders/${orderId}/status`, (message) => {
    const statusUpdate = JSON.parse(message.body);
    if (statusUpdate.status === 'DELIVERED') {
        alert("Your order has been delivered!");
        closeMap();
    }
});
```

---

## 4. Summary Checklist
- [ ] Verify `deliveryMethod` is `EXPRESS` before showing the button.
- [ ] Fetch initial markers from `GET /api/order/{orderId}`.
- [ ] Connect to WebSocket using the order ID.
- [ ] Use a map library (Google Maps, Leaflet, or Mapbox) to update the courier icon position dynamically without refreshing the page.
