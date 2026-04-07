package com.buyology.ecommerce.cart.repository;

import com.buyology.ecommerce.cart.domain.CartItemSpecSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CartItemSpecSelectionRepository extends JpaRepository<CartItemSpecSelection, UUID> {

    List<CartItemSpecSelection> findByCartItemId(UUID cartItemId);

    @Modifying
    @Transactional
    void deleteByCartItemId(UUID cartItemId);
}
