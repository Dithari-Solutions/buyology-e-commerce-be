package com.buyology.ecommerce.review.dto;

import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for moderating a review (approve or reject)")
public class ModerateReviewRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "New moderation status: APPROVED or REJECTED", example = "APPROVED")
    private ModerationStatus status;

    @NotNull(message = "Moderator ID is required")
    @Schema(description = "ID of the admin performing moderation", example = "c3d4e5f6-a7b8-9012-cdef-123456789012")
    private UUID moderatedBy;

    @Schema(description = "Rejection reason (required when status is REJECTED)", example = "Contains inappropriate language")
    private String rejectionReason;

    // =====================
    // Getters & Setters
    // =====================

    public ModerationStatus getStatus() { return status; }
    public void setStatus(ModerationStatus status) { this.status = status; }

    public UUID getModeratedBy() { return moderatedBy; }
    public void setModeratedBy(UUID moderatedBy) { this.moderatedBy = moderatedBy; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
