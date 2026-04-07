package com.buyology.ecommerce.cart.repository;

import com.buyology.ecommerce.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartId(UUID cartId);

    Optional<CartItem> findByCartIdAndProductIdAndVariantId(UUID cartId, UUID productId, UUID variantId);

    Optional<CartItem> findByCartIdAndProductIdAndVariantIdIsNull(UUID cartId, UUID productId);

    @Modifying
    @Transactional
    void deleteByCartId(UUID cartId);

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM cart_items ci
            JOIN carts c ON ci.cart_id = c.id
            JOIN auth_credentials ac ON c.auth_credential_id = ac.id
            WHERE ac.user_id = :userId
              AND ci.product_id = :productId
              AND c.status = 'CHECKED_OUT'
            """, nativeQuery = true)
    boolean existsCheckedOutPurchaseByUserAndProduct(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId);
}
