# Payment System — Database Design

## 1. Overview

This document describes the database design for the payment service, built around a **Paymob** integration that supports three payment methods: direct card payment, Tabby (BNPL), and Tamara (BNPL).

The design is intentionally separated into multiple focused tables rather than one fat `payments` table. The reason is that a payment is not a single atomic event — it is a **multi-step workflow** (auth → provider order → payment key → transaction → webhook → optional refund), and each step has its own lifecycle, failure modes, and audit requirements. Collapsing all of this into one table would make it impossible to cleanly replay failures, debug partial states, or extend the system to a second provider later.

### Core design principles

- **Every raw webhook is stored before it is processed.** If our processing logic crashes or has a bug, we can fix it and replay from the raw log without losing data.
- **Provider credentials are in the database, not hardcoded config.** This allows sandbox/live switching per environment and makes multi-merchant support possible without a code change.
- **Amounts are stored twice: decimal and cents.** Paymob requires integer cents (no decimals). Storing both avoids repeated conversion and removes an entire class of rounding bugs.
- **Soft deletes nowhere.** Payment records are immutable history. Nothing in this schema is deleted — statuses change, refunds are created, but the original transaction row is never mutated past its terminal state.
- **Customer fields are denormalized onto the transaction.** The user's name, email, and phone at the time of payment are snapshotted on the transaction row. If the user later changes their profile, the payment history still reflects what was true at the time of the transaction.

---

## 2. Entity-Relationship Diagram

```
payment_providers
  │
  └──< payment_method_configs         (provider_id FK — one per method type)

app_orders  (orders service — external)
  │
  ├──< payment_provider_orders        (app_order_id — Paymob-side order)
  │
  └──< payment_transactions           (app_order_id — one per attempt)
            │
            ├──< payment_webhook_events   (transaction_id FK — all Paymob callbacks)
            │
            └──< payment_refunds          (transaction_id FK — one or many)
```

`payment_method_configs` is also referenced by `payment_transactions` via `method_config_id` so we always know which exact integration (and iframe) was used for a given transaction.

---

## 3. Table-by-Table Breakdown

### 3.1 `payment_providers`

Stores Paymob account-level credentials. One row per environment (sandbox, live) or per merchant if you ever go multi-tenant.

```sql
CREATE TABLE payment_providers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50)  NOT NULL UNIQUE,  -- 'PAYMOB'
    api_key         TEXT         NOT NULL,          -- encrypt at rest
    hmac_secret     TEXT         NOT NULL,          -- encrypt at rest
    merchant_id     VARCHAR(100),
    base_url        VARCHAR(255) NOT NULL,          -- sandbox vs live URL
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

**Why this exists as a separate table:**
The Paymob authentication flow requires the `api_key` on every request to get a short-lived auth token. Keeping this in the DB (encrypted at rest) rather than in environment variables means:
- You can rotate credentials without a deploy.
- You can toggle sandbox/live per environment by flipping `is_active`.
- When you add a second provider (Stripe, HyperPay), the pattern is already there.

`api_key` and `hmac_secret` **must be encrypted at the column level** (AES-256 or via a secrets manager like AWS Secrets Manager / HashiCorp Vault). Never store them as plaintext.

---

### 3.2 `payment_method_configs`

One row per payment method type. For Paymob this means three rows: `CARD`, `TABBY`, `TAMARA`.

```sql
CREATE TYPE payment_method_type AS ENUM ('CARD', 'TABBY', 'TAMARA');

CREATE TABLE payment_method_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id     UUID         NOT NULL REFERENCES payment_providers(id),
    method_type     payment_method_type NOT NULL,
    integration_id  VARCHAR(100) NOT NULL,          -- Paymob integration ID
    iframe_id       VARCHAR(100),                   -- Card payments only
    currency        CHAR(3)      NOT NULL DEFAULT 'AED',
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    extra_config    JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (provider_id, method_type)
);
```

**Why `integration_id` matters:**
Paymob's Step 3 (generate payment key) requires the `integration_id` of the specific method being used. If you pass the card integration ID when the user selected Tabby, the payment will fail silently or route incorrectly. This table makes it impossible to mix them up — you look up the config by `method_type` and the correct ID is there.

**`iframe_id`** is only relevant for card payments. Paymob card payments render inside an iframe that is identified by this ID. Tabby and Tamara are redirect-based — the user leaves your site entirely — so `iframe_id` is `NULL` for those rows.

**`extra_config` (JSONB):** A safety valve for provider-specific options that don't deserve their own column yet (e.g., Tabby merchant code, Tamara country restrictions). Use sparingly — if a field becomes load-bearing, migrate it to a proper column.

---

### 3.3 `payment_provider_orders`

Maps one of our app orders to a Paymob-side order (Step 2 of the Paymob flow).

```sql
CREATE TABLE payment_provider_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_order_id        UUID         NOT NULL,
    provider_id         UUID         NOT NULL REFERENCES payment_providers(id),
    provider_order_id   VARCHAR(100) NOT NULL UNIQUE,  -- Paymob's order ID
    amount_cents        BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

**Why this is its own table and not a column on `payment_transactions`:**

A Paymob order is created once per checkout attempt at the order level, but a user may go on to make multiple payment attempts (e.g., card declined → try Tabby). Each attempt creates a new `payment_transaction`, but they all share the same `payment_provider_orders` row because the order amount and items do not change between attempts.

If you collapse this into the transaction table, you either duplicate the `provider_order_id` across retry rows (data integrity problem) or you lose the ability to link retries to the same provider order (query problem).

`provider_order_id` has a `UNIQUE` constraint because Paymob guarantees uniqueness and we should enforce it on our side too. Any duplicate would indicate a bug in the integration.

---

### 3.4 `payment_transactions`

The central table. One row per payment attempt, regardless of outcome.

```sql
CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'SUCCESS',
    'FAILED',
    'CANCELLED',
    'REFUNDED',
    'PARTIALLY_REFUNDED'
);

CREATE TABLE payment_transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_order_id            UUID             NOT NULL,
    provider_order_id       UUID             REFERENCES payment_provider_orders(id),
    method_config_id        UUID             NOT NULL REFERENCES payment_method_configs(id),
    method_type             payment_method_type NOT NULL,

    -- amounts
    amount                  NUMERIC(12, 2)   NOT NULL,
    amount_cents            BIGINT           NOT NULL,
    currency                CHAR(3)          NOT NULL,

    -- status
    status                  payment_status   NOT NULL DEFAULT 'PENDING',

    -- Paymob identifiers
    provider_transaction_id VARCHAR(100)     UNIQUE,
    payment_key_token       TEXT,

    -- redirect URL (Tabby / Tamara)
    redirect_url            TEXT,

    -- customer snapshot at time of payment
    customer_id             UUID,
    customer_email          VARCHAR(255),
    customer_phone          VARCHAR(50),
    billing_name            VARCHAR(255),

    -- failure info
    failure_reason          TEXT,
    failure_code            VARCHAR(50),

    metadata                JSONB,
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_transactions_app_order    ON payment_transactions(app_order_id);
CREATE INDEX idx_payment_transactions_status       ON payment_transactions(status);
CREATE INDEX idx_payment_transactions_provider_txn ON payment_transactions(provider_transaction_id);
```

**Status lifecycle:**

```
PENDING
  └─→ PROCESSING       (user redirected to payment page / iframe rendered)
        ├─→ SUCCESS         (webhook received: success=true)
        ├─→ FAILED          (webhook received: success=false)
        └─→ CANCELLED       (user abandoned / timeout)

SUCCESS
  ├─→ REFUNDED             (full refund processed)
  └─→ PARTIALLY_REFUNDED   (one or more partial refunds, total < original)
```

`PENDING` and `PROCESSING` are the only mutable states. Once a transaction reaches `SUCCESS`, `FAILED`, or `CANCELLED`, **the status must never be changed** except by the refund workflow. Any incoming webhook for a terminal-state transaction should be logged in `payment_webhook_events` and ignored, not re-processed.

**`payment_key_token`:** Paymob's payment key is short-lived (~1 hour). Store it so that if the user's browser crashes mid-flow, you can check whether the existing key is still valid before generating a new one. Do not expose this in API responses to the frontend beyond the initial redirect/iframe render.

**`redirect_url`:** For Tabby and Tamara, Paymob returns a URL to redirect the user to the BNPL provider's hosted checkout. Store it on the transaction so if the user's session drops after the key is generated but before the redirect, you can recover without hitting Paymob again.

**`provider_transaction_id`:** This is the ID Paymob sends in the webhook callback. It is `NULL` until the webhook arrives. The `UNIQUE` constraint ensures we cannot accidentally process the same Paymob transaction twice even if the webhook is delivered more than once.

**Indexes:** `app_order_id` is queried constantly (order detail page shows payment status). `provider_transaction_id` is the lookup key for every incoming webhook. Both need indexes. The `status` index supports admin dashboards querying pending or failed transactions.

---

### 3.5 `payment_webhook_events`

A raw, append-only log of every HTTP callback Paymob sends to our server.

```sql
CREATE TABLE payment_webhook_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id     UUID         REFERENCES payment_providers(id),
    transaction_id  UUID         REFERENCES payment_transactions(id),
    event_type      VARCHAR(100),
    provider_txn_id VARCHAR(100),
    hmac_valid      BOOLEAN      NOT NULL,
    payload         JSONB        NOT NULL,
    processed       BOOLEAN      NOT NULL DEFAULT false,
    processed_at    TIMESTAMPTZ,
    error           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_txn ON payment_webhook_events(transaction_id);
```

**The golden rule of webhooks: store first, process second.**

The very first thing your webhook handler does is write the raw `payload` and `hmac_valid` to this table. Only then do you process it. This gives you:

1. **Idempotency:** Before processing, check if a webhook with this `provider_txn_id` was already processed successfully. If yes, return `200 OK` immediately without reprocessing.
2. **Replay:** If your processing logic had a bug (wrong status mapping, missed edge case), you can fix the code and re-run every unprocessed or errored webhook from this table.
3. **Audit trail:** Customer disputes, chargebacks, and finance reconciliation all require proof of exactly what Paymob told us and when.

**`hmac_valid`:** Store whether the HMAC check passed. If it failed, still write the row (it is evidence of a potential attack or a Paymob misconfiguration) but set `processed = false` and never update the linked transaction. Alert on `hmac_valid = false`.

**`transaction_id` can be `NULL`** on insert if the webhook arrives before we can look up the transaction (e.g., the `provider_txn_id` in the payload does not match any known transaction yet). This should not happen in normal flow but the schema must handle it gracefully.

---

### 3.6 `payment_refunds`

One row per refund operation. A single transaction can have multiple partial refunds.

```sql
CREATE TYPE refund_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED');

CREATE TABLE payment_refunds (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID           NOT NULL REFERENCES payment_transactions(id),
    amount              NUMERIC(12, 2) NOT NULL,
    amount_cents        BIGINT         NOT NULL,
    currency            CHAR(3)        NOT NULL,
    reason              TEXT,
    status              refund_status  NOT NULL DEFAULT 'PENDING',
    provider_refund_id  VARCHAR(100),
    refunded_by         UUID,
    notes               TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
```

**Why refunds are a separate table and not a column:**

A column approach (e.g., `is_refunded BOOLEAN`, `refunded_amount NUMERIC`) breaks immediately the moment you have a partial refund, and it breaks again when you have a second partial refund. A table with one row per refund operation handles all cases uniformly and gives you a complete refund history for reconciliation.

**Partial refund guard (enforce in application layer):**
Before inserting a new refund row, query:
```sql
SELECT COALESCE(SUM(amount), 0)
FROM payment_refunds
WHERE transaction_id = $1 AND status = 'SUCCESS';
```
Ensure `existing_refunded + new_refund_amount <= original_transaction_amount`. This check belongs in the service layer, not a DB trigger, so it participates in the same transaction as the insert and is testable.

**`refunded_by`:** The UUID of the admin user who initiated the refund. Essential for audit. If the refund is triggered automatically (e.g., order cancellation flow), record a system service account UUID.

---

## 4. What Is NOT in This Schema (and Why)

| Thing | Why it is excluded |
|---|---|
| Product / order item details | The payment service does not own order data. It receives `app_order_id` and amount. Order item details live in the order service and are passed to Paymob in the API call body, not persisted here. |
| User addresses | Billing address is passed to Paymob at payment key generation time. It is not stored here — the user profile service owns it. We only snapshot the customer's name, email, and phone as a lightweight audit anchor. |
| Paymob auth tokens | Auth tokens expire in ~1 hour. They are obtained at runtime, used, and discarded. Caching them in Redis (keyed by `provider_id`) is appropriate. Persisting them to the DB is not. |
| Payment method card details | PCI-DSS. Never store raw card numbers, CVVs, or expiry dates. Paymob's hosted iframe handles this — it never touches your server. |

---

## 5. Operational Notes

### Idempotency on retry
If a user retries a failed payment, create a **new** `payment_transactions` row. Never reuse or mutate the failed one. The failed row is history.

### Currency handling
Always work in cents (integers) when communicating with Paymob. The `amount` (decimal) column is for human display and accounting exports only. The `amount_cents` column is the source of truth for all provider communication.

### Reconciliation
At end-of-day, reconciliation should join:
```sql
payment_transactions pt
  JOIN payment_refunds pr ON pr.transaction_id = pt.id
```
and compare net settled amounts against Paymob's transaction report. Discrepancies between `pt.amount_cents` and the Paymob report indicate a webhook was missed or a status was not updated correctly.

### Adding a new payment method
1. Insert a new row into `payment_method_configs` with the new `integration_id`.
2. Add the new value to the `payment_method_type` enum (requires a migration).
3. No other schema changes needed. The transaction and webhook tables are already agnostic to method type.

### Adding a second provider (e.g., Stripe)
1. Insert a new row into `payment_providers`.
2. Insert method config rows for that provider.
3. The transaction table already carries `method_config_id` which links back to the provider through the config. No new columns needed on the transaction table.
