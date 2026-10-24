package com.buyology.ecommerce.warranty.domain;

/**
 * What is stored on a card. Expiry is not a stored state: whether a card is active, expiring soon
 * or expired is read from its dates on the day someone looks (see {@code WarrantyCardView}).
 */
public enum WarrantyCardStatus {
    ACTIVE,
    /** Withdrawn by an admin, typically after the unit was returned or refunded. Reversible. */
    VOID
}
