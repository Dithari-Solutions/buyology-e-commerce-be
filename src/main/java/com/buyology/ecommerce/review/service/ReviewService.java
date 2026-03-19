package com.buyology.ecommerce.review.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.review.domain.*;
import com.buyology.ecommerce.review.domain.enums.MediaType;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.review.repository.*;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {

    private static final String REVIEW_UPLOAD_PATH = "/opt/uploads/review";
    private static final int MAX_REVIEW_IMAGES = 2;

    private final ProductReviewRepository reviewRepository;
    private final ProductReviewStatsRepository statsRepository;
    private final ProductReviewMediaRepository mediaRepository;
    private final ProductReviewReplyRepository replyRepository;
    private final ProductReviewVoteRepository voteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;

    public ReviewService(ProductReviewRepository reviewRepository,
                         ProductReviewStatsRepository statsRepository,
                         ProductReviewMediaRepository mediaRepository,
                         ProductReviewReplyRepository replyRepository,
                         ProductReviewVoteRepository voteRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         CartItemRepository cartItemRepository) {
        this.reviewRepository = reviewRepository;
        this.statsRepository = statsRepository;
        this.mediaRepository = mediaRepository;
        this.replyRepository = replyRepository;
        this.voteRepository = voteRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
    }

    // ── Public: List approved reviews for a product ──────────────────────────

    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getApprovedReviewsByProduct(
            UUID productId, int page, int size) {

        Page<ProductReview> reviews = reviewRepository.findByProductIdAndStatusAndDeletedAtIsNull(
                productId, ModerationStatus.APPROVED,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<ReviewResponse> data = reviews.getContent().stream()
                .map(r -> toResponse(r, true))
                .toList();
        return ApiResponse.success(data, "Reviews fetched successfully");
    }

    // ── Public: Get review stats for a product ────────────────────────────────

    public ResponseEntity<ApiResponse<ReviewStatsResponse>> getReviewStats(UUID productId) {
        ProductReviewStats stats = statsRepository.findByProductId(productId).orElse(null);
        if (stats == null) {
            ReviewStatsResponse empty = emptyStats(productId);
            return ApiResponse.success(empty, "Review stats fetched successfully");
        }
        return ApiResponse.success(toStatsResponse(stats), "Review stats fetched successfully");
    }

    // ── Public: Get single approved review ───────────────────────────────────

    public ResponseEntity<ApiResponse<ReviewResponse>> getApprovedReview(UUID reviewId) {
        ProductReview review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId).orElse(null);
        if (review == null || review.getStatus() != ModerationStatus.APPROVED) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }
        return ApiResponse.success(toResponse(review, true), "Review fetched successfully");
    }

    // ── Create review ─────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            CreateReviewRequest request, List<MultipartFile> images) {

        Product product = productRepository.findById(request.getProductId()).orElse(null);
        if (product == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        Users user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        if (reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(
                request.getProductId(), request.getUserId())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "User has already reviewed this product");
        }

        if (images != null && images.size() > MAX_REVIEW_IMAGES) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "A review can have at most " + MAX_REVIEW_IMAGES + " images");
        }

        if (images != null) {
            for (MultipartFile file : images) {
                String ct = file.getContentType();
                if (ct == null || !ct.startsWith("image/")) {
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                            "Only image files are allowed (JPEG, PNG, WEBP, etc.)");
                }
            }
        }

        boolean isVerifiedPurchase = cartItemRepository
                .existsCheckedOutPurchaseByUserAndProduct(request.getUserId(), request.getProductId());

        ProductReview review = new ProductReview(product, user, request.getRating(),
                request.getTitle(), request.getBody());
        review.setIsVerifiedPurchase(isVerifiedPurchase);

        ProductReview saved = reviewRepository.save(review);

        if (images != null && !images.isEmpty()) {
            Path reviewDir = Paths.get(REVIEW_UPLOAD_PATH, saved.getId().toString());
            ensureDirectory(reviewDir);
            for (int i = 0; i < images.size(); i++) {
                MultipartFile file = images.get(i);
                String url = saveFile(reviewDir, file, "review_" + i);
                mediaRepository.save(new ProductReviewMedia(saved, url, MediaType.IMAGE, (short) i));
            }
        }

        return ApiResponse.created(toResponse(saved, false), "Review submitted successfully and is pending moderation");
    }

    // ── Update own review (only if PENDING) ───────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(UUID reviewId, UpdateReviewRequest request) {
        ProductReview review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId).orElse(null);
        if (review == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }

        if (review.getStatus() != ModerationStatus.PENDING) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "Only pending reviews can be edited");
        }

        if (request.getRating() != null) review.setRating(request.getRating());
        if (request.getTitle() != null) review.setTitle(request.getTitle());
        if (request.getBody() != null) review.setBody(request.getBody());

        ProductReview saved = reviewRepository.save(review);
        return ApiResponse.success(toResponse(saved, false), "Review updated successfully");
    }

    // ── Soft-delete own review ────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteReview(UUID reviewId) {
        ProductReview review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId).orElse(null);
        if (review == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }

        review.setDeletedAt(Instant.now());
        reviewRepository.save(review);

        if (review.getStatus() == ModerationStatus.APPROVED) {
            recalculateStats(review.getProduct().getId());
        }

        return ApiResponse.success(null, "Review deleted successfully");
    }

    // ── Vote on a review ──────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> voteOnReview(UUID reviewId, VoteReviewRequest request) {
        ProductReview review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId).orElse(null);
        if (review == null || review.getStatus() != ModerationStatus.APPROVED) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Review not found");
        }

        Users user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        ProductReviewVote existing = voteRepository
                .findByIdReviewIdAndIdUserId(reviewId, request.getUserId()).orElse(null);

        if (existing != null) {
            boolean changed = !existing.getIsHelpful().equals(request.getIsHelpful());
            if (!changed) {
                return ApiResponse.failure(HttpStatus.CONFLICT, "You have already cast this vote");
            }
            // Update the vote and adjust counts
            if (Boolean.TRUE.equals(existing.getIsHelpful())) {
                review.setHelpfulCount(review.getHelpfulCount() - 1);
                review.setNotHelpfulCount(review.getNotHelpfulCount() + 1);
            } else {
                review.setNotHelpfulCount(review.getNotHelpfulCount() - 1);
                review.setHelpfulCount(review.getHelpfulCount() + 1);
            }
            existing.setIsHelpful(request.getIsHelpful());
            voteRepository.save(existing);
        } else {
            ProductReviewVoteId voteId = new ProductReviewVoteId(reviewId, request.getUserId());
            ProductReviewVote vote = new ProductReviewVote(voteId, review, user, request.getIsHelpful());
            voteRepository.save(vote);

            if (Boolean.TRUE.equals(request.getIsHelpful())) {
                review.setHelpfulCount(review.getHelpfulCount() + 1);
            } else {
                review.setNotHelpfulCount(review.getNotHelpfulCount() + 1);
            }
        }

        reviewRepository.save(review);
        return ApiResponse.success(null, "Vote recorded successfully");
    }

    // ── Remove vote ───────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeVoteOnReview(UUID reviewId, UUID userId) {
        ProductReviewVote vote = voteRepository
                .findByIdReviewIdAndIdUserId(reviewId, userId).orElse(null);
        if (vote == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Vote not found");
        }

        ProductReview review = vote.getReview();
        if (Boolean.TRUE.equals(vote.getIsHelpful())) {
            review.setHelpfulCount(Math.max(0, review.getHelpfulCount() - 1));
        } else {
            review.setNotHelpfulCount(Math.max(0, review.getNotHelpfulCount() - 1));
        }
        reviewRepository.save(review);
        voteRepository.delete(vote);
        return ApiResponse.success(null, "Vote removed successfully");
    }

    // ── Stats recalculation ───────────────────────────────────────────────────

    public void recalculateStats(UUID productId) {
        List<ProductReview> approved = reviewRepository.findByProductIdAndStatusAndDeletedAtIsNull(
                productId, ModerationStatus.APPROVED);

        ProductReviewStats stats = statsRepository.findByProductId(productId)
                .orElseGet(() -> {
                    Product p = productRepository.findById(productId).orElseThrow();
                    return new ProductReviewStats(p);
                });

        int total = approved.size();
        int r1 = 0, r2 = 0, r3 = 0, r4 = 0, r5 = 0;
        double sum = 0;
        for (ProductReview r : approved) {
            sum += r.getRating();
            switch (r.getRating()) {
                case 1 -> r1++;
                case 2 -> r2++;
                case 3 -> r3++;
                case 4 -> r4++;
                case 5 -> r5++;
            }
        }

        stats.setTotalReviews(total);
        stats.setAverageRating(total > 0
                ? new java.math.BigDecimal(sum / total).setScale(2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO);
        stats.setRating1Count(r1);
        stats.setRating2Count(r2);
        stats.setRating3Count(r3);
        stats.setRating4Count(r4);
        stats.setRating5Count(r5);
        statsRepository.save(stats);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    public ReviewResponse toResponse(ProductReview review, boolean includeReply) {
        ReviewResponse r = new ReviewResponse();
        r.setId(review.getId());
        r.setProductId(review.getProduct().getId());
        r.setUserId(review.getUser().getId());
        r.setUserFirstName(review.getUser().getFirstName());
        r.setUserLastName(review.getUser().getLastName());
        r.setRating(review.getRating());
        r.setTitle(review.getTitle());
        r.setBody(review.getBody());
        r.setIsVerifiedPurchase(review.getIsVerifiedPurchase());
        r.setStatus(review.getStatus());
        r.setRejectionReason(review.getRejectionReason());
        r.setHelpfulCount(review.getHelpfulCount());
        r.setNotHelpfulCount(review.getNotHelpfulCount());
        r.setCreatedAt(review.getCreatedAt());
        r.setUpdatedAt(review.getUpdatedAt());

        List<ReviewResponse.MediaDto> mediaDtos = mediaRepository
                .findByReviewIdOrderBySortOrderAsc(review.getId())
                .stream().map(m -> {
                    ReviewResponse.MediaDto md = new ReviewResponse.MediaDto();
                    md.setId(m.getId());
                    md.setUrl(m.getUrl());
                    md.setMediaType(m.getMediaType());
                    md.setSortOrder(m.getSortOrder());
                    md.setCreatedAt(m.getCreatedAt());
                    return md;
                }).toList();
        r.setMedia(mediaDtos);

        if (includeReply) {
            replyRepository.findByReviewId(review.getId()).ifPresent(reply -> {
                ReviewResponse.ReviewReplyDto rd = new ReviewResponse.ReviewReplyDto();
                rd.setId(reply.getId());
                rd.setAdminId(reply.getAdmin().getId());
                rd.setAdminFirstName(reply.getAdmin().getFirstName());
                rd.setAdminLastName(reply.getAdmin().getLastName());
                rd.setBody(reply.getBody());
                rd.setCreatedAt(reply.getCreatedAt());
                rd.setUpdatedAt(reply.getUpdatedAt());
                r.setReply(rd);
            });
        }

        return r;
    }

    private ReviewStatsResponse toStatsResponse(ProductReviewStats stats) {
        ReviewStatsResponse r = new ReviewStatsResponse();
        r.setProductId(stats.getProductId());
        r.setTotalReviews(stats.getTotalReviews());
        r.setAverageRating(stats.getAverageRating());
        r.setRating1Count(stats.getRating1Count());
        r.setRating2Count(stats.getRating2Count());
        r.setRating3Count(stats.getRating3Count());
        r.setRating4Count(stats.getRating4Count());
        r.setRating5Count(stats.getRating5Count());
        r.setUpdatedAt(stats.getUpdatedAt());
        return r;
    }

    private ReviewStatsResponse emptyStats(UUID productId) {
        ReviewStatsResponse r = new ReviewStatsResponse();
        r.setProductId(productId);
        r.setTotalReviews(0);
        r.setAverageRating(java.math.BigDecimal.ZERO);
        r.setRating1Count(0);
        r.setRating2Count(0);
        r.setRating3Count(0);
        r.setRating4Count(0);
        r.setRating5Count(0);
        return r;
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    private String saveFile(Path dir, MultipartFile file, String baseName) {
        String original = file.getOriginalFilename();
        String extension = "";
        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf("."));
        }
        String fileName = baseName + extension;
        try {
            Files.write(dir.resolve(fileName), file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save review image: " + original, e);
        }
        return "/review/" + dir.getFileName() + "/" + fileName;
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create review media directory", e);
        }
    }
}
