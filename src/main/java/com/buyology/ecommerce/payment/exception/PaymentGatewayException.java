package com.buyology.ecommerce.payment.exception;

/**
 * Thrown when a call to the external payment provider (Paymob) fails — network
 * error, non-2xx response, or an unexpected payload. Carrying a distinct type lets
 * {@code GlobalExceptionHandler} return a payment-specific 502 with a meaningful
 * reason instead of the opaque catch-all "An unexpected error occurred" (HTTP 500),
 * which made gateway misconfiguration impossible to diagnose from the client.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
