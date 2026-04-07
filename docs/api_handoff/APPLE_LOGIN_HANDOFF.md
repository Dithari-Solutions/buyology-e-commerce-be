# Apple Login API — Frontend Integration Handoff

**Base URL:** `/auth`
**Auth Strategy:** OAuth2 (Authorization Code Flow) with Apple ID.

---

## 1. Overview

The Apple Login integration allows users to sign in or sign up using their Apple ID. The backend handles the exchange of the authorization code for tokens and manages user creation/linking.

---

## 2. Authentication Flow

1.  **Frontend**: Triggers the Apple Sign-In flow (using `Sign in with Apple` JS library or native iOS/Android SDK).
2.  **Apple**: Authenticates the user and returns an `authorization_code` (and a `user` object containing name/email on the *first* login only).
3.  **Frontend**: Sends the `code` and optional user details to the backend `/auth/apple/callback` endpoint.
4.  **Backend**: Validates the code with Apple, extracts the user's permanent Apple ID (`sub`), and returns a session.

---

## 3. Endpoints

### 3.1 Apple Login Callback

Process the authorization code received from Apple.

```http
POST /auth/apple/callback
```

**Request Body:**

| Field       | Type   | Required | Description |
| :---------- | :----- | :------- | :---------- |
| `code`      | String | **Yes**  | The `authorization_code` returned by Apple. |
| `firstName` | String | No       | User's first name (usually only available on the first sign-up). |
| `lastName`  | String | No       | User's last name (usually only available on the first sign-up). |

**Note on Name:** Apple only provides the user's name the **first time** they authorize your app. The frontend must capture this from the Apple response and forward it to this endpoint to ensure the user's profile is created with their name.

**Example Request:**

```json
{
  "code": "c928...392.0.rru...z",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response — 200 OK (Success)**

Returns the standard sign-in response. An `HttpOnly` refresh token cookie is also set.

```json
{
  "statusCode": 200,
  "message": "Signin successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 900
  }
}
```

**Response — 400 Bad Request**

```json
{
  "statusCode": 400,
  "message": "Apple OAuth token exchange failed: {error_details}",
  "data": null
}
```

---

## 4. Frontend Implementation Guide

### 4.1 Capturing User Info (Web/Mobile)

When using Apple's libraries, you will receive a response similar to:

```javascript
{
  authorization: {
    code: "...",
    id_token: "..."
  },
  user: {
    name: { firstName: "John", lastName: "Doe" },
    email: "john.doe@example.com"
  }
}
```

**Crucial:** The `user` object is **only present on the very first login**. 
1.  Check if `user` exists in the Apple response.
2.  If it does, map `user.name.firstName` and `user.name.lastName` to the request body sent to `/auth/apple/callback`.
3.  The backend will extract the `email` directly from the `id_token`.

### 4.2 Handling Tokens

*   **Access Token**: Store the `accessToken` in memory (or secure storage). Use it in the `Authorization: Bearer <token>` header for all authenticated requests.
*   **Refresh Token**: The backend sets a cookie named `refresh_token` automatically. Ensure your frontend client is configured to send cookies (`withCredentials: true` in Axios or `credentials: 'include'` in fetch) when calling the `/auth/refresh` endpoint.

---

## 5. Testing

To test this flow, you will need:
1.  A valid Apple Developer Account.
2.  A Service ID configured with the correct `redirect_uri`.
3.  The backend must be running with valid `APPLE_TEAM_ID`, `APPLE_CLIENT_ID`, `APPLE_KEY_ID`, and `APPLE_PRIVATE_KEY` environment variables.
