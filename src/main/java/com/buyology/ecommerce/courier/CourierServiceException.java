package com.buyology.ecommerce.courier;

public class CourierServiceException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public CourierServiceException(int statusCode, String body) {
        super("Courier service returned " + statusCode);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() { return statusCode; }
    public String getBody()    { return body; }
}
