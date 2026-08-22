package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.dto.CreateOrderRequest;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins when a prior PENDING_PAYMENT order may stand in for the checkout being submitted.
 *
 * <p>Each direction of error here has a distinct cost. Reusing too eagerly is the production bug
 * this replaces: the customer changed something — a promo, an address, pickup instead of delivery —
 * and was charged and shipped the OLD checkout, because only the subtotal was compared and none of
 * those changes move it. Rebuilding too eagerly quietly re-runs supersede on every double-tap,
 * churning order ids, promo reservations and stock for no reason. So these tests hold the predicate
 * from both sides: everything that decides money or destination must fail it, and pure noise —
 * BigDecimal scale, coupon case, item order — must not.
 */
class CheckoutIdentityTest {

    private static final UUID PRODUCT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VARIANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STORE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADDRESS = UUID.fromString("44444444-4444-4444-4444-444444444444");

    // ── fixture: one settled checkout, mirrored on both sides ────────────────

    private static Cart cart() {
        Cart c = new Cart();
        c.setTotalPrice(new BigDecimal("200.00"));
        c.setCurrency("AED");
        return c;
    }

    private static CartItem cartItem(UUID productId, UUID variantId, UUID storeId,
                                     int qty, String unitPrice) {
        CartItem i = new CartItem();
        Product p = new Product();
        p.setId(productId);
        i.setProduct(p);
        if (variantId != null) {
            ProductVariant v = new ProductVariant();
            v.setId(variantId);
            i.setVariant(v);
        }
        i.setStoreId(storeId);
        i.setQuantity(qty);
        i.setUnitPrice(new BigDecimal(unitPrice));
        return i;
    }

    private static OrderItem orderItem(UUID productId, UUID variantId, UUID storeId,
                                       int qty, String unitPrice) {
        OrderItem i = new OrderItem();
        i.setProductId(productId);
        i.setVariantId(variantId);
        i.setStoreId(storeId);
        i.setQuantity(qty);
        i.setUnitPrice(new BigDecimal(unitPrice));
        return i;
    }

    private static Order prior() {
        Order o = new Order();
        o.setSubtotal(new BigDecimal("200.00"));
        o.setShippingFee(new BigDecimal("15.00"));
        o.setCurrency("AED");
        o.setCountryCode("UAE");
        o.setCountry("UAE");
        o.setCouponCode(null);
        o.setDeliveryMethod(DeliveryMethod.REGULAR);
        o.setDeliveryAddressId(ADDRESS);
        o.getItems().add(orderItem(PRODUCT, VARIANT, STORE, 2, "100.00"));
        return o;
    }

    private static OrderService.FulfilmentPlan deliveryPlan() {
        return new OrderService.FulfilmentPlan(DeliveryMethod.REGULAR, new BigDecimal("15.00"),
                "2-3 days", null, null, null, ADDRESS, null, "UAE");
    }

    private static CreateOrderRequest req(String coupon) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setCouponCode(coupon);
        return r;
    }

    private static List<CartItem> basket() {
        return List.of(cartItem(PRODUCT, VARIANT, STORE, 2, "100.00"));
    }

    private static boolean same(Order prior, OrderService.FulfilmentPlan plan, String coupon) {
        return CheckoutIdentity.isSameCheckout(prior, cart(), basket(), req(coupon), plan, "AED", "UAE");
    }

    // ── the double-tap must still reuse ──────────────────────────────────────

    @Test
    void anUnchangedCheckoutReuses() {
        assertTrue(same(prior(), deliveryPlan(), null));
    }

    @Test
    void bigDecimalScaleIsNotAChange() {
        // 200 vs 200.00: equal money. BigDecimal.equals would say otherwise and turn every
        // double-tap into a supersede — churning order ids, stock and promo claims for nothing.
        Order o = prior();
        o.setSubtotal(new BigDecimal("200"));
        o.setShippingFee(new BigDecimal("15"));
        assertTrue(same(o, deliveryPlan(), null));
    }

    @Test
    void couponCaseAndWhitespaceAreNotAChange() {
        Order o = prior();
        o.setCouponCode("SAVE10");
        assertTrue(same(o, deliveryPlan(), "  save10 "));
    }

    @Test
    void aBlankCouponEqualsNoCoupon() {
        // The storefront omits the field entirely when no promo is applied; a stored null must not
        // read as different from a submitted empty string.
        assertTrue(same(prior(), deliveryPlan(), ""));
        assertTrue(same(prior(), deliveryPlan(), null));
    }

    @Test
    void basketOrderDoesNotMatter() {
        UUID p2 = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Order o = prior();
        o.getItems().add(orderItem(p2, null, STORE, 1, "50.00"));
        o.setSubtotal(new BigDecimal("250.00"));

        Cart c = cart();
        c.setTotalPrice(new BigDecimal("250.00"));
        List<CartItem> reversed = List.of(
                cartItem(p2, null, STORE, 1, "50.00"),
                cartItem(PRODUCT, VARIANT, STORE, 2, "100.00"));

        assertTrue(CheckoutIdentity.isSameCheckout(o, c, reversed, req(null), deliveryPlan(), "AED", "UAE"));
    }

    // ── everything the customer can change must supersede ────────────────────

    @Test
    void applyingAPromoSupersedes() {
        // THE original bug: the promo does not move the subtotal, so the old test reused the old
        // order and the discount was shown but never charged.
        assertFalse(same(prior(), deliveryPlan(), "SAVE10"));
    }

    @Test
    void removingAPromoSupersedes() {
        Order o = prior();
        o.setCouponCode("SAVE10");
        assertFalse(same(o, deliveryPlan(), null));
    }

    @Test
    void switchingToPickupSupersedes() {
        // The other half of the original bug: pickup was silently ignored and the goods shipped to
        // the address with a shipping fee anyway.
        OrderService.FulfilmentPlan pickup = new OrderService.FulfilmentPlan(
                DeliveryMethod.PICKUP, BigDecimal.ZERO, "today",
                STORE, "Buyology Downtown", "Emaar Square 1", null, null, "UAE");
        assertFalse(same(prior(), pickup, null));
    }

    @Test
    void aDifferentAddressSupersedes() {
        OrderService.FulfilmentPlan other = new OrderService.FulfilmentPlan(
                DeliveryMethod.REGULAR, new BigDecimal("15.00"), "2-3 days",
                null, null, null, UUID.randomUUID(), null, "UAE");
        assertFalse(same(prior(), other, null));
    }

    @Test
    void aChangedShippingFeeSupersedes() {
        // The fee is compared POST-resolution: crossing the free-shipping threshold, or an express
        // downgrade, changes what the customer pays even with an identical basket.
        OrderService.FulfilmentPlan free = new OrderService.FulfilmentPlan(
                DeliveryMethod.REGULAR, BigDecimal.ZERO, "2-3 days",
                null, null, null, ADDRESS, null, "UAE");
        assertFalse(same(prior(), free, null));
    }

    @Test
    void aResolvedMethodChangeSupersedes() {
        OrderService.FulfilmentPlan express = new OrderService.FulfilmentPlan(
                DeliveryMethod.EXPRESS, new BigDecimal("20.00"), "30 minutes",
                null, null, null, ADDRESS, null, "UAE");
        assertFalse(same(prior(), express, null));
    }

    @Test
    void aQuantityChangeSupersedesEvenAtTheSameSubtotal() {
        // Two different baskets can share a subtotal; the goods shipped are still different.
        Order o = prior();
        o.getItems().clear();
        o.getItems().add(orderItem(PRODUCT, VARIANT, STORE, 1, "200.00"));
        assertFalse(same(o, deliveryPlan(), null));
    }

    @Test
    void aCurrencyChangeSupersedes() {
        Order o = prior();
        o.setCurrency("USD");
        assertFalse(same(o, deliveryPlan(), null));
    }

    @Test
    void aSubtotalChangeStillSupersedes() {
        // The one thing the old check DID catch must obviously keep superseding.
        Order o = prior();
        o.setSubtotal(new BigDecimal("180.00"));
        assertFalse(same(o, deliveryPlan(), null));
    }
}
