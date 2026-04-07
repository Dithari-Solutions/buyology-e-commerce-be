package com.buyology.ecommerce.cart.repository;

import com.buyology.ecommerce.cart.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(UUID authCredentialId, Cart.CartStatus status);
}
