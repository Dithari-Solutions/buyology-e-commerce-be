package com.buyology.ecommerce.support.domain;

/** What the customer needs help with. SOFTWARE_BUG covers site defects; OTHER catches the rest. */
public enum SupportCategory {
    SOFTWARE_BUG,
    ORDER_ISSUE,
    PAYMENT_ISSUE,
    ACCOUNT_ISSUE,
    OTHER
}
