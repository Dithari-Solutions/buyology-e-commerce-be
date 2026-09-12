package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.dto.CartCountResponse;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.order.service.DeliveryFeePolicy;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreOperatingHoursRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.service.AccountStatusValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins what "the shopper's cart" means once a checkout has been started and not paid for.
 *
 * <p>Checkout flips the cart to CHECKED_OUT and it stays there until a payment actually succeeds
 * (success marks it ABANDONED). Everyone who reached the checkout page and did not pay — abandoned
 * at the gateway, was signed out, closed the tab — is therefore holding one, and that state was
 * read three different ways by three parts of the same page: {@code getCart} resumed it and showed
 * every item, the badge counted only ACTIVE and said the cart was empty, and the buttons on the
 * cart page looked only for ACTIVE and answered 404. Nothing was ever deleted; the reports of carts
 * emptying themselves after a checkout were the header and the buttons disagreeing with the page.
 *
 * <p>Also covers the duplicate-cart race, which is the other half of the same story: find-or-create
 * is a read then an insert, a page load fires several cart calls at once, and every one of them
 * read "no cart" and inserted. Where V17's partial unique index exists the loser's insert fails and
 * the shopper is shown "A record with the same unique value already exists" — the checkout-page
 * error; where it does not, they quietly end up with two carts and their items split across them.
 */
class CartLifecycleTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID credentialId = UUID.randomUUID();

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private AuthCredentialRepository authCredentialRepository;
    private AuthCredentials credentials;
    private CartService service;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        authCredentialRepository = mock(AuthCredentialRepository.class);

        credentials = new AuthCredentials();
        credentials.setId(credentialId);
        credentials.setUserId(userId);
        when(authCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credentials));
        when(authCredentialRepository.findByIdForUpdate(credentialId)).thenReturn(Optional.of(credentials));
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArgument(0));
        when(cartItemRepository.findByCartId(any())).thenReturn(List.of());
        when(cartItemRepository.findByCartIdAndSelectedTrue(any())).thenReturn(List.of());

        service = new CartService(
                cartRepository,
                cartItemRepository,
                mock(CartItemSpecSelectionRepository.class),
                authCredentialRepository,
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(ProductSpecOptionRepository.class),
                mock(StoreProductRepository.class),
                false,
                mock(StoreProductVariantRepository.class),
                mock(StoreLocationRepository.class),
                mock(StoreOperatingHoursRepository.class),
                mock(UserProfilesRepository.class),
                mock(AccountStatusValidator.class),
                mock(CurrencyExchangeService.class),
                mock(DeliveryFeePolicy.class),
                new com.buyology.ecommerce.order.service.VatPolicy(new java.math.BigDecimal("5"), ""));

        // requireOwnedCredential asserts the principal owns the credential.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── The badge must agree with the page ───────────────────────────────────

    @Test
    void theBadgeCountsACartLeftBehindByAnUnpaidCheckout() {
        // The reported "my cart is empty after logging back in". getCart resumes this exact cart
        // and renders both lines; the badge read zero over the top of it.
        Cart checkedOut = cart(Cart.CartStatus.CHECKED_OUT);
        noActiveCart();
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.CHECKED_OUT)).thenReturn(Optional.of(checkedOut));
        when(cartItemRepository.findByCartId(checkedOut.getId()))
                .thenReturn(List.of(line(2), line(3)));

        CartCountResponse count = body(service.getCartCount(credentialId));

        assertEquals(2, count.getItemCount(), "both lines are still in the cart");
        assertEquals(5, count.getTotalQuantity());
    }

    @Test
    void countingTheBadgeDoesNotResumeTheCart() {
        // A badge poll is a read. Resuming here would flip the cart out of CHECKED_OUT behind the
        // back of a checkout that may still be completing at the gateway.
        Cart checkedOut = cart(Cart.CartStatus.CHECKED_OUT);
        noActiveCart();
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.CHECKED_OUT)).thenReturn(Optional.of(checkedOut));

        service.getCartCount(credentialId);

        assertEquals(Cart.CartStatus.CHECKED_OUT, checkedOut.getStatus());
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void anEmptyBadgeIsStillEmpty() {
        noActiveCart();
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.CHECKED_OUT)).thenReturn(Optional.empty());

        CartCountResponse count = body(service.getCartCount(credentialId));

        assertEquals(0, count.getItemCount());
        assertEquals(0, count.getTotalQuantity());
    }

    // ── The buttons must work on the cart the page is showing ────────────────

    @Test
    void removingALineWorksOnACartLeftBehindByAnUnpaidCheckout() {
        // Before: 404 "No active cart found" on a cart the page had just rendered in full.
        Cart checkedOut = cart(Cart.CartStatus.CHECKED_OUT);
        noActiveCart();
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.CHECKED_OUT)).thenReturn(Optional.of(checkedOut));
        CartItem item = line(1);
        item.setCart(checkedOut);
        when(cartItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        ResponseEntity<ApiResponse<com.buyology.ecommerce.cart.dto.CartResponse>> response =
                service.removeItem(credentialId, item.getId());

        assertEquals(200, response.getStatusCode().value(), "the line must actually be removable");
        verify(cartItemRepository).delete(item);
        assertEquals(Cart.CartStatus.ACTIVE, checkedOut.getStatus(),
                "editing the basket means the shopper is no longer mid-checkout");
    }

    // ── One cart per shopper, however many requests arrive at once ───────────

    @Test
    void aCartLoadThatLosesTheRaceReturnsTheWinnersCartInsteadOfCreatingASecond() {
        // Two cart calls from one page load. Both read "no ACTIVE cart" before either inserts. The
        // loser then blocks on the credential lock, and the re-read behind it is the whole fix: it
        // must find the winner's cart. Creating another here is what produced either the 409 or a
        // silently split cart.
        Cart winners = cart(Cart.CartStatus.ACTIVE);
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.ACTIVE))
                .thenReturn(Optional.empty())          // the read that raced
                .thenReturn(Optional.of(winners));     // the re-read, behind the lock

        var response = service.getCart(credentialId, null, null);

        assertEquals(winners.getId(), body(response).getId(), "the loser adopts the winner's cart");
        verify(authCredentialRepository).findByIdForUpdate(credentialId);
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void theCredentialIsLockedBeforeTheCartIsCreated() {
        // Without the lock the re-read above proves nothing: both callers would read "no cart" and
        // both would insert. Creating without taking it first is the bug.
        noActiveCart();
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.CHECKED_OUT)).thenReturn(Optional.empty());

        service.getCart(credentialId, null, null);

        var order = inOrder(authCredentialRepository, cartRepository);
        order.verify(authCredentialRepository).findByIdForUpdate(credentialId);
        order.verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void aCartLoadStillResumesTheCartLeftBehindByAnUnpaidCheckout() {
        // The pre-existing behaviour this must not regress: no new cart, and the items stay.
        Cart checkedOut = cart(Cart.CartStatus.CHECKED_OUT);
        noActiveCart();
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.CHECKED_OUT)).thenReturn(Optional.of(checkedOut));

        var response = service.getCart(credentialId, null, null);

        assertEquals(checkedOut.getId(), body(response).getId());
        assertEquals(Cart.CartStatus.ACTIVE, checkedOut.getStatus());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void noActiveCart() {
        when(cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                credentialId, Cart.CartStatus.ACTIVE)).thenReturn(Optional.empty());
    }

    private Cart cart(Cart.CartStatus status) {
        Cart cart = new Cart(credentials);
        cart.setId(UUID.randomUUID());
        cart.setStatus(status);
        return cart;
    }

    private CartItem line(int quantity) {
        CartItem item = new CartItem();
        item.setId(UUID.randomUUID());
        item.setQuantity(quantity);
        return item;
    }

    private static <T> T body(ResponseEntity<ApiResponse<T>> response) {
        return java.util.Objects.requireNonNull(response.getBody()).getData();
    }
}
