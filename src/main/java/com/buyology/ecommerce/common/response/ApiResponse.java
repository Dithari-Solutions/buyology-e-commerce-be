package com.buyology.ecommerce.common.response;

import com.buyology.ecommerce.auth.dto.SignInResponse;

public class ApiResponse<T> {
    private int statusCode;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int statusCode, String message, T data) {
        this.statusCode = statusCode;
        this.message = message;
        this.data = data;
    }

    // ------------------------
    // Generic success
    // ------------------------
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(200, message, data);
    }

    // ------------------------
    // Generic failure
    // ------------------------
    public static <T> ApiResponse<T> failure(int statusCode, String message) {
        return new ApiResponse<>(statusCode, message, null);
    }

    // ------------------------
    // Convenience for signin
    // ------------------------
    public static ApiResponse<SignInResponse> signinSuccess(String accessToken, String refreshToken, long expiresIn) {
        SignInResponse response = new SignInResponse(accessToken, refreshToken, expiresIn);
        return new ApiResponse<>(200, "Signin successful", response);
    }

    // ------------------------
    // Getters & Setters
    // ------------------------
    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
