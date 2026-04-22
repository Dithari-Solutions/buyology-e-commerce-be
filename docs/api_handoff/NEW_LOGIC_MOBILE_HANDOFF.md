# [Mobile] Product Spec Logic Change

The backend logic for product specifications has changed from an **Additive Model** to a **Selection Model**.

### Key Changes
1.  **No Additional Prices in Specs**: The `additionalPrice` field has been **removed** from the `specs` and `selectedSpecs` arrays.
2.  **Variant-Based Pricing**: Each product now has multiple **Variants**. Each variant is a unique combination of spec options (e.g., MacBook Pro + 16GB RAM + 512GB SSD).
3.  **One Spec Per Category**: A product variant is defined by exactly one option from each spec category (RAM, Storage, Color, etc.).

### Integration Workflow
- **Price Display**: Always use the `storePrice` returned in the main product response. This price represents the default/selected variant.
- **Switching Specs**: When a user selects a different spec (e.g., changes RAM from 8GB to 16GB):
    1. Find the **Variant** in the `variants[]` array that matches the user's new combination of `specOptionIds`.
    2. Note: Pricing for specific variants is managed in the Store layer. If the user selects a combination that is actually a different SKU, you should ideally refresh the pricing by calling the Product Details API with that SKU/Variant context or handle the variant ID transition.
- **Cart**: When calling `POST /api/cart/{id}/items`, pass the `variantId` corresponding to the user's choices. The backend will automatically set the correct `unitPrice` based on that variant.

### Field Mappings
| Old Logic | New Logic |
| :--- | :--- |
| `specs[].options[].additionalPrice` | **DELETED** |
| `unitPrice` | `basePrice` of the selected **Variant** |
| Selection | User picks 1 option per Spec Group |
