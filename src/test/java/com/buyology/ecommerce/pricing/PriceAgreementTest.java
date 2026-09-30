package com.buyology.ecommerce.pricing;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.service.CartLinePricing;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The owner's complaint, as an assertion: the price a customer is SHOWN and the price they are
 * CHARGED must be the same number — for a plain listing and for one that has variants, with either
 * kind of discount, inside its window and outside it.
 *
 * <p>There is exactly ONE number a customer is ever shown for a product, and it comes from the parent
 * {@code store_products} row: every card, rail, category page, search result, store option and PDP
 * resolves that row through {@code StoreProduct.effectivePrice}. So every charge path has to agree
 * with THAT number — the cart line, the checkout re-price, Buy Now, the order record and the
 * abandoned-cart email all price through the same function on the same row.
 *
 * <p><b>A variantId does not enter into it.</b> Two earlier attempts both broke by treating
 * {@code store_product_variants.store_price} as a price:
 *
 * <ol>
 *   <li>Charging a variant line from the variant row while every surface quoted the parent. A shopper
 *       saw "750, was 1000", tapped through, and the basket said 1000.</li>
 *   <li>Then quoting the cheapest variant on the CARD so the card would match. That was worse: the web
 *       storefront posts {storeId, productId, quantity} and sends no variantId, so its lines were
 *       still priced from the parent — the card advertised 900 and the checkout charged 1000, a
 *       divergence in the direction that costs the customer money, on the busier client, where there
 *       had been none.</li>
 * </ol>
 *
 * <p>Hence the rule these tests pin: a variantId identifies the line and caps its stock, and the
 * PARENT listing decides the money. {@link #aVariantLineAndAPlainLineOfTheSameListingAgree} is the
 * assertion that keeps the two clients charging the same basket the same amount.
 *
 * <p>Why the assertions run the LISTING surface and the CART and the ORDER against each other rather
 * than checking the cart alone: the bug was never inside one component. It was two code paths holding
 * different opinions about one price. A test that only pins the cart re-tests the half that was
 * already self-consistent and would have passed throughout the entire period the shop was mispricing.
 */
class PriceAgreementTest {

    private static final Instant DURING = Instant.parse("2026-03-26T10:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-03-20T10:00:00Z");
    private static final Instant AFTER = Instant.parse("2026-04-05T10:00:00Z");

    private static final Instant STARTS = Instant.parse("2026-03-25T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-31T20:00:00Z");

    /** Parent 1000. The two variant prices straddle it, which the data model allows — and neither is charged. */
    private static final BigDecimal PARENT = new BigDecimal("1000.00");
    private static final BigDecimal CHEAP_VARIANT = new BigDecimal("900.00");
    private static final BigDecimal DEAR_VARIANT = new BigDecimal("1500.00");

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private StoreProduct listing(Product.DiscountType type, String value, Instant startsAt, Instant endsAt) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setSku("MBP-14-M4");
        StoreProduct sp = new StoreProduct(store, product, PARENT);
        sp.setId(UUID.randomUUID());
        sp.setDiscountType(type);
        sp.setDiscountValue(value == null ? null : new BigDecimal(value));
        sp.setDiscountStartsAt(startsAt);
        sp.setDiscountEndsAt(endsAt);
        return sp;
    }

    /**
     * What the catalogue ADVERTISES, by the same route {@code ProductService.buildStoreOption} takes:
     * the static form on the raw projection columns, because a card is built from a query row and never
     * from an entity. If this and {@link #cartLine} ever diverge, the shop is back to advertising one
     * number and charging another.
     */
    private BigDecimal advertised(StoreProduct listing, Instant now) {
        return StoreProduct.effectivePrice(listing.getStorePrice(), listing.getDiscountType(),
                listing.getDiscountValue(), listing.getDiscountStartsAt(), listing.getDiscountEndsAt(), now);
    }

    /** What the CART stamps on the line, by the route CartService.addItem takes. */
    private CartLinePricing.Priced cartLine(StoreProduct listing, Instant now) {
        return CartLinePricing.price(listing, now);
    }

    /**
     * What a line carrying a variant costs, through {@code CartLinePricing.Basket} — the lookup both
     * re-pricing sites actually call. This is the wiring, not just the arithmetic.
     */
    private CartLinePricing.Priced variantLine(StoreProduct listing, Instant now) {
        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        CartLinePricing.Basket basket = new CartLinePricing.Basket(Map.of(
                new CartLinePricing.ListingKey(listing.getStore().getId(), listing.getProduct().getId()),
                listing));
        CartItem line = new CartItem(new Cart(), listing.getProduct(), variant, 1,
                DEAR_VARIANT, listing.getStore().getId());
        return basket.priceFor(line, now);
    }

    /** What the ORDER line ends up holding, by the route OrderService.createOrder takes. */
    private OrderItem orderLine(CartLinePricing.Priced priced, int quantity) {
        CartItem cartItem = new CartItem(new Cart(), new Product(), null, quantity,
                priced.unitPrice(), UUID.randomUUID());
        cartItem.setOriginalUnitPrice(priced.originalUnitPrice());

        OrderItem item = new OrderItem();
        item.setQuantity(cartItem.getQuantity());
        item.setUnitPrice(cartItem.getUnitPrice());
        item.setOriginalUnitPrice(cartItem.getOriginalUnitPrice());
        item.setTotalPrice(cartItem.getTotalPrice());
        return item;
    }

    // ── PERCENTAGE ────────────────────────────────────────────────────────────

    @Test
    void aPercentageSaleIsAdvertisedChargedAndRecordedAsOneNumber() {
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        BigDecimal shown = advertised(sp, DURING);
        CartLinePricing.Priced cart = cartLine(sp, DURING);
        OrderItem order = orderLine(cart, 2);

        assertEquals(new BigDecimal("750.00"), shown, "25% off 1000");
        assertEquals(shown, cart.unitPrice(), "the catalogue and the basket must quote one number");
        assertEquals(shown, order.getUnitPrice(), "and the order must charge that same number");
        assertEquals(PARENT, cart.originalUnitPrice(), "struck through: the listing's own former price");
        assertEquals(PARENT, order.getOriginalUnitPrice(),
                "and the order records which advertised price was honoured");
        assertEquals(new BigDecimal("1500.00"), order.getTotalPrice());
    }

    @Test
    void aPercentageSaleOnAVariantBearingListingChargesTheAdvertisedNumberToo() {
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        CartLinePricing.Priced cart = variantLine(sp, DURING);

        assertNotNull(cart, "a variant line must not be invisible to re-pricing");
        assertEquals(advertised(sp, DURING), cart.unitPrice());
        assertEquals(PARENT, cart.originalUnitPrice());
        assertEquals(advertised(sp, DURING), orderLine(cart, 1).getUnitPrice());
    }

    // ── FIXED ─────────────────────────────────────────────────────────────────

    @Test
    void aFixedSalePriceIsAdvertisedChargedAndRecordedAsOneNumber() {
        // FIXED stores an absolute sale price, so effectivePrice returns discountValue verbatim.
        StoreProduct sp = listing(Product.DiscountType.FIXED, "750", STARTS, ENDS);

        BigDecimal shown = advertised(sp, DURING);
        CartLinePricing.Priced cart = cartLine(sp, DURING);

        assertEquals(new BigDecimal("750"), shown);
        assertEquals(shown, cart.unitPrice());
        assertEquals(shown, orderLine(cart, 1).getUnitPrice());
        assertEquals(PARENT, cart.originalUnitPrice());
    }

    @Test
    void aFixedSalePriceOnAVariantBearingListingChargesTheAdvertisedNumberToo() {
        StoreProduct sp = listing(Product.DiscountType.FIXED, "750", STARTS, ENDS);

        assertEquals(advertised(sp, DURING), variantLine(sp, DURING).unitPrice());
    }

    @Test
    void fixedAndPercentageExpressingTheSameSaleChargeTheSameMoney() {
        // An admin who types "sale price 750" and one who types "25% off" have expressed the same
        // campaign on a 1000 listing, so the shop must charge the same money for either.
        StoreProduct fixed = listing(Product.DiscountType.FIXED, "750", STARTS, ENDS);
        StoreProduct percentage = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        assertEquals(0, cartLine(percentage, DURING).unitPrice()
                .compareTo(cartLine(fixed, DURING).unitPrice()));
    }

    // ── A variantId is not a price ─────────────────────────────────────────────

    @Test
    void aVariantLineAndAPlainLineOfTheSameListingAgree() {
        // The invariant option A rests on. The web storefront sends no variantId and the app sends one,
        // so the moment these two differ the two clients charge different money for the same basket —
        // and only one of them can match the card.
        for (Product.DiscountType type : Product.DiscountType.values()) {
            StoreProduct sp = listing(type, type == Product.DiscountType.FIXED ? "750" : "25", STARTS, ENDS);

            assertEquals(cartLine(sp, DURING).unitPrice(), variantLine(sp, DURING).unitPrice(),
                    "a variantId picks the SKU and the stock ceiling, never the price (" + type + ")");
            assertEquals(cartLine(sp, DURING).originalUnitPrice(),
                    variantLine(sp, DURING).originalUnitPrice(),
                    "including the struck-through figure (" + type + ")");
        }
    }

    @Test
    void neitherVariantPriceIsEverChargedHoweverItStraddlesTheParent() {
        // 900 and 1500 are both recorded on store_product_variants and neither is money. Quoting either
        // is what produced both of the earlier failures.
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        BigDecimal charged = variantLine(sp, DURING).unitPrice();
        assertNotEquals(0, charged.compareTo(CHEAP_VARIANT));
        assertNotEquals(0, charged.compareTo(DEAR_VARIANT));
        assertEquals(0, charged.compareTo(new BigDecimal("750.00")));
    }

    // ── The window ───────────────────────────────────────────────────────────

    @Test
    void outsideTheWindowEverySurfaceQuotesTheListPriceAndShowsNoSaving() {
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        for (Instant outside : new Instant[]{BEFORE, AFTER}) {
            CartLinePricing.Priced cart = cartLine(sp, outside);
            assertEquals(PARENT, cart.unitPrice(), "no live discount, no discount");
            assertNull(cart.originalUnitPrice(),
                    "and no struck-through price, or the page advertises a saving nobody is getting");
            assertEquals(advertised(sp, outside), cart.unitPrice());
            assertEquals(PARENT, variantLine(sp, outside).unitPrice(), "the same for a variant line");
        }
    }

    @Test
    void aLineIsRePricedWhenTheSaleStartsAndAgainWhenItEnds() {
        // The staleness the flash sale was built to remove. Before V60 a line's price was written once
        // and never looked at again.
        StoreProduct sp = listing(Product.DiscountType.PERCENTAGE, "25", STARTS, ENDS);

        BigDecimal before = cartLine(sp, BEFORE).unitPrice();
        BigDecimal during = cartLine(sp, DURING).unitPrice();
        BigDecimal after = cartLine(sp, AFTER).unitPrice();

        assertTrue(CartLinePricing.moved(before, during), "the sale starting is a price change");
        assertTrue(CartLinePricing.moved(during, after), "the sale ending is a price change");
        assertEquals(before, after, "and it ends up back where it started");
    }

    @Test
    void aDiscountWithNoDatesPricesIdenticallyAtEveryInstant() {
        // THE compatibility contract. Every discount written before V60 has two NULL dates, and they are
        // the rows already live in the shop: they must price exactly as they always did.
        StoreProduct percentage = listing(Product.DiscountType.PERCENTAGE, "25", null, null);
        StoreProduct fixed = listing(Product.DiscountType.FIXED, "750", null, null);

        for (Instant at : new Instant[]{BEFORE, DURING, AFTER}) {
            assertEquals(new BigDecimal("750.00"), cartLine(percentage, at).unitPrice());
            assertEquals(0, new BigDecimal("750").compareTo(cartLine(fixed, at).unitPrice()));
            assertEquals(PARENT, cartLine(percentage, at).originalUnitPrice());
        }
    }

    // ── The floor ─────────────────────────────────────────────────────────────

    @Test
    void aDiscountedPriceNeverReachesZeroOrGoesBelowIt() {
        // PERCENTAGE 100 is ACCEPTED by FlashSalePolicy (it refuses only above 100) and the arithmetic
        // turns it into 0.00. A zero-price line sails past the free-delivery threshold and the VAT
        // extraction in buildCartResponse and produces a free order; a cent is visibly wrong to whoever
        // set the discount instead of silently free. The floor lives in the static effectivePrice, so it
        // covers the simple PERCENTAGE arithmetic and not merely some special path.
        StoreProduct everything = listing(Product.DiscountType.PERCENTAGE, "100", STARTS, ENDS);

        assertEquals(new BigDecimal("0.01"), StoreProduct.MIN_UNIT_PRICE, "the floor is one cent");
        assertEquals(StoreProduct.MIN_UNIT_PRICE, advertised(everything, DURING),
                "the CARD must not advertise a free product either");
        assertEquals(StoreProduct.MIN_UNIT_PRICE, cartLine(everything, DURING).unitPrice());
        assertEquals(StoreProduct.MIN_UNIT_PRICE, variantLine(everything, DURING).unitPrice());
        assertTrue(cartLine(everything, DURING).unitPrice().signum() > 0);
    }

    @Test
    void aLegacyRowWhoseArithmeticGoesNegativeIsFlooredToo() {
        // FlashSalePolicy refuses a percentage above 100 and a FIXED value of zero or less on the way in,
        // so these are LEGACY rows only — which is exactly why the floor is in the pricing and not only
        // in the validation: the rows already exist.
        assertEquals(StoreProduct.MIN_UNIT_PRICE,
                cartLine(listing(Product.DiscountType.PERCENTAGE, "150", STARTS, ENDS), DURING).unitPrice());
        assertEquals(StoreProduct.MIN_UNIT_PRICE,
                cartLine(listing(Product.DiscountType.FIXED, "-5", STARTS, ENDS), DURING).unitPrice());
    }

    @Test
    void anUndiscountedListingPricedAtZeroStillAnswersZero() {
        // The floor applies to ARITHMETIC, not to a price somebody typed. A giveaway listing with no
        // discount is not a rounding accident and must not be silently moved to a cent.
        StoreProduct free = listing(null, null, null, null);
        free.setStorePrice(BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(cartLine(free, DURING).unitPrice()));
    }

    @Test
    void aDiscountThatRoundsAwayToNothingShowsNoStruckThroughPrice() {
        // 0.01% off a 1.00 accessory is 1.00. Charging 1.00 is right; drawing a line through a 1.00
        // sitting above a 1.00 is a rendering bug that reads as a fake discount.
        StoreProduct sliver = listing(Product.DiscountType.PERCENTAGE, "0.01", STARTS, ENDS);
        sliver.setStorePrice(new BigDecimal("1.00"));

        CartLinePricing.Priced cart = cartLine(sliver, DURING);
        assertEquals(new BigDecimal("1.00"), cart.unitPrice());
        assertNull(cart.originalUnitPrice());
    }
}
