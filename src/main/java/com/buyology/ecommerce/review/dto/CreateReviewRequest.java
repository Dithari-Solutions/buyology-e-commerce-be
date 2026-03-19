package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.UUID;

@Schema(description = "Request body for creating a product review (sent as JSON part in multipart/form-data)")
public class CreateReviewRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "ID of the product being reviewed", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID productId;

    @NotNull(message = "User ID is required")
    @Schema(description = "ID of the user submitting the review", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private UUID userId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Schema(description = "Rating from 1 to 5", example = "4")
    private Short rating;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Optional review title", example = "Great product!")
    private String title;

    @Schema(description = "Optional review body text", example = "Really satisfied with the build quality.")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Short getRating() { return rating; }
    public void setRating(Short rating) { this.rating = rating; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
