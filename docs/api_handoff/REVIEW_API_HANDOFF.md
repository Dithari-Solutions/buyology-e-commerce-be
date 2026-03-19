# Review & Q&A API Handoff

**Base URL:** `https://api-dev.dithari.com` (dev) / your production base
**Content-Type:** `application/json`
**Auth:** Not enforced at the gateway layer — pass user/admin IDs in the request body.

All responses follow this envelope:

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

---

## Table of Contents

1. [Reviews — Public APIs](#1-reviews--public-apis)
2. [Reviews — Admin APIs](#2-reviews--admin-apis)
3. [Questions & Answers — Public APIs](#3-questions--answers--public-apis)
4. [Questions & Answers — Admin APIs](#4-questions--answers--admin-apis)
5. [Data Models](#5-data-models)
6. [Moderation Status](#6-moderation-status)
7. [Integration Notes](#7-integration-notes)

---

## 1. Reviews — Public APIs

### 1.1 Get approved reviews for a product

```
GET /api/reviews/product/{productId}?page=0&size=10
```

**Path:** `productId` — UUID of the product
**Query params:** `page` (default 0), `size` (default 10)
**Response 200:**

```json
{
  "statusCode": 200,
  "message": "Reviews fetched successfully",
  "data": [
    {
      "id": "uuid",
      "productId": "uuid",
      "userId": "uuid",
      "userFirstName": "John",
      "userLastName": "Doe",
      "rating": 4,
      "title": "Great product!",
      "body": "Really satisfied with the build quality.",
      "isVerifiedPurchase": true,
      "status": "APPROVED",
      "helpfulCount": 12,
      "notHelpfulCount": 1,
      "media": [
        {
          "id": "uuid",
          "url": "https://cdn.example.com/review/img1.jpg",
          "mediaType": "IMAGE",
          "sortOrder": 0,
          "createdAt": "2026-01-15T10:00:00Z"
        }
      ],
      "reply": {
        "id": "uuid",
        "adminId": "uuid",
        "adminFirstName": "Support",
        "adminLastName": "Team",
        "body": "Thank you for your feedback!",
        "createdAt": "2026-01-16T09:00:00Z",
        "updatedAt": "2026-01-16T09:00:00Z"
      },
      "createdAt": "2026-01-15T10:00:00Z",
      "updatedAt": "2026-01-15T10:00:00Z"
    }
  ]
}
```

> `reply` is `null` when no admin reply exists. `media` is an empty array `[]` when no media is attached.

---

### 1.2 Get review stats for a product

```
GET /api/reviews/product/{productId}/stats
```

**Response 200:**

```json
{
  "statusCode": 200,
  "message": "Review stats fetched successfully",
  "data": {
    "productId": "uuid",
    "totalReviews": 87,
    "averageRating": "4.23",
    "rating1Count": 3,
    "rating2Count": 5,
    "rating3Count": 10,
    "rating4Count": 30,
    "rating5Count": 39,
    "updatedAt": "2026-03-18T14:00:00Z"
  }
}
```

> `averageRating` is a string-serialized decimal (e.g. `"4.23"`). Parse with `parseFloat()`.
> Returns zeros for all fields when no approved reviews exist.

---

### 1.3 Get a single approved review

```
GET /api/reviews/{reviewId}
```

**Response 200:** Same shape as a single item from 1.1
**Response 404:** Review not found or not approved

---

### 1.4 Submit a review

```
POST /api/reviews
Content-Type: multipart/form-data
```

Send as two parts:

| Part | Type | Required | Notes |
|---|---|---|---|
| `request` | JSON string | Yes | Review metadata (see fields below) |
| `images` | File(s) | No | Up to **2** image files (JPEG, PNG, WEBP, etc.) |

**`request` part — JSON fields:**

| Field | Required | Notes |
|---|---|---|
| `productId` | Yes | |
| `userId` | Yes | Logged-in user's ID |
| `rating` | Yes | 1–5 |
| `title` | No | Max 255 chars |
| `body` | No | Free text |
| `orderItemId` | No | Providing this sets `isVerifiedPurchase = true` |

**Example (JavaScript `fetch`):**

```js
const formData = new FormData();
formData.append('request', JSON.stringify({
  productId: "uuid",
  userId: "uuid",
  rating: 4,
  title: "Great product!",
  body: "Exceeded my expectations.",
  orderItemId: "uuid"   // optional
}));
formData.append('images', file1);  // optional, max 2
formData.append('images', file2);  // optional

fetch('/api/reviews', { method: 'POST', body: formData });
```

**Response 201:**

```json
{
  "statusCode": 201,
  "message": "Review submitted successfully and is pending moderation",
  "data": { ...review object with status: "PENDING"... }
}
```

**Response 400:** More than 2 images supplied, or a non-image file was uploaded
**Response 409:** User has already reviewed this product

---

### 1.5 Update a pending review

```
PUT /api/reviews/{reviewId}
```

> Only works while `status` is `PENDING`. Once approved/rejected, the review is locked.

**Body (all fields optional):**

```json
{
  "rating": 5,
  "title": "Even better than expected!",
  "body": "Updated opinion after 2 weeks of use."
}
```

**Response 200:** Updated review
**Response 400:** Review is not in PENDING status

---

### 1.6 Delete a review

```
DELETE /api/reviews/{reviewId}
```

**Response 200:** `{ "statusCode": 200, "message": "Review deleted successfully", "data": null }`

---

### 1.7 Vote on a review

```
POST /api/reviews/{reviewId}/vote
```

**Body:**

```json
{
  "userId": "uuid",
  "isHelpful": true
}
```

> Re-voting with the opposite `isHelpful` changes the existing vote.
> Voting the same way twice returns **409 Conflict**.

**Response 200:** `{ "statusCode": 200, "message": "Vote recorded successfully", "data": null }`

---

### 1.8 Remove vote from a review

```
DELETE /api/reviews/{reviewId}/vote?userId={userId}
```

**Response 200:** `{ "statusCode": 200, "message": "Vote removed successfully", "data": null }`

---

## 2. Reviews — Admin APIs

Base path: `/api/admin/reviews`

---

### 2.1 List all reviews

```
GET /api/admin/reviews?status=PENDING&page=0&size=20
```

| Param | Required | Values |
|---|---|---|
| `status` | No | `PENDING`, `APPROVED`, `REJECTED` — omit for all non-deleted |
| `page` | No | default 0 |
| `size` | No | default 20 |

**Response 200:** List of review objects (same shape as public, includes all statuses and `rejectionReason`)

---

### 2.2 Get a review by ID

```
GET /api/admin/reviews/{reviewId}
```

> Returns deleted reviews too (use for audit).

---

### 2.3 Moderate a review

```
PATCH /api/admin/reviews/{reviewId}/moderate
```

**Body:**

```json
{
  "status": "APPROVED",
  "moderatedBy": "admin-uuid",
  "rejectionReason": null
}
```

| Field | Required | Notes |
|---|---|---|
| `status` | Yes | `APPROVED` or `REJECTED` |
| `moderatedBy` | Yes | Admin user UUID |
| `rejectionReason` | Conditional | Required when `status = REJECTED` |

**Response 200:** Updated review with new status
**Response 400:** Rejection reason missing

> Approving/rejecting a review **automatically recalculates** the product's review stats (`averageRating`, `ratingXCount`, `totalReviews`).

---

### 2.4 Delete a review (admin)

```
DELETE /api/admin/reviews/{reviewId}
```

> Soft-deletes the review. Also triggers stats recalculation if the review was `APPROVED`.

---

### 2.5 Add admin reply to a review

```
POST /api/admin/reviews/{reviewId}/reply
```

**Body:**

```json
{
  "adminId": "admin-uuid",
  "body": "Thank you for your review! We're glad you enjoyed the product."
}
```

> One reply per review. Returns **409** if a reply already exists — use PUT to update.

**Response 201:** Updated review object with the new reply embedded

---

### 2.6 Update admin reply

```
PUT /api/admin/reviews/{reviewId}/reply
```

**Body:**

```json
{
  "body": "We apologize for any inconvenience and will look into this."
}
```

**Response 200:** Updated review with updated reply

---

### 2.7 Delete admin reply

```
DELETE /api/admin/reviews/{reviewId}/reply
```

**Response 200:** `{ "statusCode": 200, "message": "Reply deleted successfully", "data": null }`

---

## 3. Questions & Answers — Public APIs

Base path: `/api/questions`

---

### 3.1 Get approved questions for a product

```
GET /api/questions/product/{productId}?page=0&size=10
```

Ordered by `helpfulCount DESC`, then `createdAt DESC`.

**Response 200:**

```json
{
  "statusCode": 200,
  "message": "Questions fetched successfully",
  "data": [
    {
      "id": "uuid",
      "productId": "uuid",
      "userId": "uuid",
      "userFirstName": "Jane",
      "userLastName": "Smith",
      "body": "Does this support 4K output via HDMI?",
      "status": "APPROVED",
      "helpfulCount": 15,
      "answer": {
        "id": "uuid",
        "adminId": "uuid",
        "adminFirstName": "Support",
        "adminLastName": "Team",
        "body": "Yes, it supports HDMI 2.0 for 4K at 60Hz.",
        "isActive": true,
        "createdAt": "2026-01-20T12:00:00Z",
        "updatedAt": "2026-01-20T12:00:00Z"
      },
      "createdAt": "2026-01-19T08:00:00Z",
      "updatedAt": "2026-01-19T08:00:00Z"
    }
  ]
}
```

> `answer` is `null` when no active answer exists.
> Only answers with `isActive = true` are included in public responses.

---

### 3.2 Get a single approved question

```
GET /api/questions/{questionId}
```

**Response 200:** Single question with answer
**Response 404:** Question not found or not approved

---

### 3.3 Ask a question

```
POST /api/questions
```

**Body:**

```json
{
  "productId": "uuid",
  "userId": "uuid",
  "body": "Does this laptop support fingerprint login?"
}
```

**Response 201:**

```json
{
  "statusCode": 201,
  "message": "Question submitted successfully and is pending moderation",
  "data": { ...question object with status: "PENDING"... }
}
```

> Users can ask multiple questions per product (no uniqueness constraint).

---

### 3.4 Delete a question

```
DELETE /api/questions/{questionId}
```

**Response 200:** `{ "statusCode": 200, "message": "Question deleted successfully", "data": null }`

---

### 3.5 Vote a question as helpful

```
POST /api/questions/{questionId}/vote
```

**Body:**

```json
{
  "userId": "uuid"
}
```

> Voting the same question twice returns **409 Conflict**.

---

### 3.6 Remove helpful vote

```
DELETE /api/questions/{questionId}/vote?userId={userId}
```

---

## 4. Questions & Answers — Admin APIs

Base path: `/api/admin/questions`

---

### 4.1 List all questions

```
GET /api/admin/questions?status=PENDING&page=0&size=20
```

---

### 4.2 Get a question by ID

```
GET /api/admin/questions/{questionId}
```

---

### 4.3 Moderate a question

```
PATCH /api/admin/questions/{questionId}/moderate
```

**Body:**

```json
{
  "status": "APPROVED",
  "moderatedBy": "admin-uuid"
}
```

| Field | Required | Values |
|---|---|---|
| `status` | Yes | `APPROVED` or `REJECTED` |
| `moderatedBy` | Yes | Admin user UUID |

---

### 4.4 Delete a question (admin)

```
DELETE /api/admin/questions/{questionId}
```

---

### 4.5 Add admin answer to a question

```
POST /api/admin/questions/{questionId}/answer
```

**Body:**

```json
{
  "adminId": "admin-uuid",
  "body": "Yes, it supports fingerprint login via Windows Hello."
}
```

> One answer per question. Returns **409** if answer already exists.

---

### 4.6 Update admin answer

```
PUT /api/admin/questions/{questionId}/answer
```

**Body:**

```json
{
  "body": "Updated answer text here."
}
```

---

### 4.7 Toggle answer visibility

```
PATCH /api/admin/questions/{questionId}/answer/toggle
```

> Toggles `isActive` between `true` and `false`. Use this to temporarily hide an answer while revising, without deleting it. **No request body.**

**Response 200:** Updated question with `answer.isActive` flipped

---

### 4.8 Delete admin answer (soft-delete)

```
DELETE /api/admin/questions/{questionId}/answer
```

---

## 5. Data Models

### ReviewResponse

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `productId` | UUID | |
| `userId` | UUID | |
| `userFirstName` | String | |
| `userLastName` | String | |
| `rating` | Number (1–5) | |
| `title` | String \| null | |
| `body` | String \| null | |
| `isVerifiedPurchase` | Boolean | `true` if submitted with an `orderItemId` |
| `status` | String | `PENDING`, `APPROVED`, `REJECTED` |
| `rejectionReason` | String \| null | Admin-only field; present when rejected |
| `helpfulCount` | Number | |
| `notHelpfulCount` | Number | |
| `media` | Array\<MediaDto\> | Empty array if none |
| `reply` | ReviewReplyDto \| null | Admin reply; `null` if none |
| `createdAt` | ISO 8601 string | |
| `updatedAt` | ISO 8601 string | |

### ReviewStatsResponse

| Field | Type | Notes |
|---|---|---|
| `productId` | UUID | |
| `totalReviews` | Number | Count of approved, non-deleted reviews |
| `averageRating` | String | Decimal string, e.g. `"4.23"` |
| `rating1Count` | Number | |
| `rating2Count` | Number | |
| `rating3Count` | Number | |
| `rating4Count` | Number | |
| `rating5Count` | Number | |
| `updatedAt` | ISO 8601 string | |

### QuestionResponse

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `productId` | UUID | |
| `userId` | UUID | |
| `userFirstName` | String | |
| `userLastName` | String | |
| `body` | String | |
| `status` | String | `PENDING`, `APPROVED`, `REJECTED` |
| `helpfulCount` | Number | |
| `answer` | QuestionAnswerDto \| null | `null` if no active answer |
| `createdAt` | ISO 8601 string | |
| `updatedAt` | ISO 8601 string | |

---

## 6. Moderation Status

| Value | Meaning |
|---|---|
| `PENDING` | Just submitted; awaiting admin review |
| `APPROVED` | Visible to the public |
| `REJECTED` | Hidden from public; may include a rejection reason |

---

## 7. Integration Notes

### Public website (storefront)

- **Product page — star rating bar:** Call `GET /api/reviews/product/{productId}/stats`. Display `averageRating` and the five `ratingXCount` fields as a distribution bar.
- **Product page — reviews list:** Call `GET /api/reviews/product/{productId}` with pagination. Show media thumbnails, verified-purchase badge, helpful/not-helpful buttons, and the admin reply if present.
- **Submit review form:** `POST /api/reviews`. Pass `userId` from the logged-in session. `orderItemId` is optional — only provide it if your order module can supply the UUID (enables "Verified Purchase" badge).
- **Edit review:** Only available if `review.status === "PENDING"`. Use `PUT /api/reviews/{reviewId}`.
- **Helpful voting:** `POST /api/reviews/{reviewId}/vote` with `{ userId, isHelpful: true/false }`. On success update the local count.
- **Q&A section:** Call `GET /api/questions/product/{productId}` ordered by `helpfulCount`. Show the admin answer (if any) below each question. Provide an "Ask a question" CTA that calls `POST /api/questions`.

### Admin dashboard

- **Moderation queue:** `GET /api/admin/reviews?status=PENDING` — show each review with Approve / Reject actions. The rejection form must collect a `rejectionReason`.
- **Review stats recalculation:** Happens **automatically** on approve/reject — no extra call needed.
- **Reply management:** Use `POST` to create and `PUT` to update a reply; the review object in the response always includes the latest reply.
- **Question moderation queue:** `GET /api/admin/questions?status=PENDING` — approve/reject with `PATCH /api/admin/questions/{questionId}/moderate`.
- **Answer workflow:** After approving a question, use `POST /api/admin/questions/{questionId}/answer` to publish an answer. Use the `PATCH .../toggle` endpoint to temporarily hide an answer during revision without deleting it.
- **Pagination:** All list endpoints support `?page=0&size=20`. There is no total-count field in the response currently — implement client-side "load more" or request server-side pagination metadata if needed.

### Error handling

| Status code | Meaning |
|---|---|
| 400 | Validation error or business rule violation (e.g. editing an approved review) |
| 404 | Entity not found |
| 409 | Conflict (duplicate review, duplicate vote, duplicate reply/answer) |

All errors follow the same envelope:

```json
{
  "statusCode": 404,
  "message": "Review not found",
  "data": null
}
```

### Timestamps

All timestamps are **UTC ISO 8601** strings (e.g. `"2026-01-15T10:00:00Z"`). Use `new Date(ts)` in JavaScript or a library like `date-fns`/`dayjs` to format for display.
