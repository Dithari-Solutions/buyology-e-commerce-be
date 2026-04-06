package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class AdminTrackingUpdateRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;

    private String notes;

    private String locationDescription;

    /**
     * Carrier tracking number — required when setting status to SHIPPED on
     * a REGULAR_ORDER order.
     */
    private String trackingCode;

    /** Carrier name (e.g., DHL, FedEx) — optional, set alongside trackingCode. */
    private String carrierName;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
}
