package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for marking a question as helpful")
public class VoteQuestionRequest {

    @NotNull(message = "User ID is required")
    @Schema(description = "ID of the voting user", example = "d4e5f6a7-b8c9-0123-defa-234567890123")
    private UUID userId;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
}
