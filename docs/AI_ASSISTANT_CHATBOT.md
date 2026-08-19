# Storefront AI assistant (customer-support chatbot)

A public chat widget on the storefront that answers customer questions about Buyology and the
products it sells — and declines everything else.

Backend only. The widget UI is not in this repo; this document is the contract it builds against.

---

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/assistant/status` | none | Whether to render the widget at all |
| `POST` | `/api/assistant/chat` | none | Send one message, get one reply |
| `GET` | `/api/admin/assistant/conversations` | `assistant:conversation:read` | Transcript list |
| `GET` | `/api/admin/assistant/conversations/{id}` | `assistant:conversation:read` | One full transcript |

### `GET /api/assistant/status`

```json
{ "statusCode": 200, "message": "Assistant status fetched", "data": { "enabled": true } }
```

`enabled: false` means no `ANTHROPIC_API_KEY` is configured or `ASSISTANT_ENABLED=false`. Hide the
widget — do not render a chat that will answer 503 as soon as someone types.

### `POST /api/assistant/chat`

```jsonc
{
  "message": "Do you have any refurbished MacBooks?",  // required, max 1000 chars
  "conversationId": null,      // omit on the first message; use the returned id after that
  "visitorId": "…",            // the same opaque browser id the visitor beacon sends
  "language": "en",            // en | ar | az — anything else is answered in English
  "countryCode": "AE",         // scopes products and prices to that market
  "currency": "AED"            // display currency for quoted prices
}
```

```jsonc
{
  "statusCode": 200,
  "message": "Assistant replied",
  "data": {
    "conversationId": "6b1f…",
    "reply": "Yes — we have two refurbished MacBook Air models in stock…",
    "inScope": true,            // false = the question was declined as off-topic
    "escalate": false,          // true = offer a handover to human support
    "products": [
      {
        "id": "…", "title": "MacBook Air M2", "slug": "macbook-air-m2",
        "brandName": "Apple", "availabilityStatus": "IN_STOCK",
        "price": 3999.00, "originalPrice": 4499.00, "currency": "AED",
        "imageUrl": "https://…", "averageRating": 4.6, "isRefurbished": true
      }
    ]
  }
}
```

**Client contract:** send `conversationId` on every message after the first. Do **not** send history
— the server holds it. `reply` is plain text; render it as text, not HTML.

Errors: `503` when the assistant is off, `400` on an empty or over-long message, `404` on an unknown
`conversationId`, `429` when a limit is hit (rate limit, per-conversation cap, or per-visitor daily
cap) — all carry a customer-safe `message`.

---

## How a turn works

```
customer message
      │
      ├─ 1. load/create conversation, load transcript, record the question
      │
      ├─ 2. retrieve products  ─ keyword search over the live catalog (this message +
      │                           the last few customer turns), scoped to the customer's
      │                           country and currency, PLUS the products cited earlier
      │                           in this conversation, re-loaded at current prices
      │
      ├─ 3. one Claude call    ─ system:  scope rules + company knowledge + live company
      │                                   facts + catalog overview   [prompt-cached]
      │                           messages: replayed transcript tail, then the retrieved
      │                                   products and the question
      │                           output:  structured { reply, inScope, escalate, productIds }
      │
      └─ 4. build cards from productIds by looking them up in what was retrieved,
            record the reply, return
```

**One API call per message, not a tool loop.** Products are fetched before the model runs rather
than by giving it a search tool. On a public, unauthenticated, per-token-billed endpoint a tool loop
would triple both the latency the customer feels and the bill an abuser can run up — and this way
the server, not the model, decides which products are in scope for a market.

---

## Where its knowledge comes from

| Source | Contents | Changes via |
|---|---|---|
| Live catalog | products, prices, stock, specs, ratings | admin dashboard |
| Live company facts | branches, addresses, opening hours, contact details, return window | admin dashboard |
| `assistant-knowledge.md` | policies and positioning that live nowhere in the schema | editing the file |

Anything the business can change in the dashboard is read live, so it can never be edited in two
places and drift. `assistant-knowledge.md` covers only what the schema has no column for.

**Fill in the `TODO` lines in `src/main/resources/assistant-knowledge.md` before launch.** Warranty
terms, return exclusions, delivery fees and support hours ship unanswered. The assistant hands those
questions to a human rather than guessing — an invented warranty term is a promise the business then
has to keep — but every unanswered one is an escalation that did not need to happen.

To correct an answer without a rebuild, mount an edited copy and point `ASSISTANT_KNOWLEDGE_PATH` at
it (same pattern as `REPAIR_PRICING_PATH`).

---

## Staying on topic

Four layers, of which the first does most of the work:

1. **The context is the scope.** The model is given the company knowledge, the live facts and a
   handful of catalog rows, and told those are its only sources. A weather question has nothing
   there to answer from.
2. **Explicit refusal rules**, including for the case it plainly knows the answer, plus an
   `inScope` flag it sets when it declines.
3. **Untrusted-input framing.** Customer text is wrapped in `<customer_message>` tags, declared as
   data rather than instructions, and any wrapper tag inside the customer's own text is stripped so
   the block cannot be closed early.
4. **Server-side transcript.** The client cannot supply history, so it cannot write the assistant's
   previous turns — which is the oldest way there is to talk a scoped bot out of its scope.

Product cards are built by looking the model's `productIds` up in the rows the server retrieved, so
an invented id renders nothing rather than a product that does not exist.

`assistant_messages.in_scope` records every refusal. **Watch the refusal rate**: an assistant that
starts declining real product questions is a regression visible nowhere else.

---

## Cost and abuse controls

This is the only public endpoint that spends money per request, so it has its own ceilings:

| Control | Default | Where |
|---|---|---|
| Rate limit on `/api/assistant/chat` | 12 req/min per IP | `RateLimitTier.ASSISTANT` |
| Messages per conversation | 40 | `ASSISTANT_MAX_MESSAGES_PER_CONVERSATION` |
| New conversations per visitor per day | 20 | `ASSISTANT_MAX_CONVERSATIONS_PER_VISITOR_PER_DAY` |
| Kill switch | on | `ASSISTANT_ENABLED` (repo variable — no code deploy) |

The rate limit bounds a burst; the two caps bound the daily spend. Unlike `PUBLIC` and `ADMIN`, the
`ASSISTANT` tier does **not** fail open when Redis is down — failing open there means an anonymous
caller can spend at the model provider for as long as the outage lasts.

`/api/assistant/status` is deliberately left in the `PUBLIC` tier: the widget probes it on every page
load, and sharing the chat bucket would spend a customer's budget on browsing.

---

## Configuration

Every variable is optional; the defaults boot. `ANTHROPIC_API_KEY` is the one that matters — blank
disables the assistant (and the repair and sell estimates, which share it).

| Variable | Default | Notes |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | Shared with the repair/sell estimates. Blank = feature off |
| `ASSISTANT_ENABLED` | `true` | Kill switch |
| `ASSISTANT_MODEL` | `claude-opus-5` | Separate from `ANTHROPIC_MODEL`: this runs on every customer message, so its cost/quality point is tuned on its own |
| `ASSISTANT_TIMEOUT_SECONDS` | `45` | A customer is watching this request |
| `ASSISTANT_MAX_MESSAGES_PER_CONVERSATION` | `40` | 0 disables |
| `ASSISTANT_MAX_CONVERSATIONS_PER_VISITOR_PER_DAY` | `20` | 0 disables |
| `ASSISTANT_KNOWLEDGE_PATH` | — | External knowledge base; blank = the bundled file |

---

## Data

`V31__assistant_conversations.sql` adds `assistant_conversations` and `assistant_messages`.

No IP address or personal identifier is stored. `visitor_id` is the same opaque browser id the
visitor beacon uses, kept only for the daily conversation cap; `user_id` is set only when a
signed-in customer happens to be chatting.

Transcripts are customer-written free text, so the admin reads are gated by
`assistant:conversation:read` rather than left open to any authenticated admin.

**There is no purge job yet.** `assistant_messages` grows with traffic rather than with business
events, like `site_visits`. Retention was left unset on purpose: the right period is a business
decision about customer-written text, and the volume is unknown until the widget is live. Revisit
after a month of real traffic — `SiteVisitPurgeJob` is the pattern to copy.

---

## Not built yet

- **Streaming.** Replies arrive in one response, so the customer waits a few seconds with a typing
  indicator. SSE would be the next improvement and needs no schema change.
- **Order-aware answers.** The assistant has no access to accounts, orders or tracking; it sets
  `escalate` instead. Wiring it to order data means authenticating the customer first, which is a
  different security posture from the one this widget is built on.
- **Retention/purge job** for transcripts (above).
