package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.repository.StoreProductRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What a cart line costs RIGHT NOW, and whether that differs from the price stamped on it.
 *
 * <p>Why this exists at all: a cart line freezes its unit price when it is added, and until V60
 * nothing ever looked at it again. {@code CartService} wrote it, the cart response read it back off
 * the row, and {@code OrderService.createOrder} copied it verbatim onto the order — the one pricing
 * call in OrderService was in {@code createBuyNowOrder}, a different entry point. So the customer was
 * shown, and charged, the price that was true at add-to-cart, forever.
 * Cart and order could never disagree with each other; both could disagree with the catalogue
 * indefinitely, and nobody noticed because a discount never used to change on its own.
 *
 * <p>A flash sale makes that indefensible in both directions. A basket filled during the sale and
 * checked out a month later was charged the sale price — the sale immortal, per cart. A basket filled
 * the day before a sale started was charged full price while the product page advertised the
 * discount, with no "price dropped" signal anywhere.
 *
 * <p>So the price is re-read from the live listing every time the cart is rendered and again at
 * checkout, through this one class, and the rule is:
 *
 * <ul>
 *   <li><b>Rendering the cart re-prices it.</b> The line is corrected and saved, and the response
 *       says so per line ({@code priceChanged} plus the old figure), so the client can tell the
 *       shopper "this item's sale ended — the price has been updated" rather than moving a number
 *       silently.</li>
 *   <li><b>Checkout re-prices, and refuses only what got DEARER.</b> Order creation re-prices the
 *       same way, before it decides anything else about the checkout. A line that is now cheaper is
 *       corrected and charged at the lower price — never refused, because a 409 no released client
 *       handles would turn a price drop into a customer who cannot buy. A line that is now dearer is
 *       handed back with a 409 so the shopper re-confirms, because carrying on means charging more
 *       than the screen said. The asymmetry lives in {@code CheckoutRepricing}.</li>
 * </ul>
 *
 * <p><b>A variantId does not change any of this, and that is a decision rather than an omission.</b>
 * Every line — variant-bearing or not — is priced from its PARENT store listing, through
 * {@code StoreProduct.effectivePrice}. {@code store_product_variants.store_price} is never
 * consulted for money. The reason is that the parent row is the ONLY price any customer surface
 * quotes: the card, the rail, the search result and the product page all resolve the parent listing,
 * so a line priced from the variant row is a second notion of "the price" and is guaranteed to
 * diverge from the advertised one in one direction or the other. It also cannot be made consistent
 * across clients: the web storefront posts {storeId, productId, quantity} and sends no variantId at
 * all, so the same basket would price differently depending on which app filled it.
 *
 * <p>So a variantId identifies the line (which SKU is being shipped) and caps its stock; it never
 * decides the price. Quoting and charging a genuine per-variant price is the correct e-commerce
 * model and is deliberately deferred — it needs the catalogue to advertise the variant's price
 * everywhere first, which is a backend change plus both clients.
 *
 * <p>This is the only arrangement where the price SHOWN and the price CHARGED provably agree. They
 * used to agree only by both being stale, and staleness is precisely what a flash sale turns into
 * money.
 */
public final class CartLinePricing {

    private CartLinePricing() {
    }

    /** The unit price a line should carry now, and the "was" price to strike through. */
    public record Priced(BigDecimal unitPrice, BigDecimal originalUnitPrice) {
    }

    /** What a cart line points at: the store listing is identified by the pair, not by an id of its own. */
    public record ListingKey(UUID storeId, UUID productId) {
    }

    /**
     * Every listing a whole basket needs to be priced, fetched in ONE query.
     *
     * <p>Both re-pricing sites take one of these and ask it per line, so neither can develop its own
     * idea of what a line costs — which is the bug class this whole change exists to close.
     */
    public record Basket(Map<ListingKey, StoreProduct> listings) {

        private static final Basket EMPTY = new Basket(Map.of());

        /** The listing a line is priced from — the parent listing, for a variant line too. */
        public StoreProduct listingFor(CartItem line) {
            if (line.getStoreId() == null || line.getProduct() == null) return null;
            return listings.get(new ListingKey(line.getStoreId(), line.getProduct().getId()));
        }

        /**
         * What this line costs now, or null when there is no live listing to price it from.
         *
         * <p>Null is NOT a price of zero and not a price change: a listing that has been switched off
         * is an availability problem, and this class deliberately does not turn it into a 409 about
         * money. Such a line is refused downstream instead — the checkout's stock guards resolve the
         * listing again and cannot find it.
         */
        public Priced priceFor(CartItem line, Instant now) {
            StoreProduct listing = listingFor(line);
            return listing == null ? null : price(listing, now);
        }
    }

    /**
     * The live listings behind a whole basket.
     *
     * <p>Both re-pricing sites need this and both used to ask per line — a cart GET issued one query
     * per row before it could price anything, and checkout issued the same set again. Sharing the
     * lookup also keeps the two on the same predicate: {@code isActive} only, exactly what the
     * single-row {@code findByStore_IdAndProduct_IdAndIsActiveTrue} it replaces asked for, so which
     * lines get re-priced has not changed.
     */
    public static Basket liveListings(StoreProductRepository listingRepository, List<CartItem> lines) {
        List<CartItem> priceable = lines.stream()
                .filter(i -> i.getStoreId() != null && i.getProduct() != null)
                .toList();
        if (priceable.isEmpty()) return Basket.EMPTY;

        List<UUID> storeIds = priceable.stream().map(CartItem::getStoreId).distinct().toList();
        List<UUID> productIds = priceable.stream()
                .map(i -> i.getProduct().getId()).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) return Basket.EMPTY;

        Map<ListingKey, StoreProduct> byKey = new HashMap<>();
        for (StoreProduct sp : listingRepository.findActiveByStoreIdsAndProductIds(storeIds, productIds)) {
            byKey.put(new ListingKey(sp.getStore().getId(), sp.getProduct().getId()), sp);
        }
        return new Basket(byKey);
    }

    /** Prices a line off its live store listing — the one pricing call every basket path makes. */
    public static Priced price(StoreProduct storeProduct, Instant now) {
        return new Priced(
                storeProduct.effectivePrice(now),
                storeProduct.hasDiscount(now) ? storeProduct.getStorePrice() : null);
    }

    /**
     * Whether a stamped price differs from the live one.
     *
     * <p>{@code compareTo}, not {@code equals}: 999.00 and 999.000 are the same money and a scale
     * difference from a currency conversion or a column definition must not look like a price change
     * and block a checkout.
     *
     * <p>Returns false when either side is unknown — a line whose listing has vanished is a stock
     * problem for the order path to refuse with a message about stock, not a pricing dispute.
     */
    public static boolean moved(BigDecimal stamped, BigDecimal live) {
        if (stamped == null || live == null) return false;
        return stamped.compareTo(live) != 0;
    }

    /**
     * Whether the struck-through "was" figure differs — asked separately from {@link #moved} because
     * the two can move independently, and only the charged price was ever being watched.
     *
     * <p>The case that got through: a line stamped at unitPrice=800 / originalUnitPrice=1000 on a
     * FIXED 800 sale, whose listing then has its storePrice lowered to 850. The charged price is still
     * 800, so nothing re-stamped the line, and the basket went on advertising "was 1000" while the
     * product page said "was 850" — a saving of 200 the shop was not offering, on the screen where the
     * customer decides. The same stale figure is then copied onto {@code order_items.original_unit_price}.
     *
     * <p>Null is a VALUE here, not the "unknown" it means for a unit price: it says this line is not
     * on a sale. So a stamped figure appearing or disappearing IS a movement, and must be re-stamped —
     * that is how a line keeps its strike-through after its sale ends. Only the caller's "is there a
     * live listing at all" check may skip a line, and it does that before asking this.
     */
    public static boolean originalMoved(BigDecimal stamped, BigDecimal live) {
        if (stamped == null || live == null) {
            return !(stamped == null && live == null);
        }
        return stamped.compareTo(live) != 0;
    }
}
