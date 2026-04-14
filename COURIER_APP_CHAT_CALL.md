# Courier App — Chat & In-App Call Integration Guide

## Overview

After a courier **accepts** a delivery assignment, they can:
- **Chat** in real-time with the customer via WebSocket (STOMP)
- **Make / receive in-app voice calls** via WebRTC, with the WebSocket acting as the signalling channel
- **View full chat history** via REST when opening the chat screen

Chat and calls are only available while the delivery is in an active state:
`COURIER_ASSIGNED` → `COURIER_ACCEPTED` → `ARRIVED_AT_PICKUP` → `PICKED_UP` → `ON_THE_WAY` → `ARRIVED_AT_DESTINATION`

After the delivery reaches `DELIVERED`, `FAILED`, or `CANCELLED` the history is still readable but no new messages or calls can be initiated.

---

## Prerequisites

| Data | Where to get it |
|------|----------------|
| `accessToken` | Courier JWT from courier sign-in (`POST /api/auth/courier/login`) |
| `deliveryOrderId` | `DeliveryOrder.id` — returned in the assignment notification payload |
| `ecommerceOrderId` | Also in the assignment payload (`ecommerceOrderId` field) |

---

## 1. WebSocket Connection

The courier app connects to the **courier backend** WebSocket — this is **separate** from the ecommerce WebSocket.

```
wss://courier.buyology.com/ws
```

No SockJS — native WebSocket only.

### STOMP CONNECT frame headers

| Header | Value |
|--------|-------|
| `Authorization` | `Bearer <courierAccessToken>` |

```kotlin
// Example using Kotlin + STOMP library
val client = StompClient(
    url = "wss://courier.buyology.com/ws",
    headers = mapOf("Authorization" to "Bearer $accessToken")
)
client.connect()
```

---

## 2. Subscribe to Assignments + Chat

Subscribe to both channels after CONNECT:

```kotlin
// Existing assignment channel (already implemented)
client.subscribe("/user/queue/assignments") { frame ->
    handleAssignment(frame.body)
}

// NEW: Chat channel — subscribe after accepting a delivery
fun subscribeToChatForDelivery(deliveryOrderId: String) {
    client.subscribe("/user/queue/chat/$deliveryOrderId") { frame ->
        val msg = Json.decodeFromString<ChatMessage>(frame.body)
        handleIncoming(msg)
    }
}
```

Subscribe to the chat channel as soon as you accept an assignment (status becomes `COURIER_ACCEPTED`).

### Incoming message shape

```json
{
  "messageId":        "uuid",
  "deliveryOrderId":  "uuid",
  "ecommerceOrderId": "uuid",
  "senderId":         "uuid",
  "senderType":       "CUSTOMER",
  "messageType":      "TEXT",
  "content":          "Please leave it at the door",
  "sentAt":           "2026-04-14T10:30:00Z",
  "deliveredAt":      "2026-04-14T10:30:01Z",
  "readAt":           null
}
```

`messageType` values: `TEXT`, `CALL_OFFER`, `CALL_ANSWER`, `CALL_ICE_CANDIDATE`, `CALL_END`, `CALL_REJECT`

---

## 3. Send a Text Message

Publish to: `/app/chat/{deliveryOrderId}/send`

```kotlin
fun sendTextMessage(deliveryOrderId: String, text: String) {
    client.send(
        destination = "/app/chat/$deliveryOrderId/send",
        body = """{"messageType":"TEXT","content":"$text"}"""
    )
}
```

You will receive an **echo** on `/user/queue/chat/{deliveryOrderId}` with `senderType: "COURIER"` — use this as the delivery confirmation to display the message in the UI.

---

## 4. In-App Voice Call (WebRTC)

The same WebSocket channel handles WebRTC signalling. Media streams are peer-to-peer.

### 4a. Initiate a call (courier → customer)

```kotlin
suspend fun startCall(deliveryOrderId: String): RTCPeerConnection {
    val pc = RTCPeerConnection(iceConfig)

    val stream = getUserMedia(audio = true)
    stream.tracks.forEach { pc.addTrack(it, stream) }

    pc.onIceCandidate = { candidate ->
        if (candidate != null) sendSignal(deliveryOrderId, "CALL_ICE_CANDIDATE", candidate.toJson())
    }

    val offer = pc.createOffer()
    pc.setLocalDescription(offer)
    sendSignal(deliveryOrderId, "CALL_OFFER", offer.toJson())

    return pc
}

fun sendSignal(deliveryOrderId: String, type: String, content: String) {
    client.send(
        destination = "/app/chat/$deliveryOrderId/send",
        body = """{"messageType":"$type","content":${content.toJsonString()}}"""
    )
}
```

### 4b. Receive a call (customer called you)

```kotlin
var activePc: RTCPeerConnection? = null

fun handleIncoming(msg: ChatMessage) {
    when (msg.messageType) {

        "TEXT" -> displayChatMessage(msg)

        "CALL_OFFER" -> {
            // Show incoming call UI with accept/reject buttons
            showIncomingCallScreen(
                onAccept = { activePc = answerCall(msg.deliveryOrderId, msg.content) },
                onReject = { sendSignal(msg.deliveryOrderId, "CALL_REJECT", "{}") }
            )
        }

        "CALL_ANSWER" -> {
            activePc?.setRemoteDescription(parseSessionDescription(msg.content))
        }

        "CALL_ICE_CANDIDATE" -> {
            activePc?.addIceCandidate(parseIceCandidate(msg.content))
        }

        "CALL_END", "CALL_REJECT" -> endCall()
    }
}

suspend fun answerCall(deliveryOrderId: String, offerJson: String): RTCPeerConnection {
    val pc = RTCPeerConnection(iceConfig)
    val stream = getUserMedia(audio = true)
    stream.tracks.forEach { pc.addTrack(it, stream) }

    pc.onIceCandidate = { c ->
        if (c != null) sendSignal(deliveryOrderId, "CALL_ICE_CANDIDATE", c.toJson())
    }

    pc.setRemoteDescription(parseSessionDescription(offerJson))
    val answer = pc.createAnswer()
    pc.setLocalDescription(answer)
    sendSignal(deliveryOrderId, "CALL_ANSWER", answer.toJson())

    return pc
}

fun endCall() {
    activePc?.close()
    activePc = null
}
```

### ICE server configuration

```kotlin
val iceConfig = RTCConfiguration(
    iceServers = listOf(
        RTCIceServer(urls = listOf("stun:stun.l.google.com:19302")),
        // Add TURN credentials for production NAT traversal
    )
)
```

---

## 5. Load Chat History (on screen open)

Fetch existing messages before subscribing the WebSocket so the conversation renders immediately.

```
GET /api/deliveries/{deliveryOrderId}/chat?page=0&size=50
Authorization: Bearer <courierAccessToken>
```

Response:
```json
{
  "content": [ /* ChatMessageResponse objects */ ],
  "totalElements": 8,
  "totalPages": 1,
  "number": 0,
  "size": 50
}
```

Render messages, then connect the WebSocket for live updates.

---

## 6. FCM Push Notifications (background)

Register the device FCM token after every login:

```
PUT /api/auth/courier/fcm-token
Authorization: Bearer <accessToken>
Body: { "fcmToken": "<device_token>" }
```

The server sends an FCM data push when a customer message arrives and the courier's WebSocket is not connected.

FCM payload:

```json
{
  "type":             "CHAT_MESSAGE",
  "deliveryOrderId":  "uuid",
  "ecommerceOrderId": "uuid",
  "senderType":       "CUSTOMER"
}
```

On tap, deep-link directly to the chat screen for the given `deliveryOrderId`.

---

## 7. Full Message Flow Diagram

```
COURIER APP                  COURIER BACKEND         ECOMMERCE BACKEND        CUSTOMER APP
    │                              │                         │                      │
    │──/app/chat/{id}/send ───────►│                         │                      │
    │  (TEXT or CALL_*)            │                         │                      │
    │                              │── persist ──────────────┤                      │
    │                              │── RabbitMQ chat.message.courier ──────────────►│
    │◄── echo /user/queue/chat/{id}│                         │── persist ───────────┤
    │                              │                         │── WS push ──────────►│
    │                              │                         │   /user/queue/chat   │
    │                              │                         │                      │
    │◄── /user/queue/chat/{id} ────│◄── RabbitMQ chat.message.customer ────────────│
    │    (customer's TEXT/CALL_*)  │── persist               │                      │
```

---

## 8. Error Handling

| Scenario | Server behaviour | Client action |
|----------|-----------------|--------------|
| Send on DELIVERED delivery | Error frame (IllegalStateException → 409) | Show "Chat closed" |
| Not the assigned courier | AccessDeniedException | Should not happen; verify deliveryOrderId |
| WebSocket disconnects | — | Reconnect with exponential backoff |
| Customer not connected (WS) | — | Server sends FCM push automatically |

---

## 9. State Machine — When to Show Chat UI

```
Delivery status            Chat UI
───────────────────────────────────────────────────
CREATED                    Hidden (no courier yet)
COURIER_ASSIGNED           ✓ Show (chat + call)
COURIER_ACCEPTED           ✓ Show (chat + call)
ARRIVED_AT_PICKUP          ✓ Show (chat + call)
PICKED_UP                  ✓ Show (chat + call)
ON_THE_WAY                 ✓ Show (chat + call)
ARRIVED_AT_DESTINATION     ✓ Show (chat + call)
DELIVERED                  Read-only history
FAILED                     Read-only history
CANCELLED                  Read-only history
```
