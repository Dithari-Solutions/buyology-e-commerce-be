package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for providing an admin answer to a question")
public class CreateQuestionAnswerRequest {

    @NotNull(message = "Admin ID is required")
    @Schema(description = "ID of the admin providing the answer", example = "c3d4e5f6-a7b8-9012-cdef-123456789012")
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
