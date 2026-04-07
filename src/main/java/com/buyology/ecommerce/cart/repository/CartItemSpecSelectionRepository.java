package com.buyology.ecommerce.cart.repository;

import com.buyology.ecommerce.cart.domain.CartItemSpecSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CartItemSpecSelectionRepository extends JpaRepository<CartItemSpecSelection, UUID> {

    List<CartItemSpecSelection> findByCartItemId(UUID cartItemId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM CartItemSpecSelection ciss WHERE ciss.cartItem.id = :cartItemId")
    void deleteByCartItemId(@org.springframework.data.repository.query.Param("cartItemId") UUID cartItemId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM CartItemSpecSelection ciss WHERE ciss.cartItem.cart.id = :cartId")
    void deleteByCartId(@org.springframework.data.repository.query.Param("cartId") UUID cartId);
}
