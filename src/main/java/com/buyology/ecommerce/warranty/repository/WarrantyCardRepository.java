package com.buyology.ecommerce.warranty.repository;

import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarrantyCardRepository extends JpaRepository<WarrantyCard, UUID> {

    Optional<WarrantyCard> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);

    List<WarrantyCard> findByOrderIdOrderByUnitIndexAsc(UUID orderId);

    /**
     * Delivered orders that are still owed cards: an item whose product states a warranty has fewer
     * cards than units. The catch-up sweep issues those, so a delivery whose after-commit listener
     * never ran (a restart in between, a failed insert) still gets its cards within the hour.
     */
    @Query(value = """
            SELECT DISTINCT o.id
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            JOIN products p ON p.id = oi.product_id
            WHERE o.status = 'DELIVERED'
              AND o.deleted_at IS NULL
              AND o.delivered_at >= :since
              AND p.warranty_months IS NOT NULL
              AND oi.quantity > (SELECT COUNT(*) FROM warranty_cards wc WHERE wc.order_item_id = oi.id)
            """, nativeQuery = true)
    List<UUID> findDeliveredOrderIdsMissingCards(@Param("since") Instant since);
}
