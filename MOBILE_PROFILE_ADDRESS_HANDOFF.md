# Mobile Integration Handoff — Profile & Address Module

**Version:** 1.0  
**Date:** 2026-04-06  
**Base URL:** `https://api-dev.dithari.com`  
**Auth:** All endpoints require `Authorization: Bearer <accessToken>` header

---

## Table of Contents

1. [Authentication & User ID](#1-authentication--user-id)
2. [Response Envelope](#2-response-envelope)
3. [Profile Endpoints](#3-profile-endpoints)
   - [GET Profile](#31-get-profile)
   - [PATCH Update Profile](#32-patch-update-profile)
   - [PATCH Upload Avatar Image](#33-patch-upload-avatar-image)
   - [PATCH Set Country Preference](#34-patch-set-country-preference)
4. [Address Endpoints](#4-address-endpoints)
   - [POST Create Address](#41-post-create-address)
   - [GET All Addresses](#42-get-all-addresses)
   - [GET Single Address](#43-get-single-address)
   - [PATCH Set Default Address](#44-patch-set-default-address)
   - [DELETE Address](#45-delete-address)
5. [Payment Readiness Flow](#5-payment-readiness-flow)
6. [Field Reference Tables](#6-field-reference-tables)
7. [Validation Rules](#7-validation-rules)
8. [Error Responses](#8-error-responses)

---

## 1. Authentication & User ID

Every request must include the JWT in the `Authorization` header:

```
Authorization: Bearer <accessToken>
```

**Important — `{userId}` in URL paths:**  
The `{userId}` path variable is the `auth_credentials.id` (UUID extracted from the JWT `sub` claim), **not** the `users.id`. Use the same UUID you received from the login/register response as the `userId` field.

```
JWT sub claim  →  auth_credentials.id  →  used as {userId} in all paths
```

---

## 2. Response Envelope

Every response is wrapped in this envelope:

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `statusCode` | int | HTTP status code mirrored |
| `message` | string | Human-readable result message |
| `data` | object / array / null | Payload; null on delete |

---

## 3. Profile Endpoints

### 3.1 GET Profile

Fetch the authenticated user's profile, including payment readiness status.

```
GET /api/users/{userId}/profile
```

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Success Response — 200 OK:**
```json
{
  "statusCode": 200,
  "message": "Profile fetched successfully",
  "data": {
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "user@example.com",
    "firstName": "Ahmed",
    "lastName": "Al Mansouri",
    "phoneNumber": "+971501234567",
    "dateOfBirth": "1990-05-15",
    "avatarUrl": "/user/avatars/3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg",
    "paymentReady": false,
    "missingFields": ["phoneNumber", "deliveryAddress"],
    "selectedCountryCode": "UAE",
    "preferredCurrency": "AED",
    "preferredLanguage": "EN",
    "createdAt": "2026-03-21T10:00:00Z",
    "updatedAt": "2026-03-21T10:00:00Z"
  }
}
```

**Response Fields:**

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `userId` | UUID (string) | No | The auth_credentials UUID |
| `email` | string | Yes | Null for social-login-only users |
| `firstName` | string | Yes | Max 100 chars |
| `lastName` | string | Yes | Max 100 chars |
| `phoneNumber` | string | Yes | E.164 format, e.g. `+971501234567` |
| `dateOfBirth` | string | Yes | ISO 8601: `YYYY-MM-DD` |
| `avatarUrl` | string | Yes | Relative path — prepend base URL to display. Null if no avatar set |
| `paymentReady` | boolean | No | `true` only when all required fields are filled and at least one address exists |
| `missingFields` | string[] | No | Empty array when `paymentReady` is true. Possible values: `"firstName"`, `"lastName"`, `"phoneNumber"`, `"deliveryAddress"` |
| `selectedCountryCode` | string | Yes | ISO 3166-1 alpha-3, e.g. `"UAE"`, `"AZE"` |
| `preferredCurrency` | string | Yes | ISO 4217, e.g. `"AED"`, `"AZN"` |
| `preferredLanguage` | string | Yes | Language code, e.g. `"EN"`, `"AR"`, `"AZ"` |
| `createdAt` | string | No | ISO 8601 timestamp |
| `updatedAt` | string | No | ISO 8601 timestamp |

**Displaying the avatar:**
```
Full avatar URL = {BASE_URL} + avatarUrl
Example: https://api-dev.dithari.com/user/avatars/3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg
```

---

### 3.2 PATCH Update Profile

Update one or more profile fields. All fields are optional — only send what needs to change.

```
PATCH /api/users/{userId}/profile
Content-Type: application/json
```

**Request Body:**
```json
{
  "firstName": "Ahmed",
  "lastName": "Al Mansouri",
  "phoneNumber": "+971501234567",
  "dateOfBirth": "1990-05-15",
  "selectedCountryCode": "UAE",
  "preferredCurrency": "AED",
  "preferredLanguage": "EN"
}
```

**Request Fields (all optional):**

| Field | Type | Max Length | Validation | Notes |
|-------|------|-----------|------------|-------|
| `firstName` | string | 100 | None | Omit to leave unchanged |
| `lastName` | string | 100 | None | Omit to leave unchanged |
| `phoneNumber` | string | — | E.164 pattern: `^\+[1-9]\d{6,14}$` | Must include country code prefix |
| `dateOfBirth` | string | — | Format: `YYYY-MM-DD` | ISO 8601 date |
| `selectedCountryCode` | string | 3 | ISO 3166-1 alpha-3 | e.g. `"UAE"`, `"AZE"`, `"GBR"` |
| `preferredCurrency` | string | 3 | ISO 4217 | e.g. `"AED"`, `"AZN"`, `"USD"` |
| `preferredLanguage` | string | 5 | Language code | e.g. `"EN"`, `"AR"`, `"AZ"` |

**Success Response — 200 OK:**  
Returns the full updated `ProfileResponse` (same structure as [GET Profile](#31-get-profile)).

---

### 3.3 PATCH Upload Avatar Image

Upload or replace the user's profile avatar. Send as `multipart/form-data`.

```
PATCH /api/users/{userId}/profile/avatar
Content-Type: multipart/form-data
```

**Form Fields:**

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `file` | File (binary) | Yes | The image file to upload |

**Supported Formats:** `image/jpeg`, `image/png`, `image/webp`, `image/gif`  
**Max File Size:** 20 MB

**Example — React Native (using fetch):**
```javascript
const formData = new FormData();
formData.append('file', {
  uri: imageUri,           // local file URI from image picker
  type: 'image/jpeg',      // MIME type
  name: 'avatar.jpg',      // filename (any name is fine)
});

const response = await fetch(
  `${BASE_URL}/api/users/${userId}/profile/avatar`,
  {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      // Do NOT set Content-Type manually — fetch sets multipart boundary automatically
    },
    body: formData,
  }
);
```

**Success Response — 200 OK:**  
Returns the full updated `ProfileResponse` with the new `avatarUrl`.

```json
{
  "statusCode": 200,
  "message": "Avatar updated successfully",
  "data": {
    "avatarUrl": "/user/avatars/3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg",
    ...
  }
}
```

> The avatar filename is always `{userId}.{extension}`. Uploading again replaces the previous image.

---

### 3.4 PATCH Set Country Preference

Set the user's country and optionally their preferred currency. If currency is not provided, it is auto-derived from the country.

```
PATCH /api/users/{userId}/profile/country-preference
Content-Type: application/json
```

**Request Body:**
```json
{
  "countryCode": "UAE",
  "currency": "AED"
}
```

**Request Fields:**

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `countryCode` | string | Yes | ISO 3166-1 alpha-3, e.g. `"UAE"`, `"AZE"` |
| `currency` | string | No | ISO 4217. If omitted, auto-derived from `countryCode` |

**Success Response — 200 OK:**  
Returns the full updated `ProfileResponse`.

---

## 4. Address Endpoints

### 4.1 POST Create Address

Add a new delivery address for the user.

```
POST /api/users/{userId}/addresses
Content-Type: application/json
```

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

**Request Fields:**

| Field | Type | Required | Max Length | Validation | Notes |
|-------|------|----------|-----------|------------|-------|
| `firstName` | string | **Yes** | 100 | Not blank | Recipient first name (may differ from profile) |
| `lastName` | string | **Yes** | 100 | Not blank | Recipient last name |
| `phoneNumber` | string | **Yes** | — | E.164: `^\+[1-9]\d{6,14}$` | Courier contact number |
| `label` | string | No | — | Enum: `HOME`, `WORK`, `OTHER` | Defaults to `HOME` |
| `addressLine1` | string | **Yes** | 255 | Not blank | Building name/number + street |
| `addressLine2` | string | No | 255 | — | Apartment, floor, unit details |
| `city` | string | **Yes** | 100 | Not blank | City name |
| `state` | string | No | 100 | — | State / emirate / province |
| `country` | string | **Yes** | 3 | Min 2 chars | ISO 2-letter code, e.g. `"AE"`, `"AZ"`, `"GB"` |
| `postalCode` | string | No | 20 | — | Postal / ZIP code |
| `isDefault` | boolean | No | — | — | If `true`, clears default flag from all other addresses. Defaults to `false` |

**Success Response — 201 Created:**
```json
{
  "statusCode": 201,
  "message": "Address saved",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
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
    "createdAt": "2026-04-06T10:00:00Z",
    "updatedAt": "2026-04-06T10:00:00Z"
  }
}
```

---

### 4.2 GET All Addresses

Retrieve all saved addresses for the user.

```
GET /api/users/{userId}/addresses
```

**Success Response — 200 OK:**
```json
{
  "statusCode": 200,
  "message": "Addresses fetched",
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
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
      "createdAt": "2026-04-06T10:00:00Z",
      "updatedAt": "2026-04-06T10:00:00Z"
    }
  ]
}
```

`data` is an array (empty array `[]` if no addresses saved).

---

### 4.3 GET Single Address

Retrieve one address by its ID. Only accessible by the address owner.

```
GET /api/users/{userId}/addresses/{addressId}
```

**Path Params:**

| Param | Type | Description |
|-------|------|-------------|
| `userId` | UUID | The auth credentials UUID |
| `addressId` | UUID | The address UUID from the address list |

**Success Response — 200 OK:**  
Returns a single `AddressResponse` object (same structure as above).

---

### 4.4 PATCH Set Default Address

Mark a specific address as the default delivery address. Automatically clears the default flag from all other addresses.

```
PATCH /api/users/{userId}/addresses/{addressId}/default
```

No request body required.

**Success Response — 200 OK:**
```json
{
  "statusCode": 200,
  "message": "Default address updated",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "isDefault": true,
    ...
  }
}
```

---

### 4.5 DELETE Address

Permanently delete an address. Only accessible by the address owner.

```
DELETE /api/users/{userId}/addresses/{addressId}
```

No request body required.

**Success Response — 200 OK:**
```json
{
  "statusCode": 200,
  "message": "Address deleted",
  "data": null
}
```

---

## 5. Payment Readiness Flow

Before a user can proceed to payment, the backend validates that all required fields are filled. The `paymentReady` flag on the profile response tells you the current state.

**Required to be `paymentReady: true`:**
1. `firstName` — must be set
2. `lastName` — must be set
3. `phoneNumber` — must be set in profile
4. At least one saved delivery address

**How to use `missingFields` in the UI:**

```json
{
  "paymentReady": false,
  "missingFields": ["phoneNumber", "deliveryAddress"]
}
```

| `missingFields` value | What to prompt the user to fill |
|-----------------------|----------------------------------|
| `"firstName"` | First name in Profile screen |
| `"lastName"` | Last name in Profile screen |
| `"phoneNumber"` | Phone number in Profile screen |
| `"deliveryAddress"` | Add at least one address in Address Book |

**Recommended flow:**
1. On cart/checkout screen, call `GET /api/users/{userId}/profile`
2. If `paymentReady === false`, show an inline banner listing `missingFields`
3. Navigate user to Profile or Address screens to complete the missing fields
4. After user saves, re-fetch the profile to re-check `paymentReady`
5. Enable the checkout button only when `paymentReady === true`

---

## 6. Field Reference Tables

### AddressLabel Enum

| Value | Display |
|-------|---------|
| `HOME` | Home |
| `WORK` | Work |
| `OTHER` | Other |

### Address Response Fields

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `id` | UUID | No | Address unique identifier |
| `firstName` | string | No | Recipient first name |
| `lastName` | string | No | Recipient last name |
| `phoneNumber` | string | No | E.164 format courier contact |
| `phoneVerified` | boolean | No | Always `false` currently (SMS OTP pending Twilio setup) |
| `label` | string | No | `HOME`, `WORK`, or `OTHER` |
| `addressLine1` | string | No | Building + street |
| `addressLine2` | string | Yes | Apartment / floor / unit |
| `city` | string | No | City name |
| `state` | string | Yes | State / emirate / province |
| `country` | string | No | ISO 2-letter code, e.g. `"AE"` |
| `postalCode` | string | Yes | Postal / ZIP code |
| `formattedAddress` | string | Yes | Always `null` currently (geocoding pending) |
| `latitude` | number | Yes | Always `null` currently (geocoding pending) |
| `longitude` | number | Yes | Always `null` currently (geocoding pending) |
| `addressVerified` | boolean | No | Always `false` currently (geocoding pending) |
| `isDefault` | boolean | No | `true` for the user's default delivery address |
| `createdAt` | string | No | ISO 8601 timestamp |
| `updatedAt` | string | No | ISO 8601 timestamp |

---

## 7. Validation Rules

### Phone Number (E.164 format)

Pattern: `^\+[1-9]\d{6,14}$`

- Must start with `+`
- Followed by country code and number
- Total length 7–15 digits after `+`

| Country | Example |
|---------|---------|
| UAE | `+971501234567` |
| Azerbaijan | `+994501234567` |
| UK | `+447911123456` |
| USA | `+12025551234` |

### Country Codes

| Field | Format | Example |
|-------|--------|---------|
| `country` (address) | ISO 2-letter (alpha-2) | `"AE"`, `"AZ"`, `"GB"` |
| `selectedCountryCode` (profile) | ISO 3-letter (alpha-3) | `"UAE"`, `"AZE"`, `"GBR"` |

> Note the difference: address uses 2-letter codes; profile country preference uses 3-letter codes.

### Date of Birth

Format: `YYYY-MM-DD` (ISO 8601 date)  
Example: `"1990-05-15"`

---

## 8. Error Responses

All errors follow the standard envelope:

```json
{
  "statusCode": 400,
  "message": "Validation failed: phoneNumber must be in E.164 format",
  "data": null
}
```

**Common Error Codes:**

| Status | Scenario |
|--------|----------|
| `400` | Invalid request body, missing required fields, invalid phone format |
| `401` | Missing or invalid JWT token |
| `403` | Attempting to access another user's data |
| `404` | User, profile, or address not found |
| `409` | Conflict (e.g., duplicate operations) |
| `500` | Server error |

**Payment Readiness Error (when backend blocks payment):**
```json
{
  "statusCode": 400,
  "message": "Your profile is incomplete. Please fill in the following before paying: firstName, lastName, phoneNumber, deliveryAddress",
  "data": null
}
```

---

## Quick Endpoint Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/users/{userId}/profile` | Fetch profile + payment readiness |
| `PATCH` | `/api/users/{userId}/profile` | Update profile fields (partial) |
| `PATCH` | `/api/users/{userId}/profile/avatar` | Upload/replace profile image |
| `PATCH` | `/api/users/{userId}/profile/country-preference` | Set country & currency |
| `POST` | `/api/users/{userId}/addresses` | Create new address |
| `GET` | `/api/users/{userId}/addresses` | Get all addresses |
| `GET` | `/api/users/{userId}/addresses/{addressId}` | Get single address |
| `PATCH` | `/api/users/{userId}/addresses/{addressId}/default` | Set address as default |
| `DELETE` | `/api/users/{userId}/addresses/{addressId}` | Delete address |
