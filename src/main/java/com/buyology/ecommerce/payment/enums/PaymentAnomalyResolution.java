package com.buyology.ecommerce.payment.enums;

/** Where a payment anomaly stands. Persisted as a plain string — see PaymentAnomaly. */
public enum PaymentAnomalyResolution {
    /** Recorded, alerted, waiting — on the sweep (auto-refundable kinds) or on a human. */
    OPEN,
    /** One replica has claimed the refund and is talking to the gateway. */
    AUTO_REFUNDING,
    /** The refund went out and is recorded against the anomaly. */
    AUTO_REFUNDED,
    /** A human reviewed it and closed it, with a note. */
    RESOLVED
}
