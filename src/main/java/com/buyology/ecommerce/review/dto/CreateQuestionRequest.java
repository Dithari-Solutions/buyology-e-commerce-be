package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for asking a question about a product")
public class CreateQuestionRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "ID of the product the question is about", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID productId;

    @NotNull(message = "User ID is required")
    @Schema(description = "ID of the user asking the question", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private UUID userId;

    @NotBlank(message = "Question body is required")
    @Schema(description = "The question text", example = "Does this laptop support 4K output via HDMI?")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
