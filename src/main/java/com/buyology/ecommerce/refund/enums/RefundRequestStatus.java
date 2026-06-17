package com.buyology.ecommerce.refund.enums;

/**
 * Lifecycle of a customer-initiated refund request.
 *
 *   PENDING_REVIEW ──(admin approve)──→ APPROVED ──(customer choose method)──→
 *      DROPOFF_SELECTED                                    ──(staff mark received)──→ RECEIVED ──(admin pay)──→
 *      COURIER_FEE_PENDING ──(courier fee paid via Paymob)──→ COURIER_REQUESTED ──┘
 *      PAID  |  FAILED
 *   PENDING_REVIEW ──(admin reject)──→ REJECTED
 *
 * COURIER_FEE_PENDING is the gate: when the customer picks courier pickup we snapshot the
 * fee and create a standalone Paymob charge, but the request only advances to
 * COURIER_REQUESTED once that fee payment succeeds. The fee is collected on top of the
 * order — it is NOT deducted from the refund.
 */
public enum RefundRequestStatus {
    PENDING_REVIEW,
    APPROVED,
    DROPOFF_SELECTED,
    COURIER_FEE_PENDING,
    COURIER_REQUESTED,
    RECEIVED,
    REJECTED,
    PAID,
    FAILED
}
