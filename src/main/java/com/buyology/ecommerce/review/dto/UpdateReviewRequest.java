package com.buyology.ecommerce.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for updating a pending review")
public class UpdateReviewRequest {

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Schema(description = "Updated rating (1-5)", example = "5")
    private Short rating;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Updated review title", example = "Even better than expected!")
    private String title;

    @Schema(description = "Updated review body text")
    private String body;

    // =====================
    // Getters & Setters
    // =====================

    public Short getRating() { return rating; }
    public void setRating(Short rating) { this.rating = rating; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
