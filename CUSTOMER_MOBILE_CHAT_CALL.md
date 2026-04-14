# Customer Mobile App — Chat & In-App Call Integration Guide

## Overview

Once a courier is assigned to an EXPRESS order the customer can:
- **Chat** in real-time with the courier via WebSocket (STOMP)
- **Make / receive in-app voice calls** via WebRTC, with the WebSocket acting as the signalling channel
- **View full chat history** via REST when opening the chat screen

Both features are only active while the order is in one of these states:
`COURIER_ASSIGNED` → `PICKED_UP` → `IN_TRANSIT`

After the order reaches `DELIVERED`, `CANCELLED`, or `FAILED` the history is still readable but no new messages or calls can be initiated.

---

## Prerequisites

| Data | Where to get it |
|------|----------------|
| `accessToken` | Customer JWT from sign-in |
| `deliveryOrderId` | `Order.deliveryOrderId` field on the order detail API (`GET /api/orders/{id}`) |
| `ecommerceOrderId` | `Order.id` — used for the REST history endpoint |

---

## 1. WebSocket Connection

Connect once per app session (or reconnect on token refresh).

```
wss://api.buyology.com/ws
```

SockJS is enabled so native WebSocket and SockJS transports are both supported.

### STOMP CONNECT frame headers

| Header | Value |
|--------|-------|
| `Authorization` | `Bearer <accessToken>` |
| `X-Client-Type` | `MOBILE` |

```js
// Example using @stomp/stompjs
const client = new Client({
  brokerURL: 'wss://api.buyology.com/ws/websocket',  // SockJS raw URL
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
    'X-Client-Type': 'MOBILE',
  },
  onConnect: () => {
    subscribeToChatAndCalls(deliveryOrderId);
  },
});
client.activate();
```

---

## 2. Subscribe to Chat & Call Events

Subscribe **after** STOMP CONNECT succeeds.

```
/user/queue/chat/{deliveryOrderId}
```

This is a private user-scoped queue — only your session receives messages on it.

```js
function subscribeToChatAndCalls(deliveryOrderId) {
  client.subscribe(
    `/user/queue/chat/${deliveryOrderId}`,
    (frame) => {
      const msg = JSON.parse(frame.body);
      handleIncoming(msg);
    }
  );
}
```

### Incoming message shape

```json
{
  "messageId":        "uuid",
  "deliveryOrderId":  "uuid",
  "ecommerceOrderId": "uuid",
  "senderId":         "uuid",
  "senderType":       "COURIER",
  "messageType":      "TEXT",
  "content":          "I'm 5 minutes away",
  "sentAt":           "2026-04-14T10:30:00Z",
  "deliveredAt":      "2026-04-14T10:30:01Z",
  "readAt":           null
}
```

`messageType` values: `TEXT`, `CALL_OFFER`, `CALL_ANSWER`, `CALL_ICE_CANDIDATE`, `CALL_END`, `CALL_REJECT`

---

## 3. Send a Text Message

Publish to: `/app/chat/{deliveryOrderId}/send`

```js
function sendTextMessage(deliveryOrderId, text) {
  client.publish({
    destination: `/app/chat/${deliveryOrderId}/send`,
    body: JSON.stringify({
      messageType: 'TEXT',
      content: text,
      clientType: 'MOBILE',
    }),
  });
}
```

You will receive an **echo** on `/user/queue/chat/{deliveryOrderId}` with `senderType: "CUSTOMER"` — use this as the send-confirmation to show the message in the UI.

---

## 4. In-App Voice Call (WebRTC)

The WebSocket channel doubles as the WebRTC signalling layer. No separate call SDK is needed. Media streams flow peer-to-peer.

### 4a. Initiate a call (customer → courier)

```js
async function startCall(deliveryOrderId) {
  const pc = new RTCPeerConnection(iceConfig);

  // Add local audio track
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  stream.getTracks().forEach(track => pc.addTrack(track, stream));

  // Send ICE candidates as they are discovered
  pc.onicecandidate = ({ candidate }) => {
    if (candidate) sendSignal(deliveryOrderId, 'CALL_ICE_CANDIDATE', JSON.stringify(candidate));
  };

  // Create and send the offer
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  sendSignal(deliveryOrderId, 'CALL_OFFER', JSON.stringify(offer));

  return pc;
}

function sendSignal(deliveryOrderId, messageType, sdpOrCandidate) {
  client.publish({
    destination: `/app/chat/${deliveryOrderId}/send`,
    body: JSON.stringify({
      messageType,            // CALL_OFFER | CALL_ICE_CANDIDATE | CALL_END | CALL_REJECT
      content: sdpOrCandidate,
      clientType: 'MOBILE',
    }),
  });
}
```

### 4b. Receive a call (courier called you)

```js
let activePeerConnection = null;

async function handleIncoming(msg) {
  switch (msg.messageType) {

    case 'TEXT':
      displayChatMessage(msg);
      break;

    case 'CALL_OFFER':
      // Show incoming call UI
      activePeerConnection = await answerCall(msg.deliveryOrderId, msg.content);
      break;

    case 'CALL_ANSWER':
      await activePeerConnection.setRemoteDescription(JSON.parse(msg.content));
      break;

    case 'CALL_ICE_CANDIDATE':
      await activePeerConnection.addIceCandidate(JSON.parse(msg.content));
      break;

    case 'CALL_END':
    case 'CALL_REJECT':
      endCall();
      break;
  }
}

async function answerCall(deliveryOrderId, offerJson) {
  const pc = new RTCPeerConnection(iceConfig);
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  stream.getTracks().forEach(track => pc.addTrack(track, stream));

  pc.onicecandidate = ({ candidate }) => {
    if (candidate) sendSignal(deliveryOrderId, 'CALL_ICE_CANDIDATE', JSON.stringify(candidate));
  };

  await pc.setRemoteDescription(JSON.parse(offerJson));
  const answer = await pc.createAnswer();
  await pc.setLocalDescription(answer);
  sendSignal(deliveryOrderId, 'CALL_ANSWER', JSON.stringify(answer));

  return pc;
}

function endCall() {
  if (activePeerConnection) {
    activePeerConnection.close();
    activePeerConnection = null;
  }
}
```

### 4c. Reject / end a call

```js
// Reject an incoming call
sendSignal(deliveryOrderId, 'CALL_REJECT', '{}');

// End an active call
sendSignal(deliveryOrderId, 'CALL_END', '{}');
endCall();
```

### ICE server configuration (recommended)

```js
const iceConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    // Add TURN server credentials for NAT traversal in production
  ],
};
```

---

## 5. Load Chat History (on screen open)

Before connecting the WebSocket, load existing messages via REST so the conversation
is immediately visible.

```
GET /api/orders/{ecommerceOrderId}/chat?page=0&size=50
Authorization: Bearer <accessToken>
```

Response:
```json
{
  "statusCode": 200,
  "message": "Chat history retrieved",
  "data": {
    "content": [ /* ChatMessageResponse objects */ ],
    "totalElements": 12,
    "totalPages": 1,
    "number": 0,
    "size": 50
  }
}
```

Render the `content` array, then connect the WebSocket for live updates.

---

## 6. FCM Push Notifications (background)

When the app is backgrounded and a new chat message arrives, the server sends an
FCM data push. Register / refresh the FCM token via:

```
POST /auth/fcm-token
Authorization: Bearer <accessToken>
Body: { "fcmToken": "<device_token>", "deviceType": "ANDROID" | "IOS" }
```

FCM payload for a new chat message:

```json
{
  "type":             "CHAT_MESSAGE",
  "deliveryOrderId":  "uuid",
  "ecommerceOrderId": "uuid",
  "senderType":       "COURIER"
}
```

On tap, deep-link the user directly to the chat screen for the given `ecommerceOrderId`.

---

## 7. Error Handling

| Scenario | Server behaviour | Client action |
|----------|-----------------|--------------|
| Send message on DELIVERED order | 409 relayed as WS error frame | Show "Chat closed" toast |
| Send message before courier assigned | 500/error frame | Disable chat UI until `COURIER_ASSIGNED` status |
| Send `CALL_OFFER` when order is terminal | 409 | Disable call button |
| WebSocket disconnected | — | Reconnect with exponential backoff; show offline indicator |
| Courier not connected (WS) | — | Server sends FCM push automatically |

---

## 8. State Machine — When to Show Chat UI

```
Order status          Chat UI
──────────────────────────────────────────
PENDING_PAYMENT       Hidden
PAID                  Hidden
COURIER_ASSIGNED      ✓ Show (chat + call)
PICKED_UP             ✓ Show (chat + call)
IN_TRANSIT            ✓ Show (chat + call)
DELIVERED             Read-only history
CANCELLED             Read-only history
FAILED                Read-only history
```
