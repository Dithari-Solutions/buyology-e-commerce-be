package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for marking a question as helpful")
public class VoteQuestionRequest {

    @NotNull(message = "Auth credential ID is required")
    @Schema(description = "JWT sub claim value (AuthCredentials ID) of the logged-in user", example = "d4e5f6a7-b8c9-0123-defa-234567890123")
    private UUID authCredentialId;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }
}
