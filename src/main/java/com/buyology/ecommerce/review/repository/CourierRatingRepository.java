package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.CourierRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CourierRatingRepository extends JpaRepository<CourierRating, UUID> {

    boolean existsByOrderIdAndUserId(UUID orderId, UUID userId);

    @Query("SELECT AVG(r.stars) FROM CourierRating r WHERE r.courierId = :courierId")
    Double findAverageStarsByCourierId(@Param("courierId") UUID courierId);
}
