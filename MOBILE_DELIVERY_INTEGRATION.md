# Mobile App: Real-Time Delivery & Tracking Integration

This guide covers status updates, GPS tracking, and push notifications for the Mobile (iOS/Android) apps.

## 1. WebSocket Status & Location

Mobile apps should subscribe to both the **Status** topic (for UI transitions) and the **Location** topic (for moving the courier marker on the map).

### Topics
| Feature | Topic |
| :--- | :--- |
| **Status Updates** | `/topic/orders/{orderId}/status` |
| **GPS Tracking** | `/topic/orders/{orderId}/location` |

### Status Payload (New Field)
The status update now includes the `proofImageUrl` taken by the courier.
```json
{
    "orderId": "uuid",
    "status": "DELIVERED",
    "proofImageUrl": "https://storage.buyology.com/proofs/delivery_123.jpg",
    "timestamp": "..."
}
```

---

## 2. Handling Courier Assignment

When a courier is assigned, the Order object is updated with the courier's contact details. This triggers the visibility of Chat/Call buttons.

1.  Watch for status `COURIER_ASSIGNED` via WebSocket.
2.  Immediately re-fetch `GET /api/orders/{id}` to get:
    -   `courierName`
    -   `courierPhone` (for the "Call" button)
    -   `deliveryOrderId` (for the "Chat" room)

---

## 3. FCM Push Notifications

The backend sends data-only FCM messages for silent processing and notification-category pings.

### Order Status Types
When you receive an FCM message with `type: "ORDER_STATUS_..."`, you should refresh the active order screen.

### Chat Message Type
```json
{
    "data": {
        "type": "CHAT_MESSAGE",
        "orderId": "uuid",
        "deliveryOrderId": "uuid",
        "senderType": "COURIER"
    }
}
```
If the user is not currently in the chat screen, show a local notification to deep-link them back.

---

## 4. In-App Calling (WebRTC)

Use the WebSocket `/user/queue/chat/{deliveryOrderId}` for signaling.
1.  **Incoming Call**: Look for `messageType: "CALL_OFFER"`.
2.  **Signaling**: Send ICE candidates and answers to `/app/chat/{deliveryOrderId}/send`.
3.  **Permissions**: Ensure your app requests Microphone permissions before moving to `COURIER_ASSIGNED` state.
