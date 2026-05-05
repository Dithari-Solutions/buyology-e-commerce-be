package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AdminStatusUpdateRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;

    private String notes;

    /** For EXPRESS: assign a courier when moving to COURIER_ASSIGNED. */
    private UUID courierUserId;

    /** Reason supplied when status is CANCELLED (admin or customer-originated). */
    private String cancellationReason;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getCourierUserId() { return courierUserId; }
    public void setCourierUserId(UUID courierUserId) { this.courierUserId = courierUserId; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
}
