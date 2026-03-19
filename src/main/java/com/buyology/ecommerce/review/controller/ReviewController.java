package com.buyology.ecommerce.review.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Reviews (Public)", description = "Public APIs for browsing and submitting product reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;

    public ReviewController(ReviewService reviewService, ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get approved reviews for a product")
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getApprovedReviewsByProduct(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.getApprovedReviewsByProduct(productId, page, size);
    }

    @Operation(summary = "Get review stats (avg rating, counts per star) for a product")
    @GetMapping("/product/{productId}/stats")
    public ResponseEntity<ApiResponse<ReviewStatsResponse>> getReviewStats(
            @PathVariable UUID productId) {
        return reviewService.getReviewStats(productId);
    }

    @Operation(summary = "Get a single approved review by ID")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getApprovedReview(
            @PathVariable UUID reviewId) {
        return reviewService.getApprovedReview(reviewId);
    }

    @Operation(summary = "Submit a new product review",
            description = "Multipart request. Send 'request' as a JSON part and up to 2 image files as 'images'. The review starts in PENDING status awaiting moderation.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Parameter(hidden = true) @RequestPart("request") String requestJson,
            @Parameter(hidden = true) @RequestPart(value = "images", required = false) List<MultipartFile> images)
            throws Exception {
        CreateReviewRequest request = objectMapper.readValue(requestJson, CreateReviewRequest.class);
        return reviewService.createReview(request, images);
    }

    @Operation(summary = "Update a pending review",
            description = "Only reviews in PENDING status can be edited.")
    @PutMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable UUID reviewId,
            @RequestBody @Valid UpdateReviewRequest request) {
        return reviewService.updateReview(reviewId, request);
    }

    @Operation(summary = "Delete (soft-delete) a review")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable UUID reviewId) {
        return reviewService.deleteReview(reviewId);
    }

    @Operation(summary = "Vote on a review's helpfulness",
            description = "isHelpful=true records a helpful vote; false records a not-helpful vote. Re-voting changes the existing vote.")
    @PostMapping("/{reviewId}/vote")
    public ResponseEntity<ApiResponse<Void>> voteOnReview(
            @PathVariable UUID reviewId,
            @RequestBody @Valid VoteReviewRequest request) {
        return reviewService.voteOnReview(reviewId, request);
    }

    @Operation(summary = "Remove a helpfulness vote from a review")
    @DeleteMapping("/{reviewId}/vote")
    public ResponseEntity<ApiResponse<Void>> removeVoteOnReview(
            @PathVariable UUID reviewId,
            @RequestParam UUID userId) {
        return reviewService.removeVoteOnReview(reviewId, userId);
    }
}
