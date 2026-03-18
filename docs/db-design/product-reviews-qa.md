# Product Reviews & Q/A — Database Design

## 1. Overview

This document describes the database design for two user-generated content features on the product detail page:

**Product Reviews** — Authenticated users can post a star-rated review (1–5) with an optional title, body text, and attached media (photos or videos). Other users can vote whether a review was helpful. Admins can post a single official reply to any review. A denormalized stats row per product keeps the average rating and histogram instantly available without expensive aggregates on the hot read path.

**Product Q&A** — Any authenticated user can ask a question about a product. Admin users provide one canonical answer per question. Other users can mark a question as helpful to surface the most relevant ones first. Both questions and answers go through a moderation queue before appearing publicly.

Both features share a common moderation workflow (`PENDING → APPROVED / REJECTED`), soft-delete semantics, and the UUID + Instant timestamp conventions used throughout the rest of the codebase.

---

## 2. Entity-Relationship Diagram

```
products (existing)
  │
  ├──< product_reviews          (product_id FK, user_id FK)
  │         ├──< product_review_media       (review_id FK)
  │         ├──< product_review_votes       (review_id + user_id  composite PK)
  │         └──< product_review_replies     (review_id  UNIQUE FK — 1 per review)
  │
  ├──1  product_review_stats    (product_id  PK = FK — shared key)
  │
  └──< product_questions        (product_id FK, user_id FK)
            ├──< product_question_answers   (question_id UNIQUE FK — 1 per question)
            └──< product_question_votes     (question_id + user_id  composite PK)

users (existing)
  ├──< product_reviews           (user_id)
  ├──< product_review_votes      (user_id)
  ├──< product_review_replies    (admin_id)
  ├──< product_questions         (user_id)
  ├──< product_question_answers  (admin_id)
  └──< product_question_votes    (user_id)
```

---

## 3. Table Descriptions

### 3.1 `product_reviews`

**Purpose:** Core review record — one entry per user per product.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | NO | gen | Primary key |
| `product_id` | UUID | NO | — | FK → products |
| `user_id` | UUID | NO | — | FK → users |
| `rating` | SMALLINT | NO | — | 1–5, validated at app + DB |
| `title` | VARCHAR(255) | YES | — | Optional headline |
| `body` | TEXT | YES | — | Full review text |
| `is_verified_purchase` | BOOLEAN | NO | false | Set by service after order lookup |
| `order_item_id` | UUID | YES | — | Raw UUID — loose link to order item |
| `status` | VARCHAR(20) | NO | PENDING | ModerationStatus enum |
| `moderated_by` | UUID | YES | — | Raw UUID — no FK (admin service) |
| `moderated_at` | TIMESTAMPTZ | YES | — | When moderation decision was made |
| `rejection_reason` | TEXT | YES | — | Populated on REJECTED status |
| `helpful_count` | INT | NO | 0 | Denormalized — incremented by service |
| `not_helpful_count` | INT | NO | 0 | Denormalized — decremented by service |
| `deleted_at` | TIMESTAMPTZ | YES | — | Soft delete |
| `created_at` | TIMESTAMPTZ | NO | NOW() | Immutable |
| `updated_at` | TIMESTAMPTZ | NO | NOW() | Updated on every change |

**Unique constraint:** `(product_id, user_id)` — enforces one review per user per product at the database level, preventing race conditions that app-level checks cannot guarantee.

**Indexes:**

| Index | Columns | Purpose |
|---|---|---|
| `idx_pr_product_created` | `product_id, created_at DESC` | Primary listing query on product page (newest first) |
| `idx_pr_product_rating` | `product_id, rating` | Filter/sort by star rating |
| `idx_pr_status_created` | `status, created_at` | Admin moderation queue ordered by submission time |

> **Note on partial indexes:** For maximum read performance in production, apply these partial indexes manually after initial schema creation. Hibernate's `@Index` cannot express `WHERE` clauses:
> ```sql
> CREATE INDEX CONCURRENTLY idx_pr_product_approved
>   ON product_reviews(product_id, created_at DESC)
>   WHERE status = 'APPROVED' AND deleted_at IS NULL;
>
> CREATE INDEX CONCURRENTLY idx_pr_product_rating_approved
>   ON product_reviews(product_id, rating)
>   WHERE status = 'APPROVED' AND deleted_at IS NULL;
> ```

**Design rationale:** The `UNIQUE (product_id, user_id)` constraint is the single most important integrity rule — without it, a race condition between two simultaneous POSTs could create duplicate reviews even if the service layer checks first. `orderItemId` is stored as a raw UUID without a foreign key constraint intentionally: the order management may live in a separate microservice or not yet exist; a hard FK would block deployment. `moderatedBy` follows the same reasoning.

---

### 3.2 `product_review_media`

**Purpose:** Stores URLs of photos or videos attached to a review.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | NO | gen | Primary key |
| `review_id` | UUID | NO | — | FK → product_reviews (CASCADE DELETE) |
| `url` | TEXT | NO | — | CDN or object storage URL |
| `media_type` | VARCHAR(10) | NO | — | IMAGE or VIDEO |
| `sort_order` | SMALLINT | NO | 0 | Display order within review |
| `created_at` | TIMESTAMPTZ | NO | NOW() | Immutable (rows are never updated) |

**Index:** `idx_prm_review_id` on `review_id` — fetched in bulk per review.

**Design rationale:** Separated from the main review row to keep `product_reviews` narrow and cache-friendly. A review row with 8 media URLs embedded in columns would inflate the row size and slow down list queries that never display media. Media rows are immutable once created — no `updated_at` needed.

---

### 3.3 `product_review_votes`

**Purpose:** Records each user's helpful/not-helpful vote on a review. Prevents double-voting.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `review_id` | UUID | NO | — | Part of composite PK + FK → product_reviews |
| `user_id` | UUID | NO | — | Part of composite PK + FK → users |
| `is_helpful` | BOOLEAN | NO | — | true = helpful, false = not helpful |
| `created_at` | TIMESTAMPTZ | NO | NOW() | Immutable |

**Primary key:** `(review_id, user_id)` — composite, acts as both the uniqueness constraint and the clustered access path.

**Design rationale:** Using a composite PK instead of a surrogate UUID + separate unique constraint eliminates a redundant index (the PK index IS the uniqueness index). It also makes the "did this user vote?" lookup a single index seek on the PK. Votes are immutable — a user can only update their vote by deleting and re-inserting.

---

### 3.4 `product_review_replies`

**Purpose:** A single official admin response to a review, editable after posting.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | NO | gen | Primary key |
| `review_id` | UUID | NO | — | FK → product_reviews (UNIQUE — 1 reply per review) |
| `admin_id` | UUID | NO | — | FK → users (must be ADMIN type, enforced at app layer) |
| `body` | TEXT | NO | — | Reply content |
| `created_at` | TIMESTAMPTZ | NO | NOW() | Immutable |
| `updated_at` | TIMESTAMPTZ | NO | NOW() | Updated when admin edits reply |

**Unique constraint:** `uq_prr_review_id` on `review_id` — guarantees exactly one reply per review at the DB level.

**Design rationale:** The `UNIQUE` constraint on `review_id` is the key design choice. Without it, a race condition between two admin sessions could insert two replies for the same review. The constraint makes the DB the authority, not just the application.

---

### 3.5 `product_review_stats`

**Purpose:** Pre-aggregated review statistics per product — eliminates `AVG` + `COUNT` queries on the hot product page read path.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `product_id` | UUID | NO | — | PK and FK → products (shared key) |
| `total_reviews` | INT | NO | 0 | Count of APPROVED non-deleted reviews |
| `average_rating` | NUMERIC(3,2) | NO | 0.00 | Pre-computed average |
| `rating_1_count` | INT | NO | 0 | Histogram bucket — 1 star |
| `rating_2_count` | INT | NO | 0 | Histogram bucket — 2 stars |
| `rating_3_count` | INT | NO | 0 | Histogram bucket — 3 stars |
| `rating_4_count` | INT | NO | 0 | Histogram bucket — 4 stars |
| `rating_5_count` | INT | NO | 0 | Histogram bucket — 5 stars |
| `updated_at` | TIMESTAMPTZ | NO | NOW() | When stats were last recomputed |

**Design rationale:** The product detail page is the highest-traffic endpoint. Doing `SELECT AVG(rating), COUNT(*) FROM product_reviews WHERE product_id = ? AND status = 'APPROVED'` on every request becomes expensive once a product accumulates thousands of reviews. The stats table is updated asynchronously (via application event or scheduled job) after each review is approved or deleted. `NUMERIC(3,2)` avoids floating-point rounding errors (`3.67` not `3.6700000000000004`). The table uses `product_id` as both PK and FK (shared-key/identifying relationship) — no wasted UUID column.

---

### 3.6 `product_questions`

**Purpose:** Questions submitted by users about a specific product.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | NO | gen | Primary key |
| `product_id` | UUID | NO | — | FK → products |
| `user_id` | UUID | NO | — | FK → users |
| `body` | TEXT | NO | — | The question text |
| `status` | VARCHAR(20) | NO | PENDING | ModerationStatus enum |
| `moderated_by` | UUID | YES | — | Raw UUID — no FK |
| `moderated_at` | TIMESTAMPTZ | YES | — | When decision was made |
| `helpful_count` | INT | NO | 0 | Denormalized — how many users found this question relevant |
| `deleted_at` | TIMESTAMPTZ | YES | — | Soft delete |
| `created_at` | TIMESTAMPTZ | NO | NOW() | Immutable |
| `updated_at` | TIMESTAMPTZ | NO | NOW() | Updated on moderation changes |

**Indexes:**

| Index | Columns | Purpose |
|---|---|---|
| `idx_pq_product_helpful_created` | `product_id, helpful_count DESC, created_at DESC` | Most helpful questions first on product page |
| `idx_pq_status_created` | `status, created_at` | Admin moderation queue |

> **Partial index to add manually in production:**
> ```sql
> CREATE INDEX CONCURRENTLY idx_pq_product_approved
>   ON product_questions(product_id, helpful_count DESC, created_at DESC)
>   WHERE status = 'APPROVED' AND deleted_at IS NULL;
> ```

**Design rationale:** Unlike reviews, there is no `UNIQUE (product_id, user_id)` constraint on questions — a user may legitimately ask multiple different questions about the same product. Questions are sorted by `helpful_count DESC` to surface the most relevant ones first; a pure chronological feed would bury useful questions under newer, less-voted ones.

---

### 3.7 `product_question_answers`

**Purpose:** A single admin-authored answer to a question.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | NO | gen | Primary key |
| `question_id` | UUID | NO | — | FK → product_questions (UNIQUE — 1 answer per question) |
| `admin_id` | UUID | NO | — | FK → users |
| `body` | TEXT | NO | — | Answer content |
| `is_active` | BOOLEAN | NO | true | Toggle visibility without deleting |
| `deleted_at` | TIMESTAMPTZ | YES | — | Hard soft-delete for audit trail |
| `created_at` | TIMESTAMPTZ | NO | NOW() | Immutable |
| `updated_at` | TIMESTAMPTZ | NO | NOW() | Updated on admin edits |

**Unique constraint:** `uq_pqa_question_id` on `question_id` — one canonical answer per question.

**Design rationale:** The `UNIQUE` constraint on `question_id` gives the business rule a DB-level guarantee. `isActive` and `deletedAt` serve different purposes: `isActive = false` is a lightweight toggle for temporary hiding (e.g., answer under revision) without any audit trail implications; `deletedAt` is the permanent soft-delete that preserves the row for compliance.

---

### 3.8 `product_question_votes`

**Purpose:** Records that a user found a question helpful. Prevents duplicate votes.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `question_id` | UUID | NO | — | Part of composite PK + FK → product_questions |
| `user_id` | UUID | NO | — | Part of composite PK + FK → users |
| `created_at` | TIMESTAMPTZ | NO | NOW() | Immutable |

**Primary key:** `(question_id, user_id)` — composite.

**Design rationale:** Unlike review votes, question votes have no `is_helpful` column — the vote's existence implies the question was helpful (users cannot mark a question as "not helpful"). This simplifies the model. The composite PK is the same pattern as `product_review_votes`.

---

## 4. Key Design Decisions

| Decision | Rationale |
|---|---|
| **`product_review_stats` separate table** | Avoid `AVG` + `COUNT` aggregates on every product page load. Stats are updated asynchronously after review approval events. Single-row lock on stats does not block product reads. |
| **Partial indexes (manual, post-generation)** | JPA `@Index` cannot express `WHERE` clauses. Full indexes are created by Hibernate on startup; optimized partial indexes (`WHERE status = 'APPROVED' AND deleted_at IS NULL`) must be applied manually. They are smaller in size and faster to scan than full indexes because they only include rows the public-facing query actually reads. |
| **`UNIQUE (product_id, user_id)` on reviews** | Enforced at DB level — not just app logic. Prevents the TOCTOU race condition where two concurrent requests both pass the service-layer duplicate check before either one commits. |
| **`UNIQUE (review_id)` on replies, `UNIQUE (question_id)` on answers** | Guarantees 1-reply-per-review and 1-answer-per-question at the storage layer. No amount of application-level locking fully replaces this. |
| **Composite PK on vote tables** | Zero-cost uniqueness — the PK IS the index. Eliminates a redundant unique index that a surrogate PK + separate constraint would require. |
| **Denormalized `helpful_count` / `not_helpful_count`** | Counting votes on every render is expensive. Counters are incremented/decremented by the service layer on each vote insert/delete. Acceptable eventual consistency (a count off by ±1 for milliseconds is invisible to users). |
| **`order_item_id` as raw UUID (no FK)** | Order management may be a separate microservice. A hard DB foreign key to `order_items` would create a cross-service coupling and block independent deployment of either service. |
| **`moderated_by` as raw UUID (no FK)** | Same reasoning — admin tooling and audit systems may be external services. |
| **`isActive` + `deletedAt` both on `product_question_answers`** | `isActive` is a lightweight operational toggle (temporarily hide while revising). `deletedAt` is the permanent, auditable soft-delete. They serve different operational needs. |
| **`ModerationStatus` enum shared by Review and Question** | Both features share the identical moderation states and workflow. A single enum in `review/domain/enums/` avoids duplication. |
| **`averageRating` as `NUMERIC(3,2)`** | Exact decimal arithmetic — avoids floating-point representation errors (e.g., `3.67` stored as `3.6700000000000004` in `FLOAT`). Precision 3, scale 2 covers `0.00` to `9.99`. |
| **`orderItemId` enables Verified Purchase badge** | The service sets `isVerifiedPurchase = true` after asynchronously confirming the reviewer has a completed order containing this product. The `orderItemId` stores the evidence link. |

---

## 5. Scaling Path

### Read scalability
- **Read replicas:** All product-page queries (reviews list, Q&A list, stats) are read-only. Route them to a PostgreSQL read replica. Zero application-code change required with Spring's `@Transactional(readOnly = true)`.
- **Redis cache for stats:** Promote `product_review_stats` rows to a Redis hash keyed by `product_id`. TTL of 60 seconds. The service invalidates/updates the cache when a review is approved. This eliminates all DB reads for the average-rating widget.
- **Redis cache for Q&A and reviews:** Cache the first page of approved reviews per product (sorted by `created_at DESC`) with a short TTL. Invalidate on new approval.

### Write scalability
- **Async vote counter increments:** Under high concurrency, `UPDATE product_reviews SET helpful_count = helpful_count + 1` rows can contend. Replace with a Redis increment (`INCR review:{id}:helpful`) and flush to Postgres periodically via a scheduled job.
- **Async stats updates:** Trigger stats recomputation via an application event (`ReviewApprovedEvent`) handled asynchronously by a `@TransactionalEventListener`. Decouples the approval API response time from the stats computation.

### Storage scalability
- **Table partitioning:** Both `product_reviews` and `product_questions` can be range-partitioned by `created_at` (monthly partitions) using PostgreSQL declarative partitioning. Queries filtered by `product_id` still benefit from partition pruning when combined with a `created_at` range filter. Zero application-code change.
- **Media URL storage:** `product_review_media.url` stores only the CDN URL — the actual bytes live in object storage (S3, GCS, etc.). This keeps the row size tiny regardless of image count.
- **Archival policy:** Reviews and questions older than N years with `deleted_at IS NOT NULL` can be moved to a cold-storage partition or archived table without affecting the live read path.

### Operational
- **Partial indexes in production:** After initial schema creation, apply the partial indexes documented in each table section using `CREATE INDEX CONCURRENTLY` (no table lock) during a low-traffic window.
- **Full-text search:** If search-by-review-content is needed, add a `tsvector` GIN index on `product_reviews(body)` or sync approved reviews to Elasticsearch for richer search capabilities.
