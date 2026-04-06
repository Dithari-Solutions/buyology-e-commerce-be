# Website: Local-Only Purchasing & Delivery Logic

This document details the logic and frontend expectations for product browsing, purchasing restrictions, and delivery fee handling on the website.

## 1. Browsing Experience
*   **Browsing:** Customers can browse products from any supported country. The website should provide a country selector in the header or footer.
*   **Browsing Country Parameter:** When a user selects a different country to browse, the website should pass the corresponding `countryCode` parameter to the product APIs.

## 2. Purchasing & Checkout Restriction
*   **Rule:** Customers are only allowed to purchase items from their "Home Country," as defined in their user profile (`selectedCountryCode`).
*   **Add to Cart Logic:**
    *   If a user tries to add an item to their cart from a store outside their profile country, a `403 Forbidden` response will be returned by the backend.
    *   **Website Action:** Display a notification or modal explaining the restriction: "You can only purchase products from stores in your current country ([SelectedCountry])."
    *   Offer a link to the user's profile settings to change their home country if they have physically moved or are in a different country.

## 3. Delivery Classification & Fees
The backend calculates delivery methods and fees dynamically during the order creation or cart summary process:

*   **Distance-Based Classification:**
    *   **Distance ≤ 12.5 km (Express Delivery):** The order is classified as `EXPRESS_DELIVERY`.
    *   **Distance > 12.5 km (Regular Order):** The order is classified as `REGULAR_ORDER`.
*   **Fee Structure (for Express Delivery):**
    *   The thresholds are set in AED and converted to the cart's display currency:
        *   Cart Total < 150 AED (Converted) → **15 AED Express Fee**
        *   Cart Total ≥ 150 AED (Converted) → **10 AED Express Fee**
*   **Estimated Delivery Time:**
    *   `EXPRESS_DELIVERY`: "Within 30 minutes."
    *   `REGULAR_ORDER`: "2-3 business days" (can be customized per order).

## 4. Frontend Integration
*   **Cart Preview:** The cart summary should always show the `shippingFee` and `estimatedDeliveryTime` provided by the backend to avoid discrepancies.
*   **Profile Settings:** Ensure the profile settings allow the user to easily update their "Home Country" (selectedCountryCode) and "Preferred Currency" to match their actual location.
