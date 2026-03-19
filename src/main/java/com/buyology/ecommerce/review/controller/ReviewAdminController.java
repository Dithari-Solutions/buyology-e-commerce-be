package com.buyology.ecommerce.review.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.service.ReviewAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reviews")
@Tag(name = "Reviews (Admin)", description = "Admin APIs for moderating reviews and managing admin replies")
public class ReviewAdminController {

    private final ReviewAdminService reviewAdminService;

    public ReviewAdminController(ReviewAdminService reviewAdminService) {
        this.reviewAdminService = reviewAdminService;
    }

    @Operation(summary = "List all reviews",
            description = "Filter by status: PENDING, APPROVED, REJECTED. Omit status to get all non-deleted reviews.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getAllReviews(
            @RequestParam(required = false) ModerationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewAdminService.getAllReviews(status, page, size);
    }

    @Operation(summary = "Get a review by ID (includes deleted records)")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(
            @PathVariable UUID reviewId) {
        return reviewAdminService.getReviewById(reviewId);
    }

    @Operation(summary = "Approve or reject a review",
            description = "Set status to APPROVED or REJECTED. Rejection requires a rejectionReason.")
    @PatchMapping("/{reviewId}/moderate")
    public ResponseEntity<ApiResponse<ReviewResponse>> moderateReview(
            @PathVariable UUID reviewId,
            @RequestBody @Valid ModerateReviewRequest request) {
        return reviewAdminService.moderateReview(reviewId, request);
    }

    @Operation(summary = "Delete a review (soft-delete)")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable UUID reviewId) {
        return reviewAdminService.deleteReview(reviewId);
    }

    @Operation(summary = "Add an admin reply to a review",
            description = "Only one reply per review is allowed. Use PUT to update an existing reply.")
    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> addReply(
            @PathVariable UUID reviewId,
            @RequestBody @Valid CreateReviewReplyRequest request) {
        return reviewAdminService.addReply(reviewId, request);
    }

    @Operation(summary = "Update the admin reply on a review")
    @PutMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReply(
            @PathVariable UUID reviewId,
            @RequestBody @Valid UpdateReviewReplyRequest request) {
        return reviewAdminService.updateReply(reviewId, request);
    }

    @Operation(summary = "Delete the admin reply on a review")
    @DeleteMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<Void>> deleteReply(
            @PathVariable UUID reviewId) {
        return reviewAdminService.deleteReply(reviewId);
    }
}
