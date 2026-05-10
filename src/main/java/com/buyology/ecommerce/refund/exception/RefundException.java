package com.buyology.ecommerce.refund.exception;

/**
 * Thrown by the refund module for business-rule violations: ineligibility, duplicate
 * requests, invalid state transitions, validation errors. Maps to HTTP 409 via
 * {@link com.buyology.ecommerce.common.exception.GlobalExceptionHandler#handleIllegalState}
 * because it extends IllegalStateException.
 */
public class RefundException extends IllegalStateException {

    public RefundException(String message) {
        super(message);
    }
}
