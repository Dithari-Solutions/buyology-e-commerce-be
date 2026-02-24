package com.buyology.ecommerce.common.response;

import com.buyology.ecommerce.auth.dto.SignInResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message) {
        return ResponseEntity.ok(new ApiResponse<>(200, message, data));
    }

    // ------------------------
    // 201 Created
    // ------------------------
    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, message, data));
    }

    // ------------------------
    // Generic failure
    // ------------------------
    public static <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(status.value(), message, null));
    }

    // ------------------------
    // Convenience for signin success
    // ------------------------
    public static ResponseEntity<ApiResponse<SignInResponse>> signinSuccess(
            String accessToken, String refreshToken, long expiresIn) {
        SignInResponse response = new SignInResponse(accessToken, refreshToken, expiresIn);
        return ResponseEntity.ok(new ApiResponse<>(200, "Signin successful", response));
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
