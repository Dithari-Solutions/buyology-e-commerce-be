package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for voting on a review's helpfulness")
public class VoteReviewRequest {

    @NotNull(message = "User ID is required")
    @Schema(description = "ID of the voting user", example = "d4e5f6a7-b8c9-0123-defa-234567890123")
    private UUID userId;

    @NotNull(message = "isHelpful is required")
    @Schema(description = "true = helpful, false = not helpful", example = "true")
    private Boolean isHelpful;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Boolean getIsHelpful() { return isHelpful; }
    public void setIsHelpful(Boolean isHelpful) { this.isHelpful = isHelpful; }
}
