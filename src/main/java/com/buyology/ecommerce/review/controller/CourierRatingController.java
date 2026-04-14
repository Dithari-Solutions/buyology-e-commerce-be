package com.buyology.ecommerce.review.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.dto.PostDeliveryRatingRequest;
import com.buyology.ecommerce.review.service.CourierRatingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Allows customers to submit a combined post-delivery rating:
 * 5-star courier rating plus optional product reviews for each order item.
 *
 * <p>Only permitted after the order status is DELIVERED. One submission per order.
 */
@RestController
@RequestMapping("/api/v1/orders/{orderId}/rate")
public class CourierRatingController {

    private final CourierRatingService courierRatingService;

    public CourierRatingController(CourierRatingService courierRatingService) {
        this.courierRatingService = courierRatingService;
    }

    /**
     * Submit a post-delivery rating.
     *
     * POST /api/v1/orders/{orderId}/rate
     *
     * @param orderId  the ecommerce order ID (must belong to the authenticated user)
     * @param userId   injected from JWT — the authenticated customer's user ID
     * @param request  courier stars (required) + optional product ratings
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> submitRating(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PostDeliveryRatingRequest request) {

        courierRatingService.submitPostDeliveryRating(orderId, userId, request);
        return ApiResponse.success(null, "Rating submitted successfully");
    }
}
