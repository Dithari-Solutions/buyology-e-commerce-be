package com.buyology.ecommerce.cart.repository;

import com.buyology.ecommerce.cart.domain.CartItemSpecSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartItemSpecSelectionRepository extends JpaRepository<CartItemSpecSelection, UUID> {

    List<CartItemSpecSelection> findByCartItemId(UUID cartItemId);

    void deleteByCartItemId(UUID cartItemId);
}
