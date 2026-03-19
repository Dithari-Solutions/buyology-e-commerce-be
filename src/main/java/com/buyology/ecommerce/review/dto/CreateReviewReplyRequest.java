package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for adding an admin reply to a review")
public class CreateReviewReplyRequest {

    @NotNull(message = "Admin ID is required")
    @Schema(description = "ID of the admin writing the reply", example = "c3d4e5f6-a7b8-9012-cdef-123456789012")
    private UUID adminId;

    @NotBlank(message = "Reply body is required")
    @Schema(description = "The reply text", example = "Thank you for your feedback! We're glad you enjoyed it.")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getAdminId() { return adminId; }
    public void setAdminId(UUID adminId) { this.adminId = adminId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
