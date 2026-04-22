# [Admin] Product Spec & Variant Logic Change

The Admin panel must be updated to reflect the new variant-based specification model.

### 1. Product Creation/Update
- **Removed Field**: `additionalPrice` has been removed from the `options` array inside `specs`. 
- **Validation**: When creating a product, ensure that for each `specGroup`, exactly one option is provided if it's a simple product, or multiple options are provided if they will be used to form `variants`.
- **Logic**: Each `ProductSpecGroup` (Category) should now represent a defining attribute of the product.

### 2. Pricing Management
- Since `additionalPrice` is gone, all pricing is now managed at the **Store level** for specific **Variants**.
- To set a price for a specific configuration (e.g., MacBook 16GB), the admin must:
    1. Create the Product and define the Specs/Options.
    2. Define the Variants (Combinations of those specs).
    3. Use the Store Management APIs to set the price for each `variantId` in each store.

### 3. API Payload Update
Remove the `additionalPrice` key from your JSON payloads when calling:
- `POST /api/admin/product`
- `PUT /api/admin/product/{id}`
- Any spec-related update endpoints.
