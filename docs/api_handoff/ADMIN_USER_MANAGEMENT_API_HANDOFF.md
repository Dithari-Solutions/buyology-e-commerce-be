# Admin — User Management API Handoff

**Base URL:** `https://api-dev.dithari.com`
**Admin panel origin:** `https://admin-dev.dithari.com`
**Auth:** All endpoints require a valid admin JWT access token.

```
Authorization: Bearer <access_token>
```

---

## Response Envelope

Every endpoint returns the same wrapper:

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

Errors follow the same shape with `data: null`:

```json
{
  "statusCode": 404,
  "message": "User not found",
  "data": null
}
```

---

## Pages & Actions to Build

| Page / Action | Route (suggestion) | API calls |
|---|---|---|
| Users List | `/admin/users` | `GET /api/admin/users` |
| User Detail | `/admin/users/:authCredentialId` | `GET /api/admin/users/:authCredentialId` |
| Block a user | button on detail or list row | `PATCH /api/admin/users/:userId/block` |
| Unblock a user | button on detail or list row | `PATCH /api/admin/users/:userId/unblock` |
| Block all inactive users | button on users list header | `POST /api/admin/users/block-inactive` |

---

## Page 1 — Users List (`/admin/users`)

### What to build

A full-page table showing all registered users with pagination. Each row links to the User Detail page and exposes quick block/unblock actions.

### Endpoint

```
GET /api/admin/users?page=0&size=20
```

| Query param | Type | Default | Description |
|---|---|---|---|
| `page` | integer | `0` | Zero-based page index |
| `size` | integer | `20` | Rows per page |

### Response `data`

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8,
  "users": [
    {
      "userId": "uuid",
      "authCredentialId": "uuid",
      "email": "ahmed@example.com",
      "firstName": "Ahmed",
      "lastName": "Al Mansouri",
      "userType": "CUSTOMER",
      "status": "ACTIVE",
      "joinedAt": "2026-03-10T08:30:00Z"
    }
  ]
}
```

### Field reference

| Field | Type | Notes |
|---|---|---|
| `userId` | UUID | Use this for block / unblock calls |
| `authCredentialId` | UUID | Use this as the route param when navigating to the detail page |
| `email` | string \| null | `null` for OAuth-only accounts with no email on record |
| `userType` | `"CUSTOMER"` \| `"ADMIN"` | Role of the user |
| `status` | `"ACTIVE"` \| `"SUSPENDED"` | Current account status |
| `joinedAt` | ISO 8601 string | When the account was created |

### Table columns

| Column | Source field | Notes |
|---|---|---|
| Name | `firstName + " " + lastName` | Show "—" if both are null |
| Email | `email` | Show "No email" if null (OAuth user) |
| Type | `userType` | Badge: blue for CUSTOMER, grey for ADMIN |
| Status | `status` | Badge: green for ACTIVE, red for SUSPENDED |
| Joined | `joinedAt` | Format as local date |
| Actions | — | "View" button → `/admin/users/:authCredentialId`; "Block" or "Unblock" button (toggle based on `status`) |

### Block / Unblock from the list

Each row should show a context action based on the current `status`:

- If `status === "ACTIVE"` → show a **Block** button. On click call `PATCH /api/admin/users/:userId/block`.
- If `status === "SUSPENDED"` → show an **Unblock** button. On click call `PATCH /api/admin/users/:userId/unblock`.

After a successful response, update the row's status badge in-place (no need to reload the full page).

### "Block Inactive Users" action

Add a prominent button in the list page header (e.g. in the top-right toolbar):

> **Block Inactive Users**

On click, show a confirmation dialog:

> "This will suspend all customers who have not logged in for the past 90 days and revoke their sessions. Continue?"

On confirm, call:

```
POST /api/admin/users/block-inactive
```

Display the response `message` (e.g. "Blocked 12 user(s) inactive for more than 90 days") as a toast notification. Reload the user list after success to reflect updated statuses.

### Pagination

- Use `totalElements` for the total count display ("142 users")
- Use `totalPages` to render page controls
- Pass `page` and `size` as query params on each navigation

---

## Page 2 — User Detail (`/admin/users/:authCredentialId`)

### What to build

A single-user view with four sections:
1. **Profile** — identity, personal details, and registration metadata
2. **Actions** — block / unblock the user account
3. **Favorites** — list of products the user has saved
4. **Active Cart** — current cart contents and total

### Endpoint

```
GET /api/admin/users/:authCredentialId
```

| Path param | Type | Description |
|---|---|---|
| `authCredentialId` | UUID | Taken directly from the users list row |

### Response `data`

```json
{
  "userId": "uuid",
  "authCredentialId": "uuid",
  "email": "ahmed@example.com",
  "userType": "CUSTOMER",
  "status": "ACTIVE",
  "joinedAt": "2026-03-10T08:30:00Z",

  "registrationIp": "185.220.101.47",
  "registrationDevice": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ...",

  "firstName": "Ahmed",
  "lastName": "Al Mansouri",
  "phoneNumber": "+971501234567",
  "dateOfBirth": "1990-05-15",
  "avatarUrl": "/user/avatars/uuid.jpg",

  "favorites": {
    "authCredentialId": "uuid",
    "total": 3,
    "items": [
      {
        "id": "uuid",
        "productId": "uuid",
        "productSku": "SKU-001",
        "savedAt": "2026-03-20T12:00:00Z"
      }
    ]
  },

  "activeCart": {
    "id": "uuid",
    "authCredentialId": "uuid",
    "status": "ACTIVE",
    "totalPrice": 1250.00,
    "items": [
      {
        "id": "uuid",
        "productId": "uuid",
        "productSku": "SKU-002",
        "variantId": "uuid",
        "variantSku": "SKU-002-BLK",
        "quantity": 2,
        "unitPrice": 500.00,
        "totalPrice": 1000.00,
        "selectedSpecs": [
          {
            "specOptionId": "uuid",
            "groupCode": "color",
            "value": "Black",
            "unit": null,
            "additionalPrice": 0,
            "colorCode": "#000000"
          }
        ],
        "createdAt": "2026-03-22T09:00:00Z",
        "updatedAt": "2026-03-22T09:00:00Z"
      }
    ],
    "createdAt": "2026-03-22T09:00:00Z",
    "updatedAt": "2026-03-22T09:00:00Z"
  }
}
```

> `activeCart` is `null` when the user has no active cart.
> `avatarUrl` is a relative path — prepend the base URL to render the image.
> `registrationIp` and `registrationDevice` are `null` for accounts created before this feature was deployed.

---

### Section 1 — Profile

Display a read-only profile card.

| Field | Source | Notes |
|---|---|---|
| Avatar | `avatarUrl` | Prepend base URL. Fall back to initials avatar if null |
| Full name | `firstName + " " + lastName` | Show "Unknown" if both null |
| Email | `email` | Show "No email" if null |
| Phone | `phoneNumber` | Show "—" if null |
| Date of birth | `dateOfBirth` | Format as `DD MMM YYYY`. Show "—" if null |
| User type | `userType` | Badge |
| Status | `status` | Badge: green for ACTIVE, red for SUSPENDED |
| Member since | `joinedAt` | Format as local date |
| Registration IP | `registrationIp` | Show "—" if null. Display in monospace font |
| Registration device | `registrationDevice` | Show "—" if null. Truncate to ~80 chars with a tooltip showing the full User-Agent string |

---

### Section 2 — Account Actions

Display a clearly separated actions card below the profile. Show only the relevant action based on the current `status`.

#### Block a user

Shown when `status === "ACTIVE"`.

Show a **Block Account** button (destructive / red). On click show a confirmation dialog:

> "Blocking this account will immediately suspend the user and revoke all their active sessions. They will not be able to sign in until unblocked. Continue?"

On confirm, call:

```
PATCH /api/admin/users/:userId/block
```

> Use `userId` (not `authCredentialId`) for this endpoint.

| Response status | Meaning |
|---|---|
| `200` | User blocked. Update the status badge and switch to the "Unblock" button |
| `404` | User not found |
| `409` | User is already blocked (handle gracefully — refresh the page) |

#### Unblock a user

Shown when `status === "SUSPENDED"`.

Show an **Unblock Account** button (primary / blue). On click show a confirmation dialog:

> "This will restore the user's access. They will be able to sign in again immediately. Continue?"

On confirm, call:

```
PATCH /api/admin/users/:userId/unblock
```

| Response status | Meaning |
|---|---|
| `200` | User unblocked. Update the status badge and switch to the "Block" button |
| `404` | User not found |
| `409` | User is not blocked (handle gracefully — refresh the page) |

---

### Section 3 — Favorites

Display a list or grid of the user's saved products.

| Field | Source | Notes |
|---|---|---|
| Count | `favorites.total` | Show in section header: "Favorites (3)" |
| Product SKU | `favorites.items[].productSku` | Link to the product detail page in the admin if available |
| Saved on | `favorites.items[].savedAt` | Format as local date |

**Empty state:** If `favorites.total === 0`, show "This user has no saved products."

> The favorites list only returns `productId` and `productSku`. To show the product name or thumbnail, call the product detail endpoint separately for each `productId`.

---

### Section 4 — Active Cart

Display the user's current cart with line items and total.

| Field | Source | Notes |
|---|---|---|
| Cart total | `activeCart.totalPrice` | Format as currency |
| Item count | `activeCart.items.length` | Show in section header: "Active Cart (2 items)" |
| Product SKU | `items[].productSku` | |
| Variant SKU | `items[].variantSku` | Only show if not null |
| Qty | `items[].quantity` | |
| Unit price | `items[].unitPrice` | Format as currency |
| Line total | `items[].totalPrice` | Format as currency |
| Spec selections | `items[].selectedSpecs` | Render as tags (e.g. `color: Black`) |

**Empty state:** If `activeCart === null`, show "This user has no active cart."

---

## Endpoint Reference

### `GET /api/admin/users`
List all users, paginated.

### `GET /api/admin/users/:authCredentialId`
Full user detail — profile, registration metadata, favorites, active cart.

### `PATCH /api/admin/users/:userId/block`
Manually block a user. Sets status to `SUSPENDED` and revokes all active sessions.

| Status | Message |
|---|---|
| `200` | User blocked successfully |
| `404` | User not found |
| `409` | User is already blocked |

### `PATCH /api/admin/users/:userId/unblock`
Restore a blocked user. Sets status back to `ACTIVE`.

| Status | Message |
|---|---|
| `200` | User unblocked successfully |
| `404` | User not found |
| `409` | User is not currently blocked |

### `POST /api/admin/users/block-inactive`
Immediately runs the inactivity sweep. Suspends all CUSTOMER accounts whose last login (or account creation, if they never logged in) is older than the configured threshold (default: 90 days). Also revokes all active sessions for each suspended user.

Returns a plain message in `data: null` — the count is in `message`.

```json
{
  "statusCode": 200,
  "message": "Blocked 12 user(s) inactive for more than 90 days",
  "data": null
}
```

If no inactive users are found:

```json
{
  "statusCode": 200,
  "message": "No inactive users found to block",
  "data": null
}
```

---

## ID Reference — `userId` vs `authCredentialId`

These are two different UUIDs and are **not interchangeable**.

| ID | Where it comes from | Used for |
|---|---|---|
| `authCredentialId` | `auth_credentials` table | User detail page route, favorites, cart |
| `userId` | `users` table | Block / unblock endpoints |

Both IDs are returned in every list row and in the detail response. Store both when you load a user.

---

## Error States

| Status | Message | What to show |
|---|---|---|
| `404` | `Auth credential not found` | Full-page "User not found" error with back button |
| `404` | `User not found` | Same as above |
| `409` | `User is already blocked` | Toast: "This user is already blocked" — refresh the row |
| `409` | `User is not currently blocked` | Toast: "This user is already active" — refresh the row |

---

## Navigation Flow

```
/admin/users
  → Table row click or "View" button
  → /admin/users/:authCredentialId
  → Back button returns to /admin/users (preserve page number in state)
```

---

## Implementation Notes

- Store **both** `userId` and `authCredentialId` when you load any user — you need `authCredentialId` for navigation and detail fetches, and `userId` for block/unblock.
- After a block or unblock action succeeds, update the local state (status badge, action button) without a full page reload for a faster UX. Only reload if a `409` conflict is returned.
- The "Block Inactive Users" sweep may affect many accounts at once — show a loading state on the button and disable it while the request is in flight.
- `registrationIp` and `registrationDevice` are only populated for accounts registered after the tracking feature was deployed. Always handle `null` gracefully.
- The inactivity threshold is 90 days by default and is configured server-side. Do not hardcode this value in the UI copy — use a generic phrase like "the inactivity period" or fetch it from a config endpoint if one is added later.
- The user list endpoint is paginated — do not fetch all pages at once.
- `email` can be `null` for OAuth users. Handle this in all display fields and table cells.
- `activeCart` being `null` is not an error — the user simply has no active cart.
