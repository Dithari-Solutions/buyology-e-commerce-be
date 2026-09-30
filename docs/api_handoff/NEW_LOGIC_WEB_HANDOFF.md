# [Web] Product Spec Logic Change

The product specification logic has been refactored to support a variant-centric **selection** model. Pricing stayed at the product listing: a variant identifies a SKU, not a price.

### 1. Specification Mapping
- The `specs` array in `GET /api/product/{id}` no longer contains `additionalPrice`.
- Every product spec group (e.g., RAM) should be rendered as a **single-choice selection** (Dropdown, Radio, or Chips).
- The user **must pick one option** from every available group to form a valid selection.

### 2. Pricing Logic (Crucial)
- Prices are no longer additive. You do **not** sum prices in the frontend.
- **One product, one price.** The product-level `storePrice` returned by the Product API (with
  `originalPrice` as the struck-through "was" figure) is the only price for the product. It comes from
  the store listing with the discount and its window already applied.
- If the user changes a spec, find the matching object in the `variants[]` array using the
  `specOptionIds` and keep its `id` for add-to-cart. **The price on screen does not move** —
  `variants[]` carries `id`, `sku` and `specOptionIds` only, with no price and no stock. Do not show or
  compute a per-variant price.
- There are no "pricing transitions": selecting a variant changes which SKU ships, not what it costs.

### 3. Add to Cart
- **Endpoint**: `POST /api/cart/{authCredentialId}/items`
- **Body Change**: Ensure you send the `variantId` that matches the user's selected specs.
- The `variantId` decides which SKU is reserved and which stock is decremented. It does **not** decide
  the price: the backend prices every line, with a variant or without, from the same store listing, so
  the basket charges exactly the `storePrice` the page advertised.
  - This changed: the backend previously charged a variant line the per-variant price recorded in the
    store, which no customer surface ever quoted. A genuine per-variant price is a **backend + both
    clients** change and is not shipped.
