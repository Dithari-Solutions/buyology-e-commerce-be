package com.buyology.ecommerce.refund.enums;

/**
 * Lifecycle of a customer-initiated refund request.
 *
 *   PENDING_REVIEW ──(admin approve)──→ APPROVED ──(customer choose method)──→
 *      DROPOFF_SELECTED  | COURIER_REQUESTED ──(staff mark received)──→ RECEIVED ──(admin pay)──→
 *      PAID  |  FAILED
 *   PENDING_REVIEW ──(admin reject)──→ REJECTED
 */
public enum RefundRequestStatus {
    PENDING_REVIEW,
    APPROVED,
    DROPOFF_SELECTED,
    COURIER_REQUESTED,
    RECEIVED,
    REJECTED,
    PAID,
    FAILED
}
