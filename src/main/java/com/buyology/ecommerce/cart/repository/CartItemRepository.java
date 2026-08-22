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

    /** Only the lines the shopper has ticked — the ones that price, reserve and ship. */
    List<CartItem> findByCartIdAndSelectedTrue(UUID cartId);

    Optional<CartItem> findByCartIdAndProductIdAndVariantId(UUID cartId, UUID productId, UUID variantId);

    Optional<CartItem> findByCartIdAndProductIdAndVariantIdIsNull(UUID cartId, UUID productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") UUID cartId);

    /**
     * Deletes only the PURCHASED lines after a successful payment, leaving unticked rows for the
     * shopper's next visit. Returning the count matters: the caller decides from it whether the
     * cart is finished (everything bought) or still alive (survivors remain).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.selected = true")
    int deleteSelectedByCartId(@Param("cartId") UUID cartId);

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
