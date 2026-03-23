package com.buyology.ecommerce.courier.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateCourierStatusRequest {

    @NotNull
    private CourierStatus status;

    @Size(max = 500)
    private String reason;

    public UpdateCourierStatusRequest() {}

    public UpdateCourierStatusRequest(CourierStatus status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public CourierStatus getStatus() { return status; }
    public void setStatus(CourierStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
