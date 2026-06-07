package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@Schema(description = "Request body for providing an admin answer to a question")
public class CreateQuestionAnswerRequest {

    @Deprecated // Ignored — the answering admin is resolved from the authenticated JWT principal.
    @Schema(description = "Deprecated/ignored; resolved from the authenticated admin", hidden = true)
    private UUID adminId;

    @NotBlank(message = "Answer body is required")
    @Schema(description = "The answer text", example = "Yes, it supports HDMI 2.0 for 4K output at 60Hz.")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getAdminId() { return adminId; }
    public void setAdminId(UUID adminId) { this.adminId = adminId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
