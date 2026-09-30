# [Admin] Product Spec & Variant Logic Change

The Admin panel must be updated to reflect the new variant-based specification model.

### 1. Product Creation/Update
- **Removed Field**: `additionalPrice` has been removed from the `options` array inside `specs`. 
- **Validation**: When creating a product, ensure that for each `specGroup`, exactly one option is provided if it's a simple product, or multiple options are provided if they will be used to form `variants`.
- **Logic**: Each `ProductSpecGroup` (Category) should now represent a defining attribute of the product.

### 2. Pricing Management
- Since `additionalPrice` is gone, all pricing is managed at the **Store level**, on the **store
  listing** (`store_products`) — one price per product per store, discount and window included.
- **A variant does not carry a billable price.** The per-variant `storePrice` on the store-variant
  endpoints is **recorded, not billed**: every cart line, with a variant or without, is charged the
  listing's price, because that listing price is the only figure any card, rail, search result or
  product page quotes. A `variantId` decides which SKU ships and which stock is decremented.
  (`effectivePrice` on a store-variant response is therefore the **parent listing's** effective price,
  and is the same for every variant of that listing.)
- To price a product in a store, the admin must:
    1. Create the Product and define the Specs/Options.
    2. Define the Variants (Combinations of those specs) — for SKUs and per-variant **stock**.
    3. Use the Store Management APIs to set the **listing** price (and any discount window) for the
       product in each store. Per-variant prices may be recorded for bookkeeping, but nothing bills
       them.
- Charging different amounts for different variants is a **backend + both clients** change and is not
  shipped. Do not build an admin flow that promises it.

### 3. API Payload Update
Remove the `additionalPrice` key from your JSON payloads when calling:
- `POST /api/admin/product`
- `PUT /api/admin/product/{id}`
- Any spec-related update endpoints.
