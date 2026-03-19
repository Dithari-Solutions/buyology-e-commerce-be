package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for updating an admin answer to a question")
public class UpdateQuestionAnswerRequest {

    @NotBlank(message = "Answer body is required")
    @Schema(description = "Updated answer text", example = "It supports HDMI 2.1, enabling 4K at 120Hz.")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
