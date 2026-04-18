# Web Frontend: Real-Time Delivery Tracking Integration

This guide covers integrating the real-time delivery status updates and courier tracking for the **Order Details** page.

## 1. WebSocket Connection (STOMP)

The web frontend should connect to the WebSocket endpoint using SockJS.

**Endpoint:** `https://api.buyology.com/ws`

### Connection Headers
Include the user's JWT in the `Authorization` header and set `X-Client-Type` to `WEB`.

```javascript
const socket = new SockJS('https://api.buyology.com/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({
    Authorization: 'Bearer ' + jwtToken,
    'X-Client-Type': 'WEB'
}, frame => {
    console.log('Connected to WebSocket');
    subscribeToOrderUpdates(orderId);
});
```

---

## 2. Order Status Updates

Subscribe to the order-specific status topic to receive real-time transitions (e.g., when the courier picks up the package or delivers it).

**Topic:** `/topic/orders/{orderId}/status`

### Payload Structure
```json
{
    "orderId": "uuid",
    "status": "PICKED_UP", 
    "proofImageUrl": "https://storage.buyology.com/proofs/abc.jpg", // Populated on PICKED_UP or DELIVERED
    "timestamp": "2026-04-19T12:00:00Z"
}
```

### UX Strategy
1. **Status Mapping**: Map the `status` string to your progress stepper.
2. **Proof Images**: If `proofImageUrl` is present and not empty, display it as "Courier Photo" in the tracking history.
3. **Chat/Call Buttons**: When `status` moves to `COURIER_ASSIGNED`, refresh the Order object via REST to get the `courierName`, `courierPhone`, and `deliveryOrderId`.

---

## 3. Chat Integration

Web users use the **Rest API** for history and **WebSocket** for live messaging.

### Live Chat Topic
**Topic:** `/user/queue/chat/{deliveryOrderId}`

### Sending Messages
**Destination:** `/app/chat/{deliveryOrderId}/send`
```javascript
stompClient.send(`/app/chat/${deliveryOrderId}/send`, {}, JSON.stringify({
    messageType: 'TEXT',
    content: 'Hello, I will be at the gate.',
    clientType: 'WEB'
}));
```

---

## 4. Polling Fallback
If the WebSocket fails to connect, fallback to polling `GET /api/orders/{orderId}` every 10 seconds.
