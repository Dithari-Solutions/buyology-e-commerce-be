# User Profile & Address — API Handoff

**Base URL:** `https://api-dev.dithari.com`
**Auth:** All endpoints require a valid JWT access token.

```
Authorization: Bearer <access_token>
```

---

## Response Envelope

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

---

## Enums

### AddressLabel
| Value | Description |
|---|---|
| `HOME` | Home address (default) |
| `WORK` | Work address |
| `OTHER` | Any other address |

---

## Part 1 — Profile Page

The profile page lets the user fill in their personal details and upload an avatar. None of these fields are required until the user tries to pay — but the API will tell you exactly what is missing so you can show a completion banner.

---

### Get Profile

`GET /api/users/{userId}/profile`

Call this when the profile page mounts to populate all fields.

**Response `data`:**
```json
{
  "userId": "uuid",
  "firstName": "Ahmed",
  "lastName": "Al Mansouri",
  "phoneNumber": "+971501234567",
  "dateOfBirth": "1990-05-15",
  "avatarUrl": "/user/avatars/uuid.jpg",
  "paymentReady": false,
  "missingFields": ["phoneNumber", "deliveryAddress"],
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:00:00Z"
}
```

| Field | Type | Notes |
|---|---|---|
| `paymentReady` | boolean | `true` when all 4 required fields are filled. Use this to show/hide a "complete your profile" banner |
| `missingFields` | string[] | Lists what is still missing. Empty array when `paymentReady` is `true` |
| `avatarUrl` | string \| null | Relative path — prepend the base URL to display the image |
| `dateOfBirth` | string \| null | ISO 8601 date `YYYY-MM-DD` |

**Possible values in `missingFields`:**
| Value | Meaning |
|---|---|
| `firstName` | User has not set their first name |
| `lastName` | User has not set their last name |
| `phoneNumber` | User has not set a phone number |
| `deliveryAddress` | User has no saved delivery address |

---

### Update Profile Details

`PATCH /api/users/{userId}/profile`

All fields are optional — send only what changed.

**Request Body:**
```json
{
  "firstName": "Ahmed",
  "lastName": "Al Mansouri",
  "phoneNumber": "+971501234567",
  "dateOfBirth": "1990-05-15"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `firstName` | string | No | Max 100 chars |
| `lastName` | string | No | Max 100 chars |
| `phoneNumber` | string | No | E.164 format — `+971501234567` |
| `dateOfBirth` | string | No | ISO 8601 date — `YYYY-MM-DD` |

**Response `data`:** Updated profile object (same shape as Get Profile).

---

### Upload Avatar

`PATCH /api/users/{userId}/profile/avatar`

**Content-Type:** `multipart/form-data`

| Part | Type | Required | Notes |
|---|---|---|---|
| `avatar` | file | Yes | JPEG, PNG, WebP, or GIF. Max size governed by server upload limit (20 MB) |

**Example (fetch):**
```js
const formData = new FormData()
formData.append('avatar', file) // file from <input type="file">

fetch(`/api/users/${userId}/profile/avatar`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${token}` },
  body: formData
})
```

**Response `data`:** Updated profile object. `avatarUrl` will contain the new path.

---

### Profile Page — Recommended UI Flow

```
1. Mount  → GET /api/users/{userId}/profile
           → populate firstName, lastName, phoneNumber, dateOfBirth, avatar

2. If paymentReady === false:
   → show banner: "Complete your profile to enable checkout"
   → highlight each field in missingFields

3. User edits any field → PATCH /api/users/{userId}/profile
   → update UI with returned profile

4. User taps avatar area / upload button
   → open file picker (accept="image/*")
   → on file selected → PATCH /api/users/{userId}/profile/avatar (multipart)
   → display returned avatarUrl as the new avatar

5. After any update, re-check paymentReady
   → hide banner once paymentReady === true
```

---

## Part 2 — Address Book (Profile Page)

The address book is accessible from both the profile page and the checkout page.

---

### Get All Addresses

`GET /api/users/{userId}/addresses`

**Response `data`:** Array of address objects.

```json
[
  {
    "id": "uuid",
    "firstName": "Ahmed",
    "lastName": "Al Mansouri",
    "phoneNumber": "+971501234567",
    "phoneVerified": false,
    "label": "HOME",
    "addressLine1": "Building 5, Sheikh Zayed Road",
    "addressLine2": "Apartment 4B, Floor 2",
    "city": "Dubai",
    "state": "Dubai",
    "country": "AE",
    "postalCode": "00000",
    "formattedAddress": null,
    "latitude": null,
    "longitude": null,
    "addressVerified": false,
    "isDefault": true,
    "createdAt": "2026-03-21T10:00:00Z",
    "updatedAt": "2026-03-21T10:00:00Z"
  }
]
```

---

### Get Single Address

`GET /api/users/{userId}/addresses/{addressId}`

**Response `data`:** Single address object (same shape as above).

---

### Add New Address

`POST /api/users/{userId}/addresses`

**Request Body:**
```json
{
  "firstName": "Ahmed",
  "lastName": "Al Mansouri",
  "phoneNumber": "+971501234567",
  "label": "HOME",
  "addressLine1": "Building 5, Sheikh Zayed Road",
  "addressLine2": "Apartment 4B, Floor 2",
  "city": "Dubai",
  "state": "Dubai",
  "country": "AE",
  "postalCode": "00000",
  "isDefault": true
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `firstName` | string | Yes | Recipient first name — may differ from profile name |
| `lastName` | string | Yes | Recipient last name |
| `phoneNumber` | string | Yes | E.164 format. Courier contact number |
| `label` | AddressLabel | No | `HOME`, `WORK`, `OTHER`. Defaults to `HOME` |
| `addressLine1` | string | Yes | Building name/number + street |
| `addressLine2` | string | No | Apartment, floor, unit |
| `city` | string | Yes | |
| `state` | string | No | Emirate / province |
| `country` | string | Yes | ISO 2-letter code, e.g. `"AE"` |
| `postalCode` | string | No | |
| `isDefault` | boolean | No | If `true`, clears the default flag from all other addresses |

**Response `data`:** The created address object. HTTP 201.

---

### Set Default Address

`PATCH /api/users/{userId}/addresses/{addressId}/default`

No request body. Marks this address as default and clears the flag from all others.

**Response `data`:** Updated address object.

---

### Delete Address

`DELETE /api/users/{userId}/addresses/{addressId}`

No request body. `data` is `null` on success.

---

## Part 3 — Checkout Page

The checkout page needs to show the user's saved details and let them pick or add a delivery address before initiating payment. The backend will **block payment** if profile details or an address are missing.

---

### Step 1 — Load checkout data

On checkout page mount, call these two endpoints in parallel:

```
GET /api/users/{userId}/profile
GET /api/users/{userId}/addresses
```

Use the results to:
- Pre-fill the customer name and phone in the order summary
- Show the list of saved addresses for the user to pick from
- Check `paymentReady` — if `false`, block the "Pay now" button and show what is missing

---

### Step 2 — Address selection UI

```
If addresses.length === 0:
  → Show "Add delivery address" form (POST /addresses)
  → Block "Pay now" until an address is saved

If addresses.length > 0:
  → Show all addresses as selectable cards
  → Pre-select the one with isDefault === true
  → Show "Add new address" button that opens the add form
  → Show "Edit" / "Delete" options on each card
```

---

### Step 3 — Pre-fill payment request from selected address

When the user picks an address and taps "Pay now", use its fields to populate `POST /api/payments/initiate`:

```js
// address = the address card the user selected
// profile = from GET /profile

const paymentRequest = {
  appOrderId: order.id,
  methodType: selectedMethod,         // "CARD", "TABBY", or "TAMARA"
  amount: order.total,
  currency: "AED",
  customerId: userId,
  customerEmail: userEmail,           // from auth / session
  customerPhone: address.phoneNumber,
  billingName: `${address.firstName} ${address.lastName}`,
  billingApartment: address.addressLine2 ?? "",
  billingStreet: address.addressLine1,
  billingCity: address.city,
  billingState: address.state ?? "",
  billingCountry: address.country,
  billingPostalCode: address.postalCode ?? ""
}
```

---

### Checkout Page — Complete Flow

```
1. Mount → GET /profile + GET /addresses (parallel)

2. If paymentReady === false:
   → Disable "Pay now" button
   → Show inline message per missing field:
     - firstName / lastName  → "Complete your profile"  → link to profile page
     - phoneNumber           → "Add a phone number"     → link to profile page
     - deliveryAddress       → "Add a delivery address" → open address form inline

3. If paymentReady === true and addresses.length > 0:
   → Show saved address cards
   → Pre-select default address (isDefault === true)
   → User may switch to another address or add a new one
   → Enable "Pay now" button

4. User taps "Add new address":
   → Show address form
   → POST /api/users/{userId}/addresses
   → On success: add returned address to the list, auto-select it

5. User taps "Edit" on an address card:
   → Show pre-filled form (current address values)
   → Since there is no PATCH address endpoint, handle as:
       DELETE old address → POST new address with isDefault matching the old one
   → Or just save the new one without deleting (user can delete old later)

6. User selects payment method + taps "Pay now":
   → POST /api/payments/initiate with selected address fields
   → Follow the payment flow from PAYMENT_SYSTEM_API_HANDOFF.md
```

---

## Required Fields for Payment — Summary

The backend will reject `POST /api/payments/initiate` with HTTP 500 and a message listing missing fields if any of these are not set:

| Field | Where to fill it |
|---|---|
| `firstName` | Profile page → Update Profile |
| `lastName` | Profile page → Update Profile |
| `phoneNumber` | Profile page → Update Profile |
| At least one delivery address | Profile page or Checkout page → Add Address |

The frontend should check `paymentReady` from `GET /profile` before showing the payment button to avoid hitting this error at checkout time.

---

## Common Error Responses

| HTTP Status | Typical Cause |
|---|---|
| `400` | Validation failure — missing required field, invalid phone format |
| `404` | User or address not found |
| `500` | Payment blocked — profile incomplete (message lists missing fields) |

**Profile incomplete error shape:**
```json
{
  "statusCode": 500,
  "message": "Your profile is incomplete. Please fill in the following before paying: lastName, deliveryAddress",
  "data": null
}
```
