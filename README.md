# Buyology E-Commerce Backend

## Frontend Auth Integration Guide

This guide covers how the frontend should integrate with the **Sign In**, **Verify OTP**, **Token Refresh**, and **Logout** endpoints, including how to handle the HttpOnly refresh token cookie.

---

## How the Token System Works

| Token | Delivered via | Storage | Lifetime |
|---|---|---|---|
| **Access token** | JSON response body | Memory (JS variable / React state) | Configurable (short-lived) |
| **Refresh token** | `Set-Cookie` header (HttpOnly) | Browser cookie — inaccessible to JS | 7 days |

The refresh token cookie is set with:
- `HttpOnly=true` — JS cannot read it, protects against XSS
- `SameSite=Strict` — not sent on cross-site requests
- `Path=/auth/refresh` — the browser only sends it to that exact path
- `Secure=true` in production

Because the cookie is HttpOnly, **you never store or touch the refresh token in JavaScript**. The browser manages it automatically.

---

## Verify OTP (Sign Up completion)

After `POST /auth/signup` sends the OTP email, submit the code to this endpoint. On success it **automatically signs the user in** — the response is identical to Sign In (access token in body + refresh token cookie). No separate sign-in call is needed.

### Request

```
POST /auth/verify-otp
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "otpCode": "123456"
}
```

### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Signin successful",
  "data": {
    "accessToken": "<jwt>",
    "expiresIn": 900
  }
}
```

The response also includes a `Set-Cookie` header, exactly like Sign In:

```
Set-Cookie: refresh_token=<token>; Path=/auth/refresh; HttpOnly; SameSite=Strict; Max-Age=604800
```

### What to do on success

1. Store `data.accessToken` in memory.
2. Store `data.expiresIn` and schedule a proactive refresh.
3. Redirect to the app — the user is now authenticated.

### Example (fetch)

```js
async function verifyOtp(email, otpCode) {
  const res = await fetch('/auth/verify-otp', {
    method: 'POST',
    credentials: 'include', // required to store the refresh token cookie
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, otpCode }),
  });

  const json = await res.json();

  if (!res.ok) {
    throw new Error(json.message);
  }

  // User is now signed in — handle exactly like a sign-in response
  return {
    accessToken: json.data.accessToken,
    expiresIn: json.data.expiresIn,
  };
}
```

### Error Responses

| Status | Meaning |
|---|---|
| `400` | No pending verification for this email — user must sign up again |
| `401` | Wrong OTP code (message includes remaining attempts) |
| `410` | OTP expired — user must sign up again |
| `429` | Too many incorrect attempts — user must sign up again |

---

## Sign In

### Request

```
POST /auth/signin
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Signin successful",
  "data": {
    "accessToken": "<jwt>",
    "expiresIn": 900
  }
}
```

The response also includes a `Set-Cookie` header:

```
Set-Cookie: refresh_token=<token>; Path=/auth/refresh; HttpOnly; SameSite=Strict; Max-Age=604800
```

You do not need to do anything with this header — the browser stores it automatically.

### What to do on success

1. Store `data.accessToken` in memory (e.g. React state, Zustand, a module-level variable).
2. Store `data.expiresIn` so you know when to proactively refresh.
3. **Do not** store the access token in `localStorage` or `sessionStorage` (XSS risk).

### Example (fetch)

```js
async function signIn(email, password) {
  const res = await fetch('/auth/signin', {
    method: 'POST',
    credentials: 'include', // required so the browser stores the Set-Cookie
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  const json = await res.json();

  if (!res.ok) {
    throw new Error(json.message);
  }

  return {
    accessToken: json.data.accessToken,
    expiresIn: json.data.expiresIn, // seconds
  };
}
```

> `credentials: 'include'` is required for the browser to accept and later send the HttpOnly cookie.

### Error Responses

| Status | Meaning |
|---|---|
| `400` | Invalid email format |
| `401` | Wrong email or password |
| `500` | Server error |

---

## Refresh Access Token

When the access token expires, call this endpoint to get a new one. The browser automatically sends the `refresh_token` cookie because the request goes to `/auth/refresh`.

### Request

```
POST /auth/refresh
```

No body needed. The cookie is sent automatically by the browser.

### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "<new-jwt>",
    "expiresIn": 900
  }
}
```

A new `Set-Cookie` is also returned, rotating the refresh token (the old one is revoked).

### What to do on success

Replace the in-memory access token with the new `data.accessToken`.

### Example (fetch)

```js
async function refreshAccessToken() {
  const res = await fetch('/auth/refresh', {
    method: 'POST',
    credentials: 'include', // sends the HttpOnly cookie
  });

  if (res.status === 401) {
    // Refresh token expired or revoked — redirect to login
    redirectToLogin();
    return null;
  }

  const json = await res.json();
  return {
    accessToken: json.data.accessToken,
    expiresIn: json.data.expiresIn,
  };
}
```

### Error Responses

| Status | Meaning | Action |
|---|---|---|
| `401` | No cookie, expired, or revoked token | Clear state and redirect to login |

---

## Proactive Token Refresh Strategy

Rather than waiting for a `401` on an API call, schedule a refresh slightly before the access token expires.

```js
let accessToken = null;
let refreshTimer = null;

async function handleSignIn(email, password) {
  const { accessToken: token, expiresIn } = await signIn(email, password);
  setAccessToken(token, expiresIn);
}

function setAccessToken(token, expiresIn) {
  accessToken = token;
  clearTimeout(refreshTimer);

  // Refresh 30 seconds before expiry
  const refreshInMs = (expiresIn - 30) * 1000;
  refreshTimer = setTimeout(async () => {
    const result = await refreshAccessToken();
    if (result) {
      setAccessToken(result.accessToken, result.expiresIn);
    }
  }, refreshInMs);
}

function getAccessToken() {
  return accessToken;
}
```

---

## Attaching the Access Token to API Requests

Include the access token in the `Authorization` header for all authenticated requests.

```js
async function apiFetch(url, options = {}) {
  const token = getAccessToken();

  const res = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: {
      ...options.headers,
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  // Handle expired token mid-flight
  if (res.status === 401) {
    const refreshed = await refreshAccessToken();
    if (!refreshed) return res; // redirect already handled

    // Retry the original request once with the new token
    return fetch(url, {
      ...options,
      credentials: 'include',
      headers: {
        ...options.headers,
        'Content-Type': 'application/json',
        Authorization: `Bearer ${refreshed.accessToken}`,
      },
    });
  }

  return res;
}
```

---

## Logout

### Request

```
POST /auth/logout
```

No body needed. The browser sends the `refresh_token` HttpOnly cookie automatically (same as `/auth/refresh`).

### What the server does

1. Reads the `refresh_token` cookie.
2. Marks the token as revoked in the database (one-time use — it cannot be used again even if the cookie were somehow replayed).
3. Returns a `Set-Cookie` header that immediately expires the cookie in the browser (`Max-Age=0`).

### Success Response — `200 OK`

```json
{
  "status": 200,
  "message": "Logged out successfully",
  "data": null
}
```

The response also includes:

```
Set-Cookie: refresh_token=; Path=/auth/refresh; HttpOnly; SameSite=Strict; Max-Age=0
```

This forces the browser to delete the cookie.

### What to do on success

1. Clear the in-memory access token.
2. Cancel the proactive refresh timer.
3. Redirect to the login page.

### Example (fetch)

```js
async function logout() {
  await fetch('/auth/logout', {
    method: 'POST',
    credentials: 'include', // sends the HttpOnly cookie so the server can revoke it
  });

  // Clear in-memory state
  accessToken = null;
  clearTimeout(refreshTimer);

  redirectToLogin();
}
```

> Always call the logout endpoint rather than just clearing local state. Without calling the endpoint, the refresh token remains valid in the database and could still be used to obtain new access tokens until it naturally expires (7 days).

### Notes

- If no cookie is present (e.g. the user is already logged out), the server still returns `200 OK` — no action is taken server-side but the response is safe to handle the same way.
- After a **password reset**, all active refresh tokens for the account are revoked server-side automatically. You don't need to call `/auth/logout` explicitly in that flow, but you should still clear local state and redirect to login.

---

## CORS Requirement

If your frontend is served from a different origin than the backend (e.g. `localhost:3000` vs `localhost:8080` during development), the backend must allow credentials in its CORS configuration. The frontend must also always use `credentials: 'include'` on all requests that need the cookie.
