package com.buyology.ecommerce.cart.repository;

import com.buyology.ecommerce.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartId(UUID cartId);

    Optional<CartItem> findByCartIdAndProductIdAndVariantId(UUID cartId, UUID productId, UUID variantId);

    Optional<CartItem> findByCartIdAndProductIdAndVariantIdIsNull(UUID cartId, UUID productId);

    void deleteByCartId(UUID cartId);
}
