# [Mobile] Product Spec Logic Change

The backend logic for product specifications has changed from an **Additive Model** to a **Selection Model**.

### Key Changes
1.  **No Additional Prices in Specs**: The `additionalPrice` field has been **removed** from the `specs` and `selectedSpecs` arrays.
2.  **Variant-Based *Selection*, NOT variant-based pricing**: Each product now has multiple **Variants**. Each variant is a unique combination of spec options (e.g., MacBook Pro + 16GB RAM + 512GB SSD). A variant identifies a **SKU**, not a price — see the pricing rule below.
3.  **One Spec Per Category**: A product variant is defined by exactly one option from each spec category (RAM, Storage, Color, etc.).

### Integration Workflow
- **Price Display — one product, one price.** Always use the product-level `storePrice` from the main
  product response (with `originalPrice` as the struck-through "was" figure). It is resolved from the
  store listing with the discount and its window already applied. It is the **only** price for the
  product and it does **not** change when the shopper picks a different spec or variant.
- **Switching Specs**: When a user selects a different spec (e.g., changes RAM from 8GB to 16GB):
    1. Find the **Variant** in the `variants[]` array that matches the user's new combination of `specOptionIds`.
    2. Keep that `variantId` for add-to-cart. **Do not re-fetch or recompute a price, and do not show a
       per-variant price** — `variants[]` carries `id`, `sku` and `specOptionIds` only, with no price
       and no stock. The figure already on screen is the figure that will be charged.
- **Cart**: When calling `POST /api/cart/{id}/items`, pass the `variantId` corresponding to the user's
  choices. It decides which SKU is reserved and which stock is decremented; it does **not** decide the
  price. The backend prices every line — with a variant or without — from the same store listing, so the
  card, the PDP, the basket, the order and the abandoned-cart email all quote one number.
  - This changed: the backend previously charged a variant line `store_product_variants.store_price`
    while every customer surface advertised the listing price. If a variant genuinely needs to cost a
    different amount, that is a **backend + both clients** change and is not shipped.

### Field Mappings
| Old Logic | New Logic |
| :--- | :--- |
| `specs[].options[].additionalPrice` | **DELETED** |
| `unitPrice` | product-level `storePrice` of the **listing** (the variant does not change it) |
| Selection | User picks 1 option per Spec Group |
