package com.buyology.ecommerce.payout.enums;

/**
 *   PENDING ──(admin mark paid)──→ PAID
 *   PENDING ──(admin reject)─────→ REJECTED
 */
public enum PayoutRequestStatus {
    PENDING,
    PAID,
    REJECTED
}
