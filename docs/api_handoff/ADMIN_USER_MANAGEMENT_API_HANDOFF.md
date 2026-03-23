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
  "message": "Auth credential not found",
  "data": null
}
```

---

## Pages to Build

| Page | Route (suggestion) | API calls |
|---|---|---|
| Users List | `/admin/users` | `GET /api/admin/users` |
| User Detail | `/admin/users/:authCredentialId` | `GET /api/admin/users/:authCredentialId` |

---

## Page 1 — Users List (`/admin/users`)

### What to build

A full-page table showing all registered users. Supports pagination. Each row links to the User Detail page.

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
| `userId` | UUID | Internal users table ID |
| `authCredentialId` | UUID | Use this as the route param when navigating to the detail page |
| `email` | string \| null | `null` for OAuth-only accounts with no email on record |
| `userType` | `"CUSTOMER"` \| `"ADMIN"` | Role of the user |
| `status` | `"ACTIVE"` \| `"SUSPENDED"` | Account status |
| `joinedAt` | ISO 8601 string | When the account was created |

### Table columns

| Column | Source field | Notes |
|---|---|---|
| Name | `firstName + " " + lastName` | Show "—" if both are null |
| Email | `email` | Show "No email" if null (OAuth user) |
| Type | `userType` | Badge: blue for CUSTOMER, red for ADMIN |
| Status | `status` | Badge: green for ACTIVE, red for SUSPENDED |
| Joined | `joinedAt` | Format as local date |
| Actions | — | "View" button → navigate to `/admin/users/:authCredentialId` |

### Pagination

- Use `totalElements` for the total count display ("142 users")
- Use `totalPages` to render page controls
- Pass `page` and `size` as query params on each navigation

---

## Page 2 — User Detail (`/admin/users/:authCredentialId`)

### What to build

A single-user view with three sections:
1. **Profile** — identity and personal details
2. **Favorites** — list of products the user has saved
3. **Active Cart** — current cart contents and total (empty state if no active cart)

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

> `activeCart` is `null` when the user has no active cart. Render an empty state in that section.
> `avatarUrl` is a relative path — prepend the base URL to render the image.

---

### Section 1 — Profile

Display a read-only profile card. No edit functionality is required on this page.

| Field | Source | Notes |
|---|---|---|
| Avatar | `avatarUrl` | Prepend base URL. Fall back to initials avatar if null |
| Full name | `firstName + " " + lastName` | Show "Unknown" if both null |
| Email | `email` | Show "No email" if null |
| Phone | `phoneNumber` | Show "—" if null |
| Date of birth | `dateOfBirth` | Format as `DD MMM YYYY`. Show "—" if null |
| User type | `userType` | Badge |
| Status | `status` | Badge |
| Member since | `joinedAt` | Format as local date |

---

### Section 2 — Favorites

Display a list or grid of the user's saved products.

| Field | Source | Notes |
|---|---|---|
| Count | `favorites.total` | Show in section header: "Favorites (3)" |
| Product SKU | `favorites.items[].productSku` | Link to the product detail page in the admin if available |
| Saved on | `favorites.items[].savedAt` | Format as local date |

**Empty state:** If `favorites.total === 0`, show "This user has no saved products."

> The favorites list only returns `productId` and `productSku`. To show the product name or thumbnail, call the product detail endpoint separately for each `productId`, or batch-fetch if your admin panel has a product lookup utility.

---

### Section 3 — Active Cart

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

## Error States

| Status | Message | What to show |
|---|---|---|
| `404` | `Auth credential not found` | Full-page "User not found" error with back button |
| `404` | `User not found` | Same as above |

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

- Store `authCredentialId` (not `userId`) in the route — it is the stable identifier used across all admin endpoints (favorites, cart, etc.)
- The user list endpoint is paginated — do not fetch all pages at once. Load on demand as the admin navigates pages.
- On the detail page, a single API call returns profile + favorites + cart in one response. No waterfall requests needed.
- `email` can be `null` for users who registered via OAuth (Google, Facebook, Apple) and did not provide an email. Handle this in all display fields and table cells.
- `activeCart` being `null` is not an error — it just means the user has not added anything to their cart yet, or all previous carts were checked out.
