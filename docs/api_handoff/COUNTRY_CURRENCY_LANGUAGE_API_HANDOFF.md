# Country, Currency & Language — Frontend Implementation Guide

This document describes how the frontend should implement country selection, currency conversion, and language preferences. All conversion math happens on the backend — the frontend only needs to pass the right parameters and persist user preferences locally.

---

## Overview

| What                       | How it works                                                                 |
|----------------------------|------------------------------------------------------------------------------|
| Country selector           | User picks from active countries list or allows location detection           |
| Store filtering            | Products are scoped to stores inside the selected country                    |
| Currency display           | Backend converts store prices to the user's preferred currency automatically |
| Language                   | Passed as `lang` param; stored in profile                                    |
| Profile persistence        | Preferences saved on the user profile via `PATCH` endpoint                   |

---

## 1. Load Available Countries

Call this once on app load (or before showing the country picker). Cache the result in app state.

```
GET /api/countries/active
```

**Response:**
```json
{
  "data": [
    { "id": "uuid", "code": "UAE", "name": "United Arab Emirates", "currency": "AED", "isActive": true },
    { "id": "uuid", "code": "AZE", "name": "Azerbaijan", "currency": "AZN", "isActive": true }
  ]
}
```

Store the list in global state (e.g. Redux, Zustand, Context). Use `code` as the key — it's what all other endpoints expect.

---

## 2. Detect the User's Country

### Option A — Browser Geolocation (recommended on first visit)

```js
navigator.geolocation.getCurrentPosition(async (position) => {
  const { latitude, longitude } = position.coords;

  // Reverse-geocode using a free service or your own endpoint
  // Then match against the active countries list from step 1
  const countryCode = await reverseGeocodeToCountryCode(latitude, longitude);

  applyCountryPreference(countryCode);
});
```

> Use the `Geolocation.getCurrentPosition` browser API. If the user denies, fall back to a default country (e.g. `"UAE"`).

### Option B — Manual Selection

Show a dropdown populated from the countries list (step 1). When the user picks a country:

```js
function onCountrySelected(countryCode) {
  applyCountryPreference(countryCode);
}
```

---

## 3. Apply the Country Preference

This function runs both on first detection and whenever the user manually changes their country.

```js
async function applyCountryPreference(countryCode, overrideCurrency = null) {
  // 1. Persist to backend (for authenticated users)
  if (isLoggedIn()) {
    await fetch(`/api/users/${userId}/profile/country-preference?countryCode=${countryCode}${overrideCurrency ? `&currency=${overrideCurrency}` : ''}`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  }

  // 2. Save locally (works for guest users too)
  localStorage.setItem('selectedCountryCode', countryCode);

  const country = countriesList.find(c => c.code === countryCode);
  const currency = overrideCurrency ?? country?.currency ?? 'USD';
  localStorage.setItem('preferredCurrency', currency);

  // 3. Re-fetch products with the new country context
  reloadProducts();
}
```

**What the backend does automatically:**
- If `currency` is not provided, it derives the currency from the country (e.g. `UAE` → `AED`)
- If the user is from Azerbaijan browsing Dubai stores (`countryCode=UAE`) but passes `currency=AZN`, prices come back converted to AZN

---

## 4. Fetch Products with Country + Currency Context

Pass `countryCode` and `currency` as query params on every product request.

```js
function buildProductParams() {
  const lang     = localStorage.getItem('preferredLanguage') ?? 'EN';
  const country  = localStorage.getItem('selectedCountryCode') ?? 'UAE';
  const currency = localStorage.getItem('preferredCurrency') ?? 'AED';
  return new URLSearchParams({ lang, countryCode: country, currency });
}
```

### Get All Products
```
GET /api/product?lang=EN&countryCode=UAE&currency=AZN
```

### Get Product by ID
```
GET /api/product/{productId}?lang=EN&countryCode=UAE&currency=AZN
```

### Get Products by Category
```
GET /api/product/category/{categoryId}?lang=EN&countryCode=UAE&currency=AZN
```

### Search / Filter Products
```
GET /api/product/search?lang=EN&countryCode=UAE&currency=AZN&[...otherFilters]
```

**Product response now includes:**
```json
{
  "id": "...",
  "title": "iPhone 15 Pro",
  "storePrice": 2847.50,
  "currency": "AZN",
  "availableInSelectedCountry": true,
  "...": "all other existing fields"
}
```

- `storePrice` — lowest price across stores in the selected country, converted to the requested currency
- `currency` — the ISO 4217 code the price is expressed in
- `availableInSelectedCountry` — `true` if at least one store in the country carries this product; `false` if returned from a global search but not locally available; `null` if no country was specified

> Products are **filtered** to only those available in the selected country's stores. If no `countryCode` is passed, all active products are returned without pricing.

---

## 5. Display Prices

```js
function formatPrice(storePrice, currency) {
  if (storePrice == null) return 'Price unavailable';

  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 2
  }).format(storePrice);
}

// Example: formatPrice(2847.50, 'AZN') → "₼2,847.50" (in az-AZ locale)
// Example: formatPrice(3675.00, 'AED') → "AED 3,675.00"
```

---

## 6. Language Preference

The `lang` param controls which translation is returned for product titles, descriptions, spec group names, and spec values. Supported values: `EN`, `AZ`, `AR`.

### Saving language preference

```js
async function setLanguage(lang) {
  localStorage.setItem('preferredLanguage', lang);

  if (isLoggedIn()) {
    await fetch(`/api/users/${userId}/profile`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`
      },
      body: JSON.stringify({ preferredLanguage: lang })
    });
  }

  reloadProducts(); // re-fetch with new lang
}
```

---

## 7. Profile — Save All Preferences at Once

All three preferences can be updated in a single call via the profile `PATCH` endpoint.

```
PATCH /api/users/{userId}/profile
Content-Type: application/json

{
  "selectedCountryCode": "UAE",
  "preferredCurrency": "AZN",
  "preferredLanguage": "AZ"
}
```

**Response includes the full updated profile:**
```json
{
  "data": {
    "userId": "...",
    "firstName": "Firdovsi",
    "selectedCountryCode": "UAE",
    "preferredCurrency": "AZN",
    "preferredLanguage": "AZ",
    "...": "other profile fields"
  }
}
```

> On login, read `selectedCountryCode`, `preferredCurrency`, and `preferredLanguage` from the profile response and overwrite localStorage with the server values. This syncs preferences across devices.

---

## 8. Country Selector UI — Recommended UX

```
┌─────────────────────────────┐
│  🌍  United Arab Emirates   │  ← flag + name
│       Prices in: AED        │  ← auto-derived currency
│  [Change country ▾]         │
└─────────────────────────────┘
```

On click → show modal with:
1. Active countries list (from `GET /api/countries/active`)
2. Each row: flag emoji + country name + currency code
3. Optional: "Detect my location" button (calls geolocation API)
4. Optional: separate "Display currency" dropdown (lets users separate browsing country from display currency — e.g. browse Dubai stores but see prices in AZN)

When confirmed:
- Call `applyCountryPreference(countryCode, optionalCurrencyOverride)`
- Update the header display
- Products re-fetch automatically

---

## 9. Guest vs. Authenticated Users

| State         | Where preferences live          | How to restore                                |
|---------------|---------------------------------|-----------------------------------------------|
| Guest         | `localStorage` only             | Read on app load                              |
| Logged in     | `localStorage` + user profile   | On login, merge profile → localStorage        |
| Post-login    | Sync local → profile via `PATCH`| After login, push any local changes to server |

```js
async function onLoginSuccess(profile) {
  // Server is source of truth — overwrite local with profile values
  if (profile.selectedCountryCode) localStorage.setItem('selectedCountryCode', profile.selectedCountryCode);
  if (profile.preferredCurrency)   localStorage.setItem('preferredCurrency',   profile.preferredCurrency);
  if (profile.preferredLanguage)   localStorage.setItem('preferredLanguage',   profile.preferredLanguage);

  reloadProducts();
}
```

---

## 10. API Endpoints Summary

| Endpoint                                                    | Method | Auth     | Purpose                                       |
|-------------------------------------------------------------|--------|----------|-----------------------------------------------|
| `GET /api/countries/active`                                 | GET    | None     | List countries for the country picker         |
| `PATCH /api/users/{userId}/profile`                         | PATCH  | Required | Update language / currency / country in one call |
| `PATCH /api/users/{userId}/profile/country-preference`      | PATCH  | Required | Set country; auto-derives currency            |
| `GET /api/product?lang=&countryCode=&currency=`             | GET    | None     | Products filtered by country, prices converted |
| `GET /api/product/{id}?lang=&countryCode=&currency=`        | GET    | None     | Single product with country price             |
| `GET /api/product/category/{id}?lang=&countryCode=&currency=` | GET  | None     | Products by category in selected country      |
| `GET /api/product/search?lang=&countryCode=&currency=&...`  | GET    | None     | Search products with country + currency scope |

---

## 11. Exchange Rate Notes

- Rates are fetched live from [frankfurter.app](https://api.frankfurter.app) (European Central Bank data)
- Cached on the backend for **1 hour** — no need to poll
- The frontend never needs to do currency math — always pass `currency` param and display `storePrice` as-is
- If a currency pair is unsupported or the external API is down, the backend falls back to the store's native price (no conversion)
