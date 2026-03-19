package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Request body for updating a pending review")
public class UpdateReviewRequest {

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Schema(description = "Updated rating (1-5)", example = "5")
    private Short rating;

    @Schema(description = "Updated review body text")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public Short getRating() { return rating; }
    public void setRating(Short rating) { this.rating = rating; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
