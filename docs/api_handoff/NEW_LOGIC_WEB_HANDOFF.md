# [Web] Product Spec Logic Change

The product specification and pricing logic has been refactored to support a variant-centric selection model.

### 1. Specification Mapping
- The `specs` array in `GET /api/product/{id}` no longer contains `additionalPrice`.
- Every product spec group (e.g., RAM) should be rendered as a **single-choice selection** (Dropdown, Radio, or Chips).
- The user **must pick one option** from every available group to form a valid selection.

### 2. Pricing Logic (Crucial)
- Prices are no longer additive. You do **not** sum prices in the frontend.
- The `storePrice` returned in the Product API is the base price for the default variant.
- If the user changes a spec, find the matching object in the `variants[]` array using the `specOptionIds`.
- All pricing transitions should be handled by the backend. When a user selects a configuration that leads to a different Variant SKU, the `variantId` must be used for adding to the cart.

### 3. Add to Cart
- **Endpoint**: `POST /api/cart/{authCredentialId}/items`
- **Body Change**: Ensure you send the `variantId` that matches the user's selected specs.
- The backend will ignore any additive spec prices and fetch the fixed price defined for that specific variant in the store.
