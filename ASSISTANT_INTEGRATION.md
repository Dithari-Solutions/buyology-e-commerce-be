# Buyology AI Assistant — Frontend Integration Guide

How the storefront chat box at **v2.buyology.online** talks to the AI assistant.
Written for **Next.js (App Router) + TypeScript**.

The assistant answers questions about Buyology and the products it sells, and declines
everything else.

| | |
|---|---|
| **API base (dev)** | `https://api-dev.buyology.online` |
| **Frontend** | `https://v2.buyology.online` |
| **Auth** | None — public endpoint. Anonymous visitors are the point. |
| **Transport** | JSON over HTTPS, one request per message. No websocket, no streaming (yet). |
| **Backend reference** | [`docs/AI_ASSISTANT_CHATBOT.md`](docs/AI_ASSISTANT_CHATBOT.md) |

---

## Contents

1. [Decide first: direct or proxied](#1-decide-first-direct-or-proxied)
2. [Endpoints](#2-endpoints)
3. [TypeScript types](#3-typescript-types)
4. [The API client](#4-the-api-client)
5. [The React hook](#5-the-react-hook)
6. [The widget component](#6-the-widget-component)
7. [Optional: Next.js route handler proxy](#7-optional-nextjs-route-handler-proxy)
8. [Rendering rules](#8-rendering-rules)
9. [Errors and limits](#9-errors-and-limits)
10. [Known issues](#10-known-issues)
11. [Ship checklist](#11-ship-checklist)

---

## 1. Decide first: direct or proxied

There are two ways a Next.js app can reach this API, and the choice has a consequence that
is easy to miss until the widget is live.

| | **A — Browser → API** (recommended) | **B — Browser → Next route handler → API** |
|---|---|---|
| CORS | Backend must allowlist your origin | Not involved — server-to-server |
| Rate limiting | Correct: 12/min **per visitor IP** | **Broken by default** — every visitor shares one bucket |
| API base visible to client | Yes | No |
| Extra latency | None | One extra hop |

**Take option A.** The rate limiter keys its bucket on the caller's IP. Behind a route handler
every request arrives from *your Next.js server's* IP, so all your visitors share a single
12-requests-per-minute budget and the widget starts returning `429` to everyone as soon as a
handful of people chat at once.

Option B is only safe if the proxy forwards the real client IP as `X-Forwarded-For` **and** the
backend trusts it — see [§7](#7-optional-nextjs-route-handler-proxy).

### Prerequisite for option A: CORS

The API uses an explicit allowlist — no wildcards, because it sends credentials. An unlisted
origin fails in the browser while working perfectly in `curl`. Expect this to be your first bug.

> **Backend action required.** `CORS_ALLOWED_ORIGINS` on the dev server must include
> `https://v2.buyology.online` — exact scheme and host, no trailing slash, comma-separated.
> It lives in `.env.dev` and feeds `app.cors.allowed-origins`. Restart the app container after
> changing it.

Verify from the browser console on v2 before writing any widget code:

```js
fetch('https://api-dev.buyology.online/api/assistant/status')
  .then(r => r.json()).then(console.log)
// { statusCode: 200, message: "Assistant status fetched", data: { enabled: true } }
```

If that throws a CORS error, stop and fix the allowlist. Everything else is downstream.

### Environment

```bash
# .env.local
NEXT_PUBLIC_ASSISTANT_API_BASE=https://api-dev.buyology.online
```

`NEXT_PUBLIC_` because the browser calls it directly under option A.

---

## 2. Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/assistant/status` | Whether to render the widget at all |
| `POST` | `/api/assistant/chat` | Send one message, get one reply |

Call `/status` once on mount. When it returns `enabled: false` the assistant has no API key or
has been switched off — **hide the launcher entirely**. A chat box that refuses the first
message is a worse first impression than no chat box.

---

## 3. TypeScript types

These mirror the backend DTOs exactly. Optional (`?`) properties are the ones the server omits
when null — they are **absent keys, not `null` values**.

```ts
// types/assistant.ts

/** Languages the catalog is actually translated into. Anything else is answered in English. */
export type AssistantLanguage = 'en' | 'ar' | 'az';

export interface AssistantChatRequest {
  /** Required. Non-blank, max 1000 characters. */
  message: string;
  /** Omit on the first message; the reply carries the new id. */
  conversationId?: string;
  /** Max 64 chars. Share this with the analytics beacon's browser id. */
  visitorId?: string;
  language?: AssistantLanguage;
  /** ISO-3166 alpha-2, e.g. "AE". Scopes products and prices to that market. */
  countryCode?: string;
  /** e.g. "AED". Display currency for quoted prices. */
  currency?: string;
}

export interface AssistantProductCard {
  id: string;
  /** Null when the product has no translation in the requested language. */
  title: string | null;
  slug: string | null;
  brandName: string | null;
  /** "IN_STOCK" | "OUT_OF_STOCK" | "PRE_ORDER" */
  availabilityStatus: string | null;
  isRefurbished: boolean | null;

  // ── Omitted entirely when null — always use optional access ──
  price?: number;
  originalPrice?: number;
  currency?: string;
  imageUrl?: string;
  averageRating?: number;
}

export interface AssistantChatData {
  conversationId: string;
  /** Plain text. No markdown, no HTML. Render with textContent semantics. */
  reply: string;
  /** false = the question was outside what the assistant answers. */
  inScope: boolean;
  /** true = hand the customer to a human. */
  escalate: boolean;
  /** In the order the assistant mentioned them. Empty when none. */
  products: AssistantProductCard[];
}

export interface AssistantStatusData {
  enabled: boolean;
}

/** The standard envelope every endpoint uses — except 429, see below. */
export interface ApiResponse<T> {
  statusCode: number;
  message: string;
  data: T | null;
}

/** 429 is written by a servlet filter and has a DIFFERENT shape. */
export interface RateLimitBody {
  success: false;
  message: string;
  retryAfterSeconds: number;
}

/** Discriminated union returned by the client below. */
export type SendResult =
  | { ok: true; data: AssistantChatData }
  | { ok: false; kind: 'rate_limited'; retryAfterSeconds: number }
  | { ok: false; kind: 'network' }
  | { ok: false; kind: 'failed'; status: number; message?: string };
```

### What the three flags mean

| Field | When | What the widget should do |
|---|---|---|
| `inScope: false` | Question was off-topic | Render `reply` normally — it is already a polite redirect. `products` is always empty here. |
| `escalate: true` | Order-specific, a complaint, or unanswerable | Show a "Talk to our team" action beneath the bubble. |
| `products.length` | Assistant referred to specific items | Render cards in array order, linked by `slug`. |

> **There is no `messages` array in the request.** The server owns the transcript and replays it
> from the database each turn — that is what stops a client forging the assistant's previous
> turns. You send one message and an id; that is the entire surface you control.

---

## 4. The API client

```ts
// lib/assistant/client.ts
import type {
  ApiResponse, AssistantChatData, AssistantChatRequest,
  AssistantLanguage, AssistantStatusData, RateLimitBody, SendResult,
} from '@/types/assistant';

const API_BASE = process.env.NEXT_PUBLIC_ASSISTANT_API_BASE!;

const CID_KEY = 'buy_assistant_cid';
const VID_KEY = 'buy_visitor_id';

/**
 * Same browser id the analytics beacon uses, so the per-visitor daily cap counts
 * browsers rather than tabs. localStorage: survives reloads.
 */
function visitorId(): string {
  let id = localStorage.getItem(VID_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(VID_KEY, id);
  }
  return id;
}

export interface AssistantLocale {
  language: AssistantLanguage;
  countryCode: string;
  currency: string;
}

export class AssistantClient {
  private conversationId: string | null;
  private blockedUntil = 0;

  constructor(private readonly locale: AssistantLocale) {
    // sessionStorage, not local: a new tab should start a fresh conversation.
    this.conversationId =
      typeof window === 'undefined' ? null : sessionStorage.getItem(CID_KEY);
  }

  async isEnabled(signal?: AbortSignal): Promise<boolean> {
    try {
      const res = await fetch(`${API_BASE}/api/assistant/status`, { signal });
      if (!res.ok) return false;
      const body = (await res.json()) as ApiResponse<AssistantStatusData>;
      return body.data?.enabled === true;
    } catch {
      return false; // network or CORS failure — hide the widget
    }
  }

  async send(text: string): Promise<SendResult> {
    const waitMs = this.blockedUntil - Date.now();
    if (waitMs > 0) {
      return { ok: false, kind: 'rate_limited', retryAfterSeconds: Math.ceil(waitMs / 1000) };
    }

    const payload: AssistantChatRequest = {
      message: text.slice(0, 1000),
      visitorId: visitorId(),
      ...this.locale,
      ...(this.conversationId ? { conversationId: this.conversationId } : {}),
    };

    let res: Response;
    try {
      res = await fetch(`${API_BASE}/api/assistant/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
    } catch {
      return { ok: false, kind: 'network' };
    }

    // 429 comes from a servlet filter, so its body shape differs from every other
    // response. Read the BODY: the Retry-After header is not CORS-exposed, so
    // res.headers.get('Retry-After') returns null cross-origin.
    if (res.status === 429) {
      const body = (await res.json().catch(() => null)) as RateLimitBody | null;
      const secs = body?.retryAfterSeconds ?? 60;
      this.blockedUntil = Date.now() + secs * 1000;
      return { ok: false, kind: 'rate_limited', retryAfterSeconds: secs };
    }

    const body = (await res.json().catch(() => null)) as ApiResponse<AssistantChatData> | null;

    if (!res.ok || !body?.data) {
      // Several conditions surface as 500 today — see §9. Treat generically.
      return { ok: false, kind: 'failed', status: res.status, message: body?.message };
    }

    this.conversationId = body.data.conversationId;
    sessionStorage.setItem(CID_KEY, this.conversationId);
    return { ok: true, data: body.data };
  }

  reset(): void {
    this.conversationId = null;
    sessionStorage.removeItem(CID_KEY);
  }
}
```

---

## 5. The React hook

```ts
// lib/assistant/useAssistant.ts
'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AssistantClient, type AssistantLocale } from './client';
import type { AssistantProductCard } from '@/types/assistant';

export type Turn =
  | { role: 'customer'; text: string }
  | { role: 'assistant'; text: string; products: AssistantProductCard[]; escalate: boolean }
  | { role: 'notice'; text: string };

export function useAssistant(locale: AssistantLocale) {
  const client = useMemo(
    () => new AssistantClient(locale),
    [locale.language, locale.countryCode, locale.currency],
  );

  /** null = still checking. Keep the launcher hidden until this is true. */
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);

  useEffect(() => {
    const ctrl = new AbortController();
    client.isEnabled(ctrl.signal).then(setEnabled).catch(() => setEnabled(false));
    return () => ctrl.abort();
  }, [client]);

  const send = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || busyRef.current) return; // one request in flight at a time

      busyRef.current = true;
      setBusy(true);
      setTurns((t) => [...t, { role: 'customer', text: trimmed }]);

      const result = await client.send(trimmed);

      busyRef.current = false;
      setBusy(false);

      setTurns((t) => [
        ...t,
        result.ok
          ? {
              role: 'assistant',
              text: result.data.reply,
              products: result.data.products,
              escalate: result.data.escalate,
            }
          : {
              role: 'notice',
              text:
                result.kind === 'rate_limited'
                  ? `Just a moment — try again in ${result.retryAfterSeconds}s.`
                  : "That didn't go through. Try again, or contact our team.",
            },
      ]);
    },
    [client],
  );

  const reset = useCallback(() => {
    client.reset();
    setTurns([]);
  }, [client]);

  return { enabled, turns, busy, send, reset };
}
```

---

## 6. The widget component

```tsx
// components/AssistantWidget.tsx
'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useAssistant } from '@/lib/assistant/useAssistant';
import type { AssistantProductCard } from '@/types/assistant';

const MAX_LEN = 1000;

export function AssistantWidget({ language = 'en' as const }) {
  const { enabled, turns, busy, send } = useAssistant({
    language,
    countryCode: 'AE',
    currency: 'AED',
  });
  const [draft, setDraft] = useState('');

  // Render nothing while checking, and nothing at all when switched off.
  if (enabled !== true) return null;

  return (
    <div className="assistant">
      <div className="assistant__log" aria-live="polite">
        {turns.map((turn, i) => {
          if (turn.role === 'notice') {
            return <p key={i} className="assistant__notice">{turn.text}</p>;
          }
          return (
            <div key={i} className={`assistant__bubble assistant__bubble--${turn.role}`}>
              {/* String child, never dangerouslySetInnerHTML. */}
              <p style={{ whiteSpace: 'pre-wrap' }}>{turn.text}</p>

              {turn.role === 'assistant' && turn.products.length > 0 && (
                <ul className="assistant__cards">
                  {turn.products.map((p) => <ProductCard key={p.id} card={p} />)}
                </ul>
              )}

              {turn.role === 'assistant' && turn.escalate && (
                <Link href="/support" className="assistant__handoff">
                  Talk to our team
                </Link>
              )}
            </div>
          );
        })}
        {busy && <p className="assistant__typing">Typing…</p>}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(draft);
          setDraft('');
        }}
      >
        <input
          value={draft}
          maxLength={MAX_LEN}
          disabled={busy}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Ask about our products, delivery or returns…"
        />
        <button type="submit" disabled={busy || !draft.trim()}>Send</button>
        {draft.length > MAX_LEN - 100 && (
          <span>{MAX_LEN - draft.length} characters left</span>
        )}
      </form>
    </div>
  );
}

function ProductCard({ card }: { card: AssistantProductCard }) {
  return (
    <li>
      <Link href={`/product/${card.slug}`}>
        {/* Optional access throughout — these keys can be absent. */}
        {card.imageUrl && <img src={card.imageUrl} alt="" loading="lazy" />}
        <span>{card.title ?? 'View product'}</span>
        {card.price != null && card.currency && (
          <span>
            {card.price} {card.currency}
            {card.originalPrice != null && <s>{card.originalPrice}</s>}
          </span>
        )}
        {card.availabilityStatus === 'OUT_OF_STOCK' && <em>Out of stock</em>}
      </Link>
    </li>
  );
}
```

Mount it in the root layout so it survives navigation:

```tsx
// app/layout.tsx
import { AssistantWidget } from '@/components/AssistantWidget';

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {children}
        <AssistantWidget />
      </body>
    </html>
  );
}
```

> **Keep it a Client Component.** The widget is stateful and browser-only (`localStorage`,
> `sessionStorage`, `crypto.randomUUID`). Do not call the chat endpoint from a Server Component
> or a Server Action — that turns option A into option B and shares one rate-limit bucket
> across every visitor.

---

## 7. Optional: Next.js route handler proxy

Only if you cannot allowlist the origin. **You must forward the real client IP**, or every
visitor shares one 12/min bucket.

```ts
// app/api/assistant/chat/route.ts
import { NextRequest, NextResponse } from 'next/server';

const API_BASE = process.env.ASSISTANT_API_BASE!; // server-only, no NEXT_PUBLIC_

export async function POST(req: NextRequest) {
  const clientIp =
    req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ?? '';

  const upstream = await fetch(`${API_BASE}/api/assistant/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // Without this the rate limiter sees YOUR server's IP for every visitor.
      ...(clientIp ? { 'X-Forwarded-For': clientIp } : {}),
    },
    body: await req.text(),
  });

  // Pass the status through untouched — the client depends on 429 vs the rest.
  return new NextResponse(await upstream.text(), {
    status: upstream.status,
    headers: { 'Content-Type': 'application/json' },
  });
}
```

This works on dev because `TRUST_FORWARDED_HEADERS` defaults to `true` there, so the backend
honours `X-Forwarded-For`. On production it defaults to `false`, and the proxy would silently
collapse every visitor into one bucket again.

> **Security note for the backend team.** With `TRUST_FORWARDED_HEADERS=true` and the API
> publicly reachable, any caller can set `X-Forwarded-For` themselves and mint a fresh
> rate-limit bucket per request — bypassing the throttle on the one endpoint that costs money
> per call. That is a pre-existing dev setting, not something the assistant introduced, but it
> matters more now. Terminating TLS at a proxy that *overwrites* the header (rather than
> appending) is the fix.

---

## 8. Rendering rules

**Never use `dangerouslySetInnerHTML` for `reply`.** It is plain text produced by a language
model. Render it as a string child, or assign with `textContent` in non-React code. Passing it
through raw HTML turns a model-generated string into an XSS vector.

Three more rules:

- **Preserve line breaks with CSS**, not markup — `white-space: pre-wrap`. The assistant writes
  plain prose with no markdown, so nothing needs parsing.
- **Link cards by `slug`**, not `id`. The `id` is for analytics and dedupe.
- **One request in flight at a time.** A reply takes a few seconds and there is no streaming, so
  a typing indicator carries the wait. Disable the input while busy.

---

## 9. Errors and limits

### Status codes are not yet what they should be

The backend's `GlobalExceptionHandler` is a plain `@RestControllerAdvice` with a catch-all
`@ExceptionHandler(Exception.class)`, and does not extend Spring's
`ResponseEntityExceptionHandler`. Spring runs controller advice *before* its status-exception
resolver, so several conditions that should be `404`/`429`/`503` currently surface as **`500`**
with `"An unexpected error occurred"`.

This is pre-existing repo-wide behaviour — `ProductService` and `BannerService` are affected the
same way. A fix is queued.

**Build defensively:** treat any non-2xx that isn't `429` as one generic recoverable failure.
Do not branch on `404` vs `503` today.

| Condition | Intended | Actual today | Body shape |
|---|---|---|---|
| Success | `200` | `200` ✅ | `{ statusCode, message, data }` |
| Empty / >1000-char message | `400` | `400` ✅ | `{ statusCode, message, data: null }` |
| Rate limit exceeded | `429` | `429` ✅ | `{ success, message, retryAfterSeconds }` |
| Unknown `conversationId` | `404` | ⚠️ `500` | `{ statusCode: 500, message: "An unexpected error occurred" }` |
| Conversation / daily cap hit | `429` | ⚠️ `500` | same as above |
| Assistant switched off | `503` | ⚠️ `500` | same as above |

Validation and throttling both work correctly: `@Valid` has its own handler, and the rate limiter
is a servlet filter that writes its response before controller advice ever runs. That is also why
`429` has a different body shape from everything else.

### Limits

| Limit | Value | Scope |
|---|---|---|
| Chat requests | **12 / minute** | Per IP, on `/chat` only. `/status` sits in the normal public tier, so page loads don't spend chat budget. |
| Messages per conversation | **40** | Then call `reset()` to start a new one. |
| New conversations | **20 / day** | Per `visitorId`. Omitting it skips this cap but not the per-IP one. |
| Message length | **1000 chars** | Enforce client-side with a counter. |

> **`Retry-After` is invisible to JavaScript.** CORS exposes only `Authorization` and
> `Set-Cookie`, so `res.headers.get('Retry-After')` returns `null` cross-origin even though the
> header is sent. Read `retryAfterSeconds` from the `429` JSON body — the client above does.

---

## 10. Known issues

### Open — degenerate tail in some replies

Intermittently the `reply` string continues past the real answer with corrupted scaffolding
text. Observed once as:

```
…shopping for today?ك ⟪7 tokens⟫Yourticket has been escalated. reply in json only.
productIds is required. ⟫UPDATE: The customer has just typed:
```

Root-cause work is in progress. The leading explanation is that the prompt's trailing
`<customer_message>` block invites the model to continue the transcript, and the server's HTML
stripper then mangles what it produced.

**Backend fix — no frontend change needed.** If you want to guard the UI meanwhile, cut the
bubble at the first occurrence of `⟪`.

### Not built yet

- **Streaming.** Replies arrive whole; budget a few seconds of typing indicator. SSE is the
  planned next step and will not change this request contract.
- **Order-aware answers.** The assistant has no access to accounts, orders or tracking — it sets
  `escalate: true` instead. Sending a customer JWT is harmless (it gets recorded against the
  transcript) but changes no answer.

### Verified working on dev

Scope restriction (off-topic questions return `inScope: false`), live catalog retrieval,
multi-turn conversation state, product-card resolution, and the guard that drops any product id
the server didn't actually retrieve.

---

## 11. Ship checklist

- [ ] `https://v2.buyology.online` added to `CORS_ALLOWED_ORIGINS`; app container restarted
- [ ] `NEXT_PUBLIC_ASSISTANT_API_BASE` set in `.env.local` and in the deploy environment
- [ ] Widget is a Client Component; chat is **not** called from a Server Component or Server Action
- [ ] Launcher hidden while `enabled === null` and when `enabled === false`
- [ ] `conversationId` in `sessionStorage`, sent on every message after the first
- [ ] `visitorId` in `localStorage`, shared with the analytics beacon
- [ ] Reply rendered as a string child with `white-space: pre-wrap` — never `dangerouslySetInnerHTML`
- [ ] Card fields accessed optionally; missing `price` / `imageUrl` / `currency` handled
- [ ] `429` handled from the body's `retryAfterSeconds`, input locked until it elapses
- [ ] Every other non-2xx shows one generic recoverable message
- [ ] Input capped at 1000 chars; one request in flight at a time
- [ ] `escalate: true` surfaces a real handoff to your support channel
- [ ] `language` passed through from the storefront locale so Arabic visitors get Arabic replies
