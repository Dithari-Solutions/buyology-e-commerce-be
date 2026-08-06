package com.buyology.ecommerce.repair.dto;

import com.buyology.ecommerce.repair.domain.RepairStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Repair team's generic status transition (e.g. mark COMPLETED or CANCELLED) with an optional
 * note. The customer is emailed with a stage label + note.
 */
public class UpdateRepairStatusRequest {

    @NotNull
    private RepairStatus status;

    @Size(max = 4000)
    private String note;

    public RepairStatus getStatus() { return status; }
    public void setStatus(RepairStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
