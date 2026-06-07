package com.buyology.ecommerce.review.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.domain.ProductReview;
import com.buyology.ecommerce.review.domain.ProductReviewReply;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.repository.ProductReviewRepository;
import com.buyology.ecommerce.review.repository.ProductReviewReplyRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewAdminService {

    private final ProductReviewRepository reviewRepository;
    private final ProductReviewReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final ReviewService reviewService;

    public ReviewAdminService(ProductReviewRepository reviewRepository,
                              ProductReviewReplyRepository replyRepository,
                              UserRepository userRepository,
                              ReviewService reviewService) {
        this.reviewRepository = reviewRepository;
        this.replyRepository = replyRepository;
        this.userRepository = userRepository;
        this.reviewService = reviewService;
    }

    // ── List all reviews (admin view, filterable by status) ───────────────────

    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getAllReviews(
            ModerationStatus status, int page, int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductReview> reviews = (status != null)
                ? reviewRepository.findByStatus(status, pageable)
                : reviewRepository.findByDeletedAtIsNull(pageable);

        List<ReviewResponse> data = reviews.getContent().stream()
                .map(r -> reviewService.toResponse(r, true))
                .toList();
        return ApiResponse.success(data, "Reviews fetched successfully");
    }

    // ── Get single review by ID (admin view) ─────────────────────────────────

    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(UUID reviewId) {
        ProductReview review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }
        return ApiResponse.success(reviewService.toResponse(review, true), "Review fetched successfully");
    }

    // ── Moderate a review (approve / reject) ─────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<ReviewResponse>> moderateReview(
            UUID reviewId, ModerateReviewRequest request) {

        ProductReview review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }

        if (request.getStatus() == ModerationStatus.REJECTED && request.getRejectionReason() == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Rejection reason is required when rejecting a review");
        }

        ModerationStatus previousStatus = review.getStatus();
        review.setStatus(request.getStatus());
        review.setModeratedBy(request.getModeratedBy());
        review.setModeratedAt(Instant.now());
        review.setRejectionReason(request.getRejectionReason());

        ProductReview saved = reviewRepository.save(review);

        // Recalculate stats whenever approval status changes
        if (previousStatus != request.getStatus()) {
            reviewService.recalculateStats(review.getProduct().getId());
        }

        return ApiResponse.success(reviewService.toResponse(saved, true), "Review moderated successfully");
    }

    // ── Admin soft-delete a review ────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteReview(UUID reviewId) {
        ProductReview review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }

        review.setDeletedAt(Instant.now());
        reviewRepository.save(review);

        if (review.getStatus() == ModerationStatus.APPROVED) {
            reviewService.recalculateStats(review.getProduct().getId());
        }

        return ApiResponse.success(null, "Review deleted successfully");
    }

    // ── Add admin reply to a review ───────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<ReviewResponse>> addReply(
            UUID reviewId, CreateReviewReplyRequest request) {

        ProductReview review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId).orElse(null);
        if (review == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }

        if (replyRepository.existsByReviewId(reviewId)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "A reply already exists for this review. Use PUT to update it.");
        }

        Users admin = userRepository.findById(request.getAdminId()).orElse(null);
        if (admin == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Admin user not found");
        }

        ProductReviewReply reply = new ProductReviewReply(review, admin, request.getBody());
        replyRepository.save(reply);

        return ApiResponse.created(reviewService.toResponse(review, true), "Reply added successfully");
    }

    // ── Update admin reply ────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReply(
            UUID reviewId, UpdateReviewReplyRequest request) {

        ProductReviewReply reply = replyRepository.findByReviewId(reviewId).orElse(null);
        if (reply == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Reply not found for this review");
        }

        reply.setBody(com.buyology.ecommerce.common.utils.HtmlSanitizer.stripHtml(request.getBody()));
        replyRepository.save(reply);

        ProductReview review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId).orElseThrow();
        return ApiResponse.success(reviewService.toResponse(review, true), "Reply updated successfully");
    }

    // ── Delete admin reply ────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteReply(UUID reviewId) {
        ProductReviewReply reply = replyRepository.findByReviewId(reviewId).orElse(null);
        if (reply == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Reply not found for this review");
        }
        replyRepository.delete(reply);
        return ApiResponse.success(null, "Reply deleted successfully");
    }
}
