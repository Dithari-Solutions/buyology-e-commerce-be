package com.buyology.ecommerce.support.domain;

/** Lifecycle of a support ticket. The team moves it; RESOLVED/CLOSED are the terminal goals. */
public enum SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_FOR_CUSTOMER,
    RESOLVED,
    CLOSED
}
