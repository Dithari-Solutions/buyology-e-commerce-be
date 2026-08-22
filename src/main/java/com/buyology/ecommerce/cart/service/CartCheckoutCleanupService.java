package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Clears a cart after its selected lines were successfully bought.
 *
 * <p>Extracted from OrderService (which needs thirty-odd collaborators) so the three places that
 * clear a cart after payment share one implementation with three dependencies.
 *
 * <p>Only the PURCHASED (selected) rows are deleted. Unticked rows are the shopper's "not this
 * time" pile and survive the purchase — deleting them was acceptable only while selection did not
 * exist. Survivors deliberately stay UNTICKED: this method is reachable from a scheduled sweep that
 * runs on BOTH replicas with no ShedLock, and leaving survivors unselected is what makes the second
 * replica's pass a no-op (its selected-only delete matches zero rows). Re-ticking them for
 * friendliness would re-arm the sweep and let the losing replica delete the items the customer
 * chose to keep.
 */
@Service
public class CartCheckoutCleanupService {

    private static final Logger log = LoggerFactory.getLogger(CartCheckoutCleanupService.class);

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final CartItemSpecSelectionRepository specRepo;

    public CartCheckoutCleanupService(CartRepository cartRepo,
                                      CartItemRepository cartItemRepo,
                                      CartItemSpecSelectionRepository specRepo) {
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.specRepo = specRepo;
    }

    /**
     * Removes the purchased lines and settles the cart's fate: ABANDONED when nothing is left,
     * back to ACTIVE (with a zero selected-total) when unticked rows survive.
     *
     * <p>Idempotent: a second call finds no selected rows to delete and changes nothing.
     */
    public void clearOrderedItems(UUID cartId) {
        if (cartId == null) {
            return;
        }
        cartRepo.findById(cartId).ifPresent(cart -> {
            if (cart.getStatus() == Cart.CartStatus.ABANDONED) {
                log.debug("[CART] Cart {} already abandoned. Skipping deletion.", cartId);
                return;
            }

            // Specs first (child rows), then the items — selected-only on both.
            specRepo.deleteSelectedByCartId(cartId);
            int purchased = cartItemRepo.deleteSelectedByCartId(cartId);
            boolean survivors = !cartItemRepo.findByCartId(cartId).isEmpty();

            if (survivors) {
                // The shopper kept some lines for later: the cart lives on. Total is the SELECTED
                // subtotal, and every survivor is unticked, so it is zero by definition.
                cart.setStatus(Cart.CartStatus.ACTIVE);
                cart.setTotalPrice(BigDecimal.ZERO);
                log.info("[CART] Cart {}: cleared {} purchased line(s); unticked survivors kept, "
                        + "cart returned to ACTIVE", cartId, purchased);
            } else {
                cart.setStatus(Cart.CartStatus.ABANDONED);
                cart.setTotalPrice(BigDecimal.ZERO);
                log.info("[CART] Cart {}: cleared {} purchased line(s); nothing left, cart closed",
                        cartId, purchased);
            }
            cartRepo.saveAndFlush(cart);
        });
    }
}
