package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class CourierTrackingUpdateRequest {

    /**
     * The new status to apply. Couriers may only use:
     * PICKED_UP, IN_TRANSIT, DELIVERED, FAILED.
     */
    @NotNull(message = "status is required")
    private OrderStatus status;

    private String notes;

    /** GPS latitude of the courier at the time of this update. */
    private Double latitude;

    /** GPS longitude of the courier at the time of this update. */
    private Double longitude;

    private String locationDescription;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }
}
