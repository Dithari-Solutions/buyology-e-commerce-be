# Mobile App Bug Fix & Data Integration Guide

This document provides the backend details, API endpoints, and field names required to fix the reported bugs in the Buyology mobile application.

---

## 1. Header & Navigation

### Email Icon in Header
- **Fix**: Remove from frontend UI code. This is a local UI change.

### Arabic Layout (RTL) - Bottom Nav Circle
- **Fix**: This is a CSS/Layout issue in the mobile frontend. 
- **Data**: No backend change required. Ensure the active item indicator follows the RTL direction.

---

## 2. Screens & Directions

### Notification Screen (Missing)
- **New API - Get History**: `GET /api/v1/notifications/history`
- **New API - Unread Count**: `GET /api/v1/notifications/unread-count`
- **New API - Mark as Read**: `PUT /api/v1/notifications/history/{id}/read`
- **Response**: List of `NotificationHistory` objects (`id`, `title`, `body`, `type`, `isRead`, `createdAt`).

### What We Offer & Category Redirects
- **Repair, Rent, Sell**: These should redirect to a **"Coming Soon"** page in the app.
- **Categories Redirect**:
  - **API**: `GET /api/product/category/{categoryId}`
  - **Logic**: Use the `categoryId` from the clicked container to fetch products.

### Continue Shopping (Cart Screen)
- **Fix**: Update the button listener to navigate to the **All Products** screen.
- **API**: Use `GET /api/product?lang=EN` to show the full catalog.

---

## 3. Home Screen Data

### Super Deals
- **API**: `GET /api/product/search-elastic?query=&lang=EN&isSuperDeal=true` (or use the search API with `isSuperDeal=true`).
- **Data Field**: Use `storePrice` for the price and `title` for the name.

### Flash Sale
- **API**: `GET /api/product/flash-sale?lang=EN&countryCode=UAE&currency=AED` (also takes `lat`/`lng`, `page`, `size`).
- **Data Source**: real discount windows on the store listing, soonest-ending first. Only products whose
  price *for the requested market* is discounted right now are returned, so a page can come back shorter
  than `size` — page on "fewer than asked for means the end", not on an exact count. **`isSuperDeal` is no
  longer the flash sale** — that flag is the separate, manually curated *Super Deals* rail above and has
  no price and no dates. Point the Flash Sale rail at this endpoint or the app keeps rendering the old
  editorial list under the wrong name.
- **Data Fields**: `storePrice` is the sale price (already discounted, already in the display currency),
  `originalPrice` is the struck-through "was" price, `flashSaleEndsAt` is the countdown target (ISO-8601
  instant, UTC) and `onFlashSale` is true. These three appear on every product response, not just this
  endpoint, so a product card anywhere in the app can show a sale badge. A sale that has not STARTED yet
  sends `flashSaleStartsAt` instead, alongside the pre-sale price and with no `onFlashSale`: the product
  is not on sale yet, and the field says when it will be. Never expect both dates on one product.
- **Expiry**: nothing has to be switched off. A product leaves the rail, loses `onFlashSale` and goes
  back to its full price by itself the moment its window closes — so a countdown that reaches zero and a
  product that vanishes on refresh are both correct behaviour, not a bug.
- **Caching**: this endpoint is deliberately NOT cached, so the countdown you read is current. Other
  catalogue endpoints are cached for up to 60s — but never PAST a sale boundary: any cached body that
  quotes a sale (or a sale about to start) expires at that instant, on the server and in the browser's
  own cache, so a card cannot go on showing a price the cart will not charge. Inside those 60 seconds a
  badge can still lag an admin's manual price edit, which has no date for anything to expire at.
- **Checkout**: prices are re-checked when the order is placed, and the direction decides what happens.
  If the basket got **cheaper** (a sale started), the order is simply placed at the lower price — no error.
  If it got **dearer** (a sale ended), placing the order returns **409** with a message written for the
  customer: show it, reload the cart, and let them confirm the new price rather than retrying the old one.
  An unchanged total never errors. Either way the cart response carries `priceChanged: true` on the lines
  that moved, with `previousUnitPrice`, so the basket screen can say what changed.

### Popular For You
- **Requirement**: Only 4 products.
- **Fix**: Call any product list API and limit the result to the first 4 items locally or use a limit parameter if supported.

---

## 4. Product Details Screen (PDP)

### Price & Spec Selection
- **Issue**: Price not updating when changing spec.
- **`additionalPrice` DOES NOT EXIST. Delete any code that reads it.** This guide previously
  documented `specs[].options[].additionalPrice` and the formula `DisplayedPrice = BasePrice +
  additionalPrice`. There has never been such a column or such a field: `ProductSpecOption` carries
  `value`, `unit` and `colorCode`, and `SpecOptionDto` carries no price. Both clients implemented the
  formula and the term has always evaluated to `+0`, so the price never moved when a spec changed —
  which is the bug reported above. Spec options are **descriptive**; a spec that costs more money is
  modelled as a **variant**.
- **The real contract — one product, one price.** There is exactly **one** number to show for a
  product, and it is the product-level **`storePrice`** (with `originalPrice` as the struck-through
  "was" figure and `currency` as the code). It is resolved from the store listing, discount and window
  already applied. It does **not** change when the shopper picks a different spec or variant, and there
  is nothing to add to it.
  - `variants[]` carries `id`, `sku` and `specOptionIds` only — **no price and no stock.** A variant is
    an identity, not a price: it says which SKU will be shipped. Selecting one changes the SKU, not the
    figure on screen.
  - This is also exactly what the cart charges. Every line — with a variant or without — is priced from
    the same store listing, so the card, the PDP, the basket, the order and the abandoned-cart email
    all quote one number. Sending `variantId` on add-to-cart is still correct and still required for a
    variant product: it decides which SKU is reserved and which stock is decremented. It does not
    change the price.
  - **Do not display a per-variant price, and do not compute one.** A card or rail that adds
    `variants[0]` is fine — the price it showed is the price that will be charged.
  - If a variant genuinely needs to cost a different amount, that is a **backend + both clients**
    change and is not shipped. Say so rather than inventing a figure client-side; an invented one is
    how the app ends up advertising a number the basket does not charge, which is the bug this section
    exists to close.
- **Missing Name**: The spec group name is in `specs[].name`. The option name is in `specs[].options[].value`.

### Related Products (Mock Data)
- **API**: `GET /api/product/{productId}/related`
- **Parameters**: `lang`, `countryCode` (optional).
- **Note**: This returns 4 products from the same category.

### Share Button
- **Logic**: Use the `slug` from the product response to generate a deep link: `buyology://product/{slug}` or a web URL `https://buyology.com/product/{slug}`.

### Price Visibility & Cart Availability
- **Fix**: The backend now always returns a `storePrice` and `currency` for the Product Details Page. If the product is not available in the user's selected country, it falls back to the globally cheapest available price.
- **Frontend Logic**:
  - **Always show the price** from `storePrice`.
  - Check the `availableInSelectedCountry` boolean.
  - If `availableInSelectedCountry` is `false`: Disable the **"Add to Cart"** button and show a status message "Not available in your country".
  - If `true`: Enable the cart button.
- **Goal**: Allow users to browse all products and see their value regardless of their current location.

---

## 5. Favorites & Cart

### Favorite Screen (Sign-in Message)
- **Issue**: Shows sign-in message when empty.
- **Backend Behavior**: `GET /api/favorites/{authCredentialId}` returns `200 OK` with an empty `items` list if the user has no favorites. It returns `404` only if the `authCredentialId` is invalid.
- **Fix**: Frontend should check `items.length === 0` to show "No favorites yet" instead of "Please sign in".

---

## 6. Mini Game Integration

### Game Logic & APIs
- **Get Daily Game**: `GET /api/game/daily-type` (Returns `QUIZ` or `MINI_GAME`).
- **Submit Result**: `POST /api/game/submit`
  - **Body**: 
    ```json
    {
      "gameType": "MINI_GAME",
      "score": 100,
      "success": true
    }
    ```
- **Rewards**: Each success grants **10 tokens** to the user profile.

---

## 7. Field Mapping Reference
| UI Component | Backend Field |
| :--- | :--- |
| Product Title | `title` |
| Product Price | `storePrice` (localized) |
| Currency | `currency` (e.g., "AZN") |
| Spec Group Name | `specs[].name` |
| Spec Option Value | `specs[].options[].value` |
| Price (the only one) | `storePrice` (discounted; `originalPrice` is the "was" figure) |
| Selected variant | `variants[].id` — send as `variantId`; it does **not** change the price |
| Notification Title | `title` |
| Notification Body | `body` |
