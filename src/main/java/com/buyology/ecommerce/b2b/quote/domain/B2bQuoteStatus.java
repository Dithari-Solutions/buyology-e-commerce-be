package com.buyology.ecommerce.b2b.quote.domain;

/**
 * Lifecycle of a B2B Request-for-Quote.
 *
 * DRAFT      — the active B2B cart (one per credential). Member adds/edits lines here.
 * SUBMITTED  — member submitted the whole cart as one quote request; awaiting procurement.
 * QUOTED     — procurement priced every line and set a validUntil; awaiting member acceptance.
 * ACCEPTED   — member accepted the quoted prices before they expired; checkout is now enabled.
 * AWAITING_PAYMENT_VERIFICATION — member paid by bank transfer and uploaded proof of payment;
 *              procurement must validate the document before the order is placed.
 * REJECTED   — procurement declined to quote (with a reason).
 * EXPIRED    — the quoted prices lapsed past validUntil before the member accepted.
 * CANCELLED  — member cancelled a DRAFT/SUBMITTED/QUOTED quote.
 * ORDERED    — the accepted quote was checked out into an order (gateway payment) or its
 *              bank-transfer proof was validated by procurement.
 */
public enum B2bQuoteStatus {
    DRAFT,
    SUBMITTED,
    QUOTED,
    ACCEPTED,
    AWAITING_PAYMENT_VERIFICATION,
    REJECTED,
    EXPIRED,
    CANCELLED,
    ORDERED
}
