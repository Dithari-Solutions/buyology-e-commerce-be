# Mobile App: Local-Only Purchasing & Delivery Logic

This document outlines the updated logic for product browsing, purchasing restrictions, and delivery fee calculations in the mobile app.

## 1. Product Browsing vs. Purchasing
*   **Browsing:** Customers can browse products from any supported country. To browse a specific country, the app should pass the `countryCode` parameter (ISO 3166-1 alpha-3, e.g., `UAE`, `AZE`) to the product listing and detail APIs.
*   **Purchasing Restriction:** Customers can **only purchase** products from stores located in their "Home Country."
    *   The "Home Country" is defined by the `selectedCountryCode` field in the user's profile.
    *   If a customer attempts to add a product to their cart from a store in a different country, the backend will return a `403 Forbidden` error with a clear message.
    *   **App Action:** If the backend returns this error, the app should prompt the user that they can only buy from their home country and offer them to switch their profile country if they are actually in that country.

## 2. Delivery Method Classification
The delivery method is automatically determined based on the distance between the store and the customer's delivery address:

*   **EXPRESS_DELIVERY:**
    *   Triggered when the distance is **≤ 12.5 km** (approximately 30 minutes delivery radius).
    *   Fulfillment: Handled by a local courier with real-time GPS tracking.
*   **REGULAR_ORDER:**
    *   Triggered when the distance is **> 12.5 km** but within the same country.
    *   Fulfillment: Handled by standard shipping or store-managed delivery.
    *   Estimate: "2-3 business days" (can be customized per order).

## 3. Delivery Fees (Express Delivery Only)
Delivery fees for `EXPRESS_DELIVERY` are calculated based on the cart's total price. The thresholds are defined in AED and automatically converted to the cart's currency.

| Cart Total (Base AED) | Express Delivery Fee (Base AED) |
|-----------------------|---------------------------------|
| < 150 AED             | 15 AED                          |
| ≥ 150 AED             | 10 AED                          |

*   **Note:** `REGULAR_ORDER` delivery is currently free (0.00) unless otherwise specified.

## 4. UI/UX Recommendations
*   **Express Badge:** Show an "Express" badge only for products where `expressDelivery: true` in the API response (based on user's current lat/lng).
*   **Estimated Delivery:** Always display the `estimatedDeliveryTime` returned in the order summary or cart preview.
*   **Country Selector:** Provide a clear way for users to see which country they are currently "in" (for purchasing) vs. which country they are "browsing."
