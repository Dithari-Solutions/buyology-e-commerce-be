# Web Frontend — Chat Integration Guide

## Overview

The web frontend supports **text chat only** between the customer and the assigned courier.
In-app voice/video calls are **not available** on the web — call-type messages are rejected
at the server level (HTTP 400) and the call UI should not be rendered.

Chat is only active while the order status is:
`COURIER_ASSIGNED` → `PICKED_UP` → `IN_TRANSIT`

---

## Prerequisites

| Data | Where to get it |
|------|----------------|
| `accessToken` | Customer JWT from sign-in |
| `ecommerceOrderId` | `Order.id` from the order detail API |
| `deliveryOrderId` | `Order.deliveryOrderId` from the order detail API |

Both IDs are available on the order detail response. Fetch the order first, then open the chat screen.

---

## 1. Load History on Screen Open

Fetch the full conversation **before** connecting the WebSocket so the chat renders
instantly without a loading gap.

```
GET /api/orders/{ecommerceOrderId}/chat?page=0&size=50
Authorization: Bearer <accessToken>
```

Response body:
```json
{
  "statusCode": 200,
  "message": "Chat history retrieved",
  "data": {
    "content": [
      {
        "messageId":        "uuid",
        "deliveryOrderId":  "uuid",
        "ecommerceOrderId": "uuid",
        "senderId":         "uuid",
        "senderType":       "COURIER",
        "messageType":      "TEXT",
        "content":          "I'm on my way",
        "sentAt":           "2026-04-14T10:28:00Z",
        "deliveredAt":      "2026-04-14T10:28:01Z",
        "readAt":           null
      }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 50
  }
}
```

**Rendering rule:** Only render messages where `messageType === "TEXT"`. Silently
skip any `CALL_*` entries that may appear in the history (sent from mobile).

---

## 2. WebSocket Connection

Connect to the **ecommerce backend** WebSocket. SockJS is enabled.

```
wss://api.buyology.com/ws
```

### STOMP CONNECT headers

| Header | Value | Required |
|--------|-------|---------|
| `Authorization` | `Bearer <accessToken>` | Yes |
| `X-Client-Type` | `WEB` | Yes — tells the server to reject call signals |

```js
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const stompClient = new Client({
  webSocketFactory: () => new SockJS('https://api.buyology.com/ws'),
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
    'X-Client-Type': 'WEB',
  },
  onConnect: () => {
    subscribeToChat(deliveryOrderId);
  },
  onDisconnect: () => {
    // Show offline indicator; attempt reconnect with backoff
  },
  reconnectDelay: 5000,
});

stompClient.activate();
```

---

## 3. Subscribe to Incoming Messages

```js
function subscribeToChat(deliveryOrderId) {
  stompClient.subscribe(
    `/user/queue/chat/${deliveryOrderId}`,
    (frame) => {
      const msg = JSON.parse(frame.body);

      // Ignore any call signals — web does not support calls
      if (msg.messageType !== 'TEXT') return;

      appendMessageToUI(msg);
    }
  );
}
```

### Message shape

```json
{
  "messageId":        "uuid",
  "deliveryOrderId":  "uuid",
  "ecommerceOrderId": "uuid",
  "senderId":         "uuid",
  "senderType":       "CUSTOMER" | "COURIER",
  "messageType":      "TEXT",
  "content":          "Hello!",
  "sentAt":           "2026-04-14T10:30:00Z",
  "deliveredAt":      "2026-04-14T10:30:01Z",
  "readAt":           null
}
```

Use `senderType` to align messages left (COURIER) or right (CUSTOMER).

---

## 4. Send a Text Message

```js
function sendMessage(deliveryOrderId, text) {
  stompClient.publish({
    destination: `/app/chat/${deliveryOrderId}/send`,
    body: JSON.stringify({
      messageType: 'TEXT',
      content: text,
      clientType: 'WEB',        // Required — call signals rejected for WEB
    }),
  });
}
```

The server echoes the sent message back on `/user/queue/chat/{deliveryOrderId}` with
`senderType: "CUSTOMER"`. Use this echo as the confirmation to display the bubble.

---

## 5. Pagination — Load Older Messages

For long conversations, load earlier pages on scroll-to-top:

```
GET /api/orders/{ecommerceOrderId}/chat?page=1&size=50
Authorization: Bearer <accessToken>
```

Prepend the `content` array to the existing message list and update the scroll position.

---

## 6. Call Feature — Not Available on Web

Calls are explicitly disabled for web sessions:

- The server **rejects** any message with `messageType` other than `TEXT` when
  `clientType: "WEB"` is set, returning an error STOMP frame.
- **Do not render** a call button on the web chat UI.
- If an existing chat history contains `CALL_*` messages (sent from mobile), filter
  them out on render — they have no meaningful representation on web.

---

## 7. Complete React Example

```tsx
import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

interface ChatMsg {
  messageId: string;
  senderType: 'CUSTOMER' | 'COURIER';
  messageType: string;
  content: string;
  sentAt: string;
}

export function ChatPanel({ ecommerceOrderId, deliveryOrderId, accessToken, orderStatus }: {
  ecommerceOrderId: string;
  deliveryOrderId: string;
  accessToken: string;
  orderStatus: string;
}) {
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState('');
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);


  const isChatActive = ['COURIER_ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'].includes(orderStatus);

  // Load history
  useEffect(() => {
    fetch(`/api/orders/${ecommerceOrderId}/chat?page=0&size=50`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
      .then(r => r.json())
      .then(body => {
        const texts = (body.data.content as ChatMsg[]).filter(m => m.messageType === 'TEXT');
        setMessages(texts);
      });
  }, [ecommerceOrderId]);

  // Connect WebSocket
  useEffect(() => {
    if (!deliveryOrderId || !isChatActive) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
        'X-Client-Type': 'WEB',
      },
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/user/queue/chat/${deliveryOrderId}`, frame => {
          const msg: ChatMsg = JSON.parse(frame.body);
          if (msg.messageType !== 'TEXT') return;
          setMessages(prev => [...prev, msg]);
        });
      },
      onDisconnect: () => setConnected(false),
      reconnectDelay: 5000,
    });

    client.activate();
    clientRef.current = client;
    return () => { client.deactivate(); };
  }, [deliveryOrderId, isChatActive]);

  function send() {
    if (!input.trim() || !clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/chat/${deliveryOrderId}/send`,
      body: JSON.stringify({ messageType: 'TEXT', content: input.trim(), clientType: 'WEB' }),
    });
    setInput('');
  }

  return (
    <div className="chat-panel">
      <div className="messages">
        {messages.map(m => (
          <div key={m.messageId} className={`bubble ${m.senderType.toLowerCase()}`}>
            <span>{m.content}</span>
            <time>{new Date(m.sentAt).toLocaleTimeString()}</time>
          </div>
        ))}
      </div>

      {isChatActive ? (
        <div className="input-row">
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && send()}
            placeholder="Type a message…"
            disabled={!connected}
          />
          <button onClick={send} disabled={!connected || !input.trim()}>Send</button>
        </div>
      ) : (
        <p className="chat-closed">Chat is closed for this order.</p>
      )}
    </div>
  );
}
```

---

## 8. When to Show the Chat Panel

| Order status | Chat panel |
|-------------|-----------|
| `PENDING_PAYMENT` | Hidden |
| `PAID` | Hidden |
| `COURIER_ASSIGNED` | Visible — live chat enabled |
| `PICKED_UP` | Visible — live chat enabled |
| `IN_TRANSIT` | Visible — live chat enabled |
| `DELIVERED` | Visible — read-only (no input box) |
| `CANCELLED` | Visible — read-only |
| `FAILED` | Visible — read-only |

For read-only states: load history via REST but do not connect the WebSocket.

---

## 9. Error States

| Cause | User-visible message |
|-------|---------------------|
| 409 from server (closed order) | "This chat has ended." |
| 400 from server (call type sent) | Should never happen if `clientType: "WEB"` is set |
| WebSocket disconnected | "Reconnecting…" badge on send button |
| No `deliveryOrderId` on order | Hide chat panel entirely |
