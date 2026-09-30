package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.domain.CartPriceChangedException;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.dto.CreateOrderRequest;
import com.buyology.ecommerce.order.dto.OrderResponse;
import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.PaymentAnomalyKind;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.payment.service.PaidAmountPolicy;
import com.buyology.ecommerce.payment.service.PaymentAnomalyService;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins what happens to a CAPTURED payment when the basket's price moves before the order exists.
 *
 * <p>This is the cart-first flow: Paymob takes the money, and this AFTER_COMMIT listener builds the
 * order from the cart seconds to minutes later (webhooks retry). Everything in that gap is happening
 * to money that has already left the customer's account.
 *
 * <p>Two failures lived in that gap, and they are the same mistake pointing in opposite directions —
 * a rule written for the pre-payment checkout applied after the payment:
 *
 * <ul>
 *   <li><b>A rise DESTROYED the order.</b> {@code repriceForCheckoutOrRefuse} threw
 *       {@link CartPriceChangedException} — a RuntimeException — and the listener caught
 *       {@code InsufficientStockException} only. The throw rolled this transaction back and left a
 *       captured payment with no order, no anomaly, no auto-refund and nobody alerted: the customer
 *       charged, holding a gateway confirmation, with nothing on its way. A guard written to stop a
 *       customer being overcharged became the thing that took their money and gave them nothing.</li>
 *   <li><b>A drop POCKETED the difference.</b> The all-drops branch rewrote the cart lines and
 *       {@code cart.totalPrice} downwards, the order was built from the reduced figure, and the
 *       amount check only asks whether the payment COVERS the total — so an overpayment sailed
 *       through to PAID with no log, no anomaly and no refund of the difference.</li>
 * </ul>
 *
 * <p>The rule both violate: before capture the customer must never be charged more than they were
 * shown, so re-pricing refuses and they re-confirm; after capture the quoted amount is authoritative,
 * so re-pricing records and never decides. These tests are at the LISTENER, because that is where
 * both bugs lived — a test of {@code CheckoutRepricing} alone passes over both of them.
 *
 * <p>Built like {@link OrderServiceQuiqupHooksTest}: every constructor dependency mocked by type. The
 * service is then a Mockito spy, because the listener reaches {@code createOrder} by self-invocation
 * and a spy is the only way to observe that call — including the phase it is made with — without
 * standing up the whole checkout.
 */
class CartFirstCapturedPaymentTest {

    private static final UUID TX_ID = UUID.fromString("77770000-0000-4000-8000-000000000001");
    private static final UUID CART_ID = UUID.fromString("ccc00000-0000-4000-8000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("00dd0000-0000-4000-8000-000000000003");
    private static final UUID ITEM_ID = UUID.fromString("11110000-0000-4000-8000-000000000004");
    private static final UUID STORE_ID = UUID.fromString("22220000-0000-4000-8000-000000000005");
    private static final UUID PRODUCT_ID = UUID.fromString("33330000-0000-4000-8000-000000000006");
    private static final UUID ADDRESS_ID = UUID.fromString("44440000-0000-4000-8000-000000000007");

    /** What the customer was quoted and what the gateway captured — one unit at 1500. */
    private static final BigDecimal QUOTED = new BigDecimal("1500.00");

    private final Object[] mocks;
    private final OrderService service;
    private final OrderRepository orderRepo;
    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final StoreProductRepository storeProductRepo;
    private final PaymentTransactionRepository txRepo;
    private final PaymentAnomalyService anomalies;
    private final PaidAmountPolicy paidAmountPolicy;

    private final Cart cart = new Cart();
    private final CartItem item = new CartItem();
    private final Product product = new Product();
    private final Store store = new Store();
    private final StoreProduct listing = new StoreProduct();
    private final Order order = new Order();
    private final PaymentTransaction tx = new PaymentTransaction();

    CartFirstCapturedPaymentTest() {
        Constructor<?> ctor = OrderService.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes();
        mocks = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            mocks[i] = types[i].isPrimitive()
                    ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(types[i], 1), 0)
                    : mock(types[i]);
        }
        OrderService real;
        try {
            real = (OrderService) ctor.newInstance(mocks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        service = spy(real);

        orderRepo = firstOfType(OrderRepository.class);
        cartRepo = firstOfType(CartRepository.class);
        cartItemRepo = firstOfType(CartItemRepository.class);
        storeProductRepo = firstOfType(StoreProductRepository.class);
        txRepo = firstOfType(PaymentTransactionRepository.class);
        anomalies = firstOfType(PaymentAnomalyService.class);
        paidAmountPolicy = firstOfType(PaidAmountPolicy.class);

        // A real ObjectMapper: the listener reads the address and the delivery method out of the
        // gateway's metadata, and a mocked one silently returns nothing, which aborts the flow before
        // it reaches anything this test is about.
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        AuthCredentials credentials = new AuthCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setUserId(UUID.randomUUID());

        cart.setId(CART_ID);
        cart.setAuthCredential(credentials);
        cart.setStatus(Cart.CartStatus.CHECKED_OUT);
        cart.setCurrency("AED");
        cart.setCountryCode("AE");
        cart.setTotalPrice(QUOTED);

        store.setId(STORE_ID);
        product.setId(PRODUCT_ID);
        product.setSku("MBP-14-M4");
        listing.setStore(store);
        listing.setProduct(product);

        item.setId(ITEM_ID);
        item.setCart(cart);
        item.setProduct(product);
        item.setStoreId(STORE_ID);
        item.setQuantity(1);
        item.setSelected(true);
        item.setUnitPrice(QUOTED);
        item.setTotalPrice(QUOTED);

        tx.setId(TX_ID);
        tx.setCartId(CART_ID);
        tx.setAppOrderId(null);                 // cart-first: no order exists yet
        tx.setAmount(QUOTED);
        tx.setCurrency("AED");
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setMetadata("{\"addressId\":\"" + ADDRESS_ID + "\",\"shippingFee\":\"0\","
                + "\"deliveryMethod\":\"REGULAR\"}");

        order.setId(ORDER_ID);
        order.setCartId(CART_ID);
        order.setUserId(credentials.getUserId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCurrency("AED");
        order.setSubtotal(QUOTED);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(QUOTED);

        UserAddress address = new UserAddress();
        address.setId(ADDRESS_ID);

        when(txRepo.findById(TX_ID)).thenReturn(Optional.of(tx));
        when(cartRepo.findById(CART_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepo.findByCartIdAndSelectedTrue(CART_ID)).thenReturn(List.of(item));
        when(firstOfType(UserAddressRepository.class).findById(ADDRESS_ID))
                .thenReturn(Optional.of(address));
        when(orderRepo.findFirstByCartIdAndStatusIn(eq(CART_ID), anyList()))
                .thenReturn(Optional.empty());
        when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // The captured amount covers the order total, because it IS the order total: the quote is what
        // the gateway charged. Any test that wants the underpayment branch overrides this.
        when(paidAmountPolicy.covers(any(), any())).thenReturn(true);

        OrderResponse built = new OrderResponse();
        built.setId(ORDER_ID);
        doReturn(built).when(service).createOrder(any(UUID.class), any(UUID.class),
                any(CreateOrderRequest.class), any(OrderService.CapturePhase.class));
    }

    @SuppressWarnings("unchecked")
    private <T> T firstOfType(Class<T> type) {
        for (Object m : mocks) {
            if (type.isInstance(m)) return (T) m;
        }
        throw new IllegalStateException("no ctor param of type " + type);
    }

    /** The live listing the basket will be re-priced against, at {@code price} with no discount. */
    private void listingPricedAt(String price) {
        listing.setStorePrice(new BigDecimal(price));
        when(storeProductRepo.findActiveByStoreIdsAndProductIds(anyList(), anyList()))
                .thenReturn(List.of(listing));
    }

    private void paymentSettles() {
        service.onPaymentSucceeded(new PaymentSucceededEvent(null, TX_ID));
    }

    // ── A rise in the payment window ─────────────────────────────────────────

    @Test
    void aCapturedPaymentWhoseBasketGotDEARERStillEndsWithAnOrderAndAnAnomaly() {
        // The sale ended between the capture and this webhook. The old code threw a 409 from inside
        // this transaction: rolled back, no order, no anomaly, customer charged.
        listingPricedAt("2000.00");

        assertDoesNotThrow(this::paymentSettles,
                "a price rise must never escape the listener — the throw rolls the transaction back and "
                        + "leaves a captured payment with no order at all");

        verify(service).createOrder(any(), any(), any(CreateOrderRequest.class),
                eq(OrderService.CapturePhase.AFTER_CAPTURE));
        verify(anomalies).recordAndAlert(eq(PaymentAnomalyKind.PRICE_CHANGED_AFTER_CAPTURE),
                eq(tx), eq(ORDER_ID), any(), anyString(), anyString());
        assertEquals(OrderStatus.PAID, order.getStatus(),
                "the customer paid what they were quoted, so the order is settled — the difference is a "
                        + "finding for a human, not a reason to withhold the goods");
    }

    @Test
    void aRiseIsChargedAtTheQUOTEDTotalRatherThanTheNewOne() {
        // The order must not be re-priced UP either: they agreed to 1500 and 1500 was taken.
        listingPricedAt("2000.00");

        paymentSettles();

        assertEquals(0, QUOTED.compareTo(order.getTotalAmount()),
                "the captured amount is the order's total; re-pricing it up would manufacture an "
                        + "underpayment out of a payment that covered exactly what was agreed");
        verify(cartItemRepo, never()).save(any(CartItem.class));
    }

    // ── A drop in the payment window ─────────────────────────────────────────

    @Test
    void aCapturedPaymentWhoseBasketGotCHEAPERDoesNotSilentlyRecordALowerTotal() {
        // A sale STARTED between capture and webhook. The customer paid 1500; the basket now prices at
        // 1200. Rewriting the order down to 1200 made the overpayment invisible — the amount check only
        // asks whether the payment covers the total, so 1500 >= 1200 passed and the 300 was kept.
        listingPricedAt("1200.00");

        paymentSettles();

        assertEquals(0, QUOTED.compareTo(order.getTotalAmount()),
                "the order must still record what was actually charged, or the difference cannot be "
                        + "found, let alone refunded");
        verify(anomalies).recordAndAlert(eq(PaymentAnomalyKind.PRICE_CHANGED_AFTER_CAPTURE),
                eq(tx), eq(ORDER_ID), any(), anyString(), anyString());
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void aBasketWhosePriceDidNotMoveRecordsNothing() {
        // The overwhelmingly common case, and the one that must stay quiet: an anomaly per payment
        // would make the queue worthless.
        listingPricedAt("1500.00");

        paymentSettles();

        verify(anomalies, never()).recordAndAlert(any(), any(), any(), any(), anyString(), anyString());
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    // ── Nothing may escape unrecorded ────────────────────────────────────────

    @Test
    void anythingElseEscapingOrderCreationIsRecordedBeforeItRollsBack() {
        // The backstop. The money is captured, so the invariant is about the MONEY and not about which
        // exception carries the news: whatever escapes createOrder, a record of it must survive the
        // rollback (the anomaly insert is REQUIRES_NEW) and somebody must be alerted.
        listingPricedAt("1500.00");
        doThrow(new IllegalStateException("promo reservation vanished"))
                .when(service).createOrder(any(), any(), any(CreateOrderRequest.class),
                        any(OrderService.CapturePhase.class));

        assertThrows(IllegalStateException.class, this::paymentSettles,
                "rethrown on purpose: a partial stock take inside this transaction must roll back");

        verify(anomalies).recordAndAlert(eq(PaymentAnomalyKind.ORDER_CREATION_FAILED),
                eq(tx), eq(null), any(), anyString(), anyString());
    }

    @Test
    void even_aPriceRefusalReachingTheListenerLeavesARecordRatherThanVanishing() {
        // Belt and braces for the exact exception that caused this: re-pricing no longer refuses after
        // capture, so nothing throws this here any more — but if something ever does again, it must not
        // be able to roll the transaction back in silence. That silence was the whole defect: the
        // customer charged, a gateway confirmation on screen, no order, no anomaly, no refund, nobody
        // alerted.
        listingPricedAt("1500.00");
        doThrow(new CartPriceChangedException(ITEM_ID, "MBP-14-M4", QUOTED, new BigDecimal("2000.00")))
                .when(service).createOrder(any(), any(), any(CreateOrderRequest.class),
                        any(OrderService.CapturePhase.class));

        assertThrows(CartPriceChangedException.class, this::paymentSettles);

        verify(anomalies).recordAndAlert(eq(PaymentAnomalyKind.ORDER_CREATION_FAILED),
                eq(tx), eq(null), any(), anyString(), anyString());
    }

    @Test
    void aPriceAnomalyIsNotAutoRefundedAndTheBackstopIsNotEither() {
        // Both of the new kinds need a human. An order exists for the first, so refunding part of a
        // settled payment is a decision; the second happens for an unknown reason and a webhook retry
        // may well build the order a minute later, which an automatic refund would race.
        assertFalse(PaymentAnomalyKind.PRICE_CHANGED_AFTER_CAPTURE.autoRefunds());
        assertFalse(PaymentAnomalyKind.ORDER_CREATION_FAILED.autoRefunds());
        assertTrue(PaymentAnomalyKind.STOCK_UNAVAILABLE.autoRefunds(),
                "the one where no order exists and nothing will ever ship still refunds itself");
    }

    // ── And the pre-capture refusal is still armed ────────────────────────────

    @Test
    void beforeCaptureAPriceRiseStillRefusesTheCheckout() {
        // The protection being narrowed, not removed. Nothing has been charged here, so a basket that
        // got dearer is handed back for the customer to re-confirm.
        listingPricedAt("2000.00");

        assertThrows(CartPriceChangedException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "repriceForCheckout",
                        cart, List.of(item), OrderService.CapturePhase.BEFORE_CAPTURE));

        verify(cartItemRepo, never()).save(any(CartItem.class));
        verify(cartRepo, never()).save(any(Cart.class));
    }

    @Test
    void beforeCaptureADropIsAppliedAndCharged() {
        // Unchanged behaviour, and the reason the refusal is one-directional: a 409 saying "you are
        // paying less" is a customer who cannot check out.
        listingPricedAt("1200.00");

        ReflectionTestUtils.invokeMethod(service, "repriceForCheckout",
                cart, List.of(item), OrderService.CapturePhase.BEFORE_CAPTURE);

        assertEquals(0, new BigDecimal("1200.00").compareTo(item.getUnitPrice()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(cart.getTotalPrice()));
        verify(cartItemRepo).save(item);
        verify(cartRepo).save(cart);
    }

    @Test
    void afterCaptureRePricingWritesNothingInEitherDirection() {
        // The quoted total stands, so there is nothing to write: not the lines, not the cart total.
        // Moving cart.totalPrice is also what made CheckoutIdentity stop recognising the
        // PENDING_PAYMENT order standing on this cart, which was then superseded and rebuilt lower.
        listingPricedAt("1200.00");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "repriceForCheckout",
                cart, List.of(item), OrderService.CapturePhase.AFTER_CAPTURE));

        assertEquals(0, QUOTED.compareTo(item.getUnitPrice()), "the line keeps the price it was quoted at");
        assertEquals(0, QUOTED.compareTo(cart.getTotalPrice()), "and so does the basket");
        verify(cartItemRepo, never()).save(any(CartItem.class));
        verify(cartRepo, never()).save(any(Cart.class));
    }
}
