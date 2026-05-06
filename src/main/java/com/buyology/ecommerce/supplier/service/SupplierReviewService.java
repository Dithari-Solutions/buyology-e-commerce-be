package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.review.domain.ProductReview;
import com.buyology.ecommerce.review.domain.ProductReviewStats;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.repository.ProductReviewRepository;
import com.buyology.ecommerce.review.repository.ProductReviewStatsRepository;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupplierReviewService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ProductReviewStatsRepository statsRepository;

    public SupplierReviewService(
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            ProductReviewRepository reviewRepository,
            ProductReviewStatsRepository statsRepository) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.statsRepository = statsRepository;
    }

    public ResponseEntity<ApiResponse<Page<ProductReview>>> listReviewsForSupplier(
            UUID productId, Pageable pageable) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        if (productId != null) {
            // Ownership check
            Product p = productRepository.findById(productId).orElse(null);
            if (p == null || !supplier.getId().equals(p.getSupplierId())) {
                return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
            }
            Page<ProductReview> page = reviewRepository.findByProductIdAndStatusAndDeletedAtIsNull(
                    productId, ModerationStatus.APPROVED, pageable);
            return ApiResponse.success(page, "Reviews");
        }

        // No productId: aggregate across all supplier products (in-memory, paginated naively)
        List<UUID> productIds = productRepository.findBySupplierId(supplier.getId(), Pageable.unpaged())
                .getContent().stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return ApiResponse.success(new PageImpl<>(List.of(), pageable, 0), "Reviews");
        }
        List<ProductReview> all = new java.util.ArrayList<>();
        for (UUID pid : productIds) {
            all.addAll(reviewRepository.findByProductIdAndStatusAndDeletedAtIsNull(pid, ModerationStatus.APPROVED));
        }
        all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        Page<ProductReview> page = new PageImpl<>(all.subList(from, to), pageable, all.size());
        return ApiResponse.success(page, "Reviews");
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> getSupplierSummary() {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        List<UUID> productIds = productRepository.findBySupplierId(supplier.getId(), Pageable.unpaged())
                .getContent().stream().map(Product::getId).toList();

        Map<String, Object> result = new HashMap<>();
        if (productIds.isEmpty()) {
            result.put("totalReviews", 0);
            result.put("averageRating", BigDecimal.ZERO);
            result.put("productCount", 0);
            return ApiResponse.success(result, "Supplier review summary");
        }

        List<ProductReviewStats> stats = statsRepository.findByProductIdIn(productIds);
        long totalReviews = stats.stream().mapToLong(ProductReviewStats::getTotalReviews).sum();
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (ProductReviewStats s : stats) {
            BigDecimal contribution = s.getAverageRating()
                    .multiply(BigDecimal.valueOf(s.getTotalReviews()));
            weightedSum = weightedSum.add(contribution);
        }
        BigDecimal avg = totalReviews == 0
                ? BigDecimal.ZERO
                : weightedSum.divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);

        result.put("totalReviews", totalReviews);
        result.put("averageRating", avg);
        result.put("productCount", productIds.size());
        return ApiResponse.success(result, "Supplier review summary");
    }

    public ResponseEntity<ApiResponse<ProductReviewStats>> getProductSummary(UUID productId) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        Product p = productRepository.findById(productId).orElse(null);
        if (p == null || !supplier.getId().equals(p.getSupplierId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }
        ProductReviewStats stats = statsRepository.findByProductId(productId)
                .orElseGet(() -> {
                    ProductReviewStats empty = new ProductReviewStats();
                    return empty;
                });
        return ApiResponse.success(stats, "Product review stats");
    }

    private Supplier resolveCurrentSupplier() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) return null;
        return supplierRepository.findByUserId(userId).orElse(null);
    }
}
