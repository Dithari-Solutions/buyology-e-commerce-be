package com.buyology.ecommerce.warranty.dto;

/**
 * A warranty card as listed on an order item, for the customer's order page and the admin's.
 * The storefront shows only the serial and links to Dithari's checker for the card itself.
 *
 * @param status ACTIVE, EXPIRING_SOON, EXPIRED or VOID, computed today
 */
public record OrderItemWarranty(
        String serialNumber,
        String status,
        String startDate,
        String endDate) {
}
