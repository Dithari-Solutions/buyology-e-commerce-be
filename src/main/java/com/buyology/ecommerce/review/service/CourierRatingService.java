package com.buyology.ecommerce.review.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.exception.OrderNotFoundException;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.review.domain.CourierRating;
import com.buyology.ecommerce.review.domain.ProductReview;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.PostDeliveryRatingRequest;
import com.buyology.ecommerce.review.repository.CourierRatingRepository;
import com.buyology.ecommerce.review.repository.ProductReviewRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles post-delivery rating: 5-star courier rating plus optional product reviews.
 * Enforces that the order belongs to the user and is in DELIVERED status before
 * accepting a rating.
 */
@Service
public class CourierRatingService {

    private static final Logger log = LoggerFactory.getLogger(CourierRatingService.class);

    private final CourierRatingRepository  courierRatingRepo;
    private final ProductReviewRepository  productReviewRepo;
    private final OrderRepository          orderRepo;
    private final ProductRepository        productRepo;
    private final UserRepository           userRepo;

    public CourierRatingService(CourierRatingRepository courierRatingRepo,
                                ProductReviewRepository productReviewRepo,
                                OrderRepository orderRepo,
                                ProductRepository productRepo,
                                UserRepository userRepo) {
        this.courierRatingRepo = courierRatingRepo;
        this.productReviewRepo = productReviewRepo;
        this.orderRepo         = orderRepo;
        this.productRepo       = productRepo;
        this.userRepo          = userRepo;
    }

    /**
     * Submits a combined post-delivery rating: courier stars + optional product reviews.
     *
     * @throws IllegalArgumentException if the order is not found, not owned by the user,
     *                                  not in DELIVERED status, or already rated.
     */
    @Transactional
    public void submitPostDeliveryRating(UUID orderId, UUID userId,
                                         PostDeliveryRatingRequest request) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException(
                    "Ratings can only be submitted after the order has been delivered.");
        }

        if (order.getCourierUserId() == null) {
            throw new IllegalArgumentException(
                    "No courier is associated with this order.");
        }

        if (courierRatingRepo.existsByOrderIdAndUserId(orderId, userId)) {
            throw new IllegalArgumentException(
                    "You have already submitted a rating for this order.");
        }

        // Save courier rating
        CourierRating courierRating = new CourierRating();
        courierRating.setOrderId(orderId);
        courierRating.setUserId(userId);
        courierRating.setCourierId(order.getCourierUserId());
        courierRating.setStars(request.getCourierStars());
        courierRating.setComment(com.buyology.ecommerce.common.utils.HtmlSanitizer.stripHtml(request.getCourierComment()));
        courierRatingRepo.save(courierRating);

        log.info("[Rating] Courier rating saved orderId={} courierId={} stars={}",
                orderId, order.getCourierUserId(), request.getCourierStars());

        // Save product reviews (optional)
        if (request.getProductRatings() == null || request.getProductRatings().isEmpty()) {
            return;
        }

        Users user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        for (PostDeliveryRatingRequest.ProductRatingEntry entry : request.getProductRatings()) {
            Product product = productRepo.findById(entry.getProductId()).orElse(null);
            if (product == null) {
                log.warn("[Rating] Product not found productId={} — skipping", entry.getProductId());
                continue;
            }

            // Skip if already reviewed
            if (productReviewRepo.existsByProductIdAndUserIdAndDeletedAtIsNull(
                    entry.getProductId(), userId)) {
                log.debug("[Rating] Duplicate product review skipped productId={} userId={}",
                        entry.getProductId(), userId);
                continue;
            }

            ProductReview review = new ProductReview();
            review.setProduct(product);
            review.setUser(user);
            review.setRating(entry.getStars());
            review.setBody(entry.getBody());
            review.setIsVerifiedPurchase(true);
            review.setStatus(ModerationStatus.PENDING);
            productReviewRepo.save(review);

            log.info("[Rating] Product review saved productId={} userId={} stars={}",
                    entry.getProductId(), userId, entry.getStars());
        }
    }
}
