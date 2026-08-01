package com.buyology.ecommerce.review.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.service.ReviewAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('review:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getAllReviews(
            @RequestParam(required = false) ModerationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewAdminService.getAllReviews(status, page, size);
    }

    @Operation(summary = "Get a review by ID (includes deleted records)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('review:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(
            @PathVariable UUID reviewId) {
        return reviewAdminService.getReviewById(reviewId);
    }

    @Operation(summary = "Approve or reject a review",
            description = "Set status to APPROVED or REJECTED. Rejection requires a rejectionReason.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('review:moderate') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{reviewId}/moderate")
    public ResponseEntity<ApiResponse<ReviewResponse>> moderateReview(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal UUID adminId,
            @RequestBody @Valid ModerateReviewRequest request) {
        return reviewAdminService.moderateReview(reviewId, request, adminId);
    }

    @Operation(summary = "Delete a review (soft-delete)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('review:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable UUID reviewId) {
        return reviewAdminService.deleteReview(reviewId);
    }

    @Operation(summary = "Add an admin reply to a review",
            description = "Only one reply per review is allowed. Use PUT to update an existing reply.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('review:reply:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> addReply(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal UUID adminId,
            @RequestBody @Valid CreateReviewReplyRequest request) {
        return reviewAdminService.addReply(reviewId, request, adminId);
    }

    @Operation(summary = "Update the admin reply on a review")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('review:reply:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReply(
            @PathVariable UUID reviewId,
            @RequestBody @Valid UpdateReviewReplyRequest request) {
        return reviewAdminService.updateReply(reviewId, request);
    }

    @Operation(summary = "Delete the admin reply on a review")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('review:reply:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<Void>> deleteReply(
            @PathVariable UUID reviewId) {
        return reviewAdminService.deleteReply(reviewId);
    }
}
