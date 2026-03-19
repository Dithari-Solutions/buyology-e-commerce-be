package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for updating an admin reply on a review")
public class UpdateReviewReplyRequest {

    @NotBlank(message = "Reply body is required")
    @Schema(description = "Updated reply text", example = "We apologize for any inconvenience and will look into this.")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
