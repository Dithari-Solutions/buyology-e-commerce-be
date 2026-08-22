package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins what happens to the cart after its selected lines are bought.
 *
 * <p>Two properties carry real weight. First, only the PURCHASED rows are deleted — an unticked
 * row is the shopper's "not this time" pile, and wiping it with the purchase silently destroys a
 * decision they made. Second, survivors stay UNTICKED, and that is a cluster-safety invariant, not
 * a UX choice: this cleanup is reachable from a scheduled sweep running on BOTH replicas with no
 * ShedLock, and the second replica's selected-only delete matching zero rows is the entire reason
 * two concurrent passes cannot double-delete.
 */
class CartCheckoutCleanupServiceTest {

    private static final UUID CART = UUID.fromString("3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607");

    private final CartRepository cartRepo = mock(CartRepository.class);
    private final CartItemRepository itemRepo = mock(CartItemRepository.class);
    private final CartItemSpecSelectionRepository specRepo = mock(CartItemSpecSelectionRepository.class);
    private final CartCheckoutCleanupService service =
            new CartCheckoutCleanupService(cartRepo, itemRepo, specRepo);

    private Cart cart(Cart.CartStatus status) {
        Cart c = new Cart();
        c.setStatus(status);
        c.setTotalPrice(new BigDecimal("300.00"));
        when(cartRepo.findById(CART)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void deletesOnlyThePurchasedRows() {
        cart(Cart.CartStatus.CHECKED_OUT);
        when(itemRepo.deleteSelectedByCartId(CART)).thenReturn(2);
        when(itemRepo.findByCartId(CART)).thenReturn(List.of());

        service.clearOrderedItems(CART);

        verify(specRepo).deleteSelectedByCartId(CART);
        verify(itemRepo).deleteSelectedByCartId(CART);
        verify(itemRepo, never()).deleteByCartId(CART);
        verify(specRepo, never()).deleteByCartId(CART);
    }

    @Test
    void anEmptiedCartIsClosed() {
        Cart c = cart(Cart.CartStatus.CHECKED_OUT);
        when(itemRepo.deleteSelectedByCartId(CART)).thenReturn(2);
        when(itemRepo.findByCartId(CART)).thenReturn(List.of());

        service.clearOrderedItems(CART);

        assertEquals(Cart.CartStatus.ABANDONED, c.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getTotalPrice()));
    }

    @Test
    void untickedSurvivorsKeepTheCartAlive() {
        Cart c = cart(Cart.CartStatus.CHECKED_OUT);
        when(itemRepo.deleteSelectedByCartId(CART)).thenReturn(1);
        when(itemRepo.findByCartId(CART)).thenReturn(List.of(new CartItem()));

        service.clearOrderedItems(CART);

        assertEquals(Cart.CartStatus.ACTIVE, c.getStatus(),
                "the shopper kept lines for later; killing the cart would destroy that decision");
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getTotalPrice()),
                "the selected subtotal of an all-unticked cart is zero by definition");
    }

    @Test
    void anAbandonedCartIsLeftAlone() {
        cart(Cart.CartStatus.ABANDONED);

        service.clearOrderedItems(CART);

        verify(itemRepo, never()).deleteSelectedByCartId(CART);
        verify(specRepo, never()).deleteSelectedByCartId(CART);
    }

    @Test
    void aSecondPassIsANoOp() {
        // The two-replica case: both run the sweep, the loser's delete matches nothing and the
        // cart's fate is unchanged.
        Cart c = cart(Cart.CartStatus.ACTIVE);
        when(itemRepo.deleteSelectedByCartId(CART)).thenReturn(0);
        when(itemRepo.findByCartId(CART)).thenReturn(List.of(new CartItem()));

        service.clearOrderedItems(CART);

        assertEquals(Cart.CartStatus.ACTIVE, c.getStatus());
    }

    @Test
    void nullAndMissingCartsAreNoOps() {
        when(cartRepo.findById(CART)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.clearOrderedItems(CART));
        assertDoesNotThrow(() -> service.clearOrderedItems(null));
    }
}
