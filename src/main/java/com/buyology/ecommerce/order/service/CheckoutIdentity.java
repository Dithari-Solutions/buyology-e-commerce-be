package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.dto.CreateOrderRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether a prior PENDING_PAYMENT order still IS the checkout the customer is submitting.
 *
 * <p>The old test was the cart subtotal alone, and that is the bug this class replaces: a customer
 * who abandoned at the gateway, came back, applied a promo and switched to store pickup got the old
 * order verbatim — old total, no discount, goods shipped to the address anyway — because none of
 * those changes move the subtotal. Reuse is only safe when EVERY input that decides price or
 * fulfilment matches; anything less quietly charges or ships something the customer did not review.
 *
 * <p>Deliberately free of Spring and repositories, so the predicate is testable without
 * constructing OrderService's thirty-odd-argument constructor.
 */
final class CheckoutIdentity {

    private CheckoutIdentity() {
    }

    /**
     * True only when the prior order matches the current checkout on every price- and
     * fulfilment-deciding input. The double-tap this idempotency exists for passes trivially —
     * nothing changed. Any real change fails one clause, and the caller supersedes.
     */
    static boolean isSameCheckout(Order prior, Cart cart, List<CartItem> cartItems,
                                  CreateOrderRequest req, OrderService.FulfilmentPlan plan,
                                  String currency, String orderCountryCode) {
        return eqMoney(prior.getSubtotal(), cart.getTotalPrice())              // the price base
                && eqMoney(prior.getShippingFee(), plan.shippingFee())         // fee, post-downgrade
                && eqIgnoreCaseNullable(prior.getCurrency(), currency)         // what they pay in
                && eqIgnoreCaseNullable(prior.getCountryCode(), orderCountryCode)
                && eqIgnoreCaseNullable(prior.getCountry(), plan.country())
                && eqCoupon(prior.getCouponCode(), req.getCouponCode())        // the promo INPUT
                && prior.getDeliveryMethod() == plan.method()                  // the RESOLVED method
                && Objects.equals(prior.getPickupStoreId(), plan.pickupStoreId())
                && Objects.equals(prior.getDeliveryAddressId(), plan.addressId())
                && sameBasket(prior.getItems(), cartItems);
    }

    /**
     * Money equality by value. NEVER BigDecimal.equals — 100 and 100.00 must count as equal, or
     * every genuine double-tap "differs" on scale alone and rebuilds the order it should reuse.
     */
    static boolean eqMoney(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.compareTo(right) == 0;
    }

    static boolean eqIgnoreCaseNullable(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.equalsIgnoreCase(b);
    }

    /**
     * Coupon equality where null and blank are the SAME absence — the storefront sends nothing at
     * all when no promo is applied, and a stored null must not read as different from a submitted
     * empty string. Case-insensitive because the promo lookup itself is.
     */
    static boolean eqCoupon(String a, String b) {
        boolean aBlank = a == null || a.isBlank();
        boolean bBlank = b == null || b.isBlank();
        if (aBlank || bBlank) {
            return aBlank && bBlank;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Multiset equality over (product, variant, store, quantity, unit price).
     *
     * <p>The subtotal already participates above, but two different baskets can share a subtotal —
     * swap a 100 AED case for a 100 AED cable and the total is identical while the goods shipped
     * are not. Order-insensitive, because neither side guarantees one.
     */
    static boolean sameBasket(List<OrderItem> priorItems, List<CartItem> cartItems) {
        List<OrderItem> prior = priorItems == null ? List.of() : priorItems;
        List<CartItem> current = cartItems == null ? List.of() : cartItems;
        if (prior.size() != current.size()) {
            return false;
        }
        List<String> a = new ArrayList<>(prior.size());
        for (OrderItem i : prior) {
            a.add(key(i.getProductId(), i.getVariantId(), i.getStoreId(), i.getQuantity(), i.getUnitPrice()));
        }
        List<String> b = new ArrayList<>(current.size());
        for (CartItem i : current) {
            b.add(key(i.getProduct() == null ? null : i.getProduct().getId(),
                    i.getVariant() == null ? null : i.getVariant().getId(),
                    i.getStoreId(), i.getQuantity(), i.getUnitPrice()));
        }
        a.sort(String::compareTo);
        b.sort(String::compareTo);
        return a.equals(b);
    }

    private static String key(Object productId, Object variantId, Object storeId,
                              Integer quantity, BigDecimal unitPrice) {
        return productId + "|" + variantId + "|" + storeId + "|" + quantity + "|"
                + (unitPrice == null ? "null" : unitPrice.stripTrailingZeros().toPlainString());
    }
}
