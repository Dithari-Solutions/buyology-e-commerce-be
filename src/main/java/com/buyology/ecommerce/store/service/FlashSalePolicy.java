package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.utils.BusinessZone;
import com.buyology.ecommerce.product.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The rules a discount window has to satisfy before it is allowed to price anything.
 *
 * <p>Every method here REFUSES. That is deliberate and it is the opposite posture to the customer
 * paths in this codebase (the cancellation questionnaire, for instance, never rejects, because a
 * refusal there costs the customer their cancellation). This is an admin path: the cost of failing
 * loudly is that one dashboard save comes back with a message, and the cost of failing quietly is
 * that every order for the next fortnight is priced wrong. A "sale" that raises the price, or one
 * that expired before it was saved, is not a request to interpret — it is a typo to hand back.
 *
 * <p>Timezone lives here too, at the API boundary, and nowhere else. The business is in the UAE, so
 * an admin who types "ends 31 March" means the end of that day in Dubai; the entity and the database
 * only ever see instants. Converting anywhere deeper would mean two places knowing about Dubai.
 */
public final class FlashSalePolicy {

    /**
     * The platform's business zone, from the one constant the whole codebase shares. It used to be a
     * fourth private copy of the literal, which is how two of them end up disagreeing about when a
     * day ends.
     */
    public static final ZoneId BUSINESS_ZONE = BusinessZone.ID;

    private FlashSalePolicy() {
    }

    /**
     * "ends 31 March" → the instant that day finishes in Dubai, i.e. 2026-04-01T00:00+04:00.
     *
     * <p>Paired with the INCLUSIVE end in {@link com.buyology.ecommerce.store.domain.StoreProduct},
     * the sale therefore covers the whole of 31 March in Dubai plus the single instant of midnight.
     * That one instant is a deliberate choice, not an accident: the alternative (subtracting a
     * millisecond) makes the stored value unreadable in a dashboard and buys nothing a customer
     * could ever notice.
     */
    public static Instant endOfDayInBusinessZone(LocalDate day) {
        return day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    /** "starts 25 March" → the first instant of that day in Dubai. */
    public static Instant startOfDayInBusinessZone(LocalDate day) {
        return day.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    /**
     * The discount itself: a value that lowers the price, and only in the ways each type allows.
     *
     * @param storePrice the price the discount will be applied to — a sanity check for FIXED, which
     *                   stores an absolute sale price rather than a rate: a "sale" at or above the store
     *                   price is a price rise, and one at or below zero is a free order. Optional,
     *                   because the PATCH path can legitimately validate a discount without touching
     *                   the price.
     */
    public static void validateDiscount(Product.DiscountType discountType, BigDecimal discountValue,
                                        BigDecimal storePrice) {
        if (discountType == null && discountValue == null) {
            throw new IllegalArgumentException("discountType and discountValue are required to put a product on the flash sale");
        }
        if (discountType != null && discountValue == null) {
            throw new IllegalArgumentException("discountValue is required when discountType is set");
        }
        if (discountValue != null && discountType == null) {
            throw new IllegalArgumentException("discountType is required when discountValue is set");
        }
        if (discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("discountValue must be greater than zero");
        }
        if (discountType == Product.DiscountType.PERCENTAGE
                && discountValue.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage discount must be between 0 and 100");
        }
        if (discountType == Product.DiscountType.FIXED && storePrice != null
                && discountValue.compareTo(storePrice) >= 0) {
            throw new IllegalArgumentException(
                    "Fixed discount value must be lower than storePrice (" + storePrice
                            + ") — a discounted price at or above the store price is a price rise, not a sale");
        }
        // A listing with no positive price to discount FROM is a half-filled row, not a campaign: the
        // sale price would be the only price, and whoever typed it has not said what it is a sale on.
        // 400 on the typo rather than a listing whose "discount" is its whole price.
        if (discountType == Product.DiscountType.FIXED
                && (storePrice == null || storePrice.signum() <= 0)) {
            throw new IllegalArgumentException(
                    "A FIXED discount needs a storePrice above zero to discount from — set the store "
                            + "price first, or use a PERCENTAGE discount");
        }
    }

    /**
     * The window: an end that is after its start, and after now.
     *
     * <p>An end already in the past is refused rather than stored, even though the pricing code
     * would handle it perfectly well (it would simply never discount). Storing it would leave an
     * admin looking at a dashboard row that says the product is on the flash sale while every
     * customer pays full price, which is a worse outcome than a 400.
     */
    public static void validateWindow(Instant startsAt, Instant endsAt, Instant now) {
        if (endsAt == null) {
            // A discount with no end is legitimate — it is an ordinary permanent markdown, and every
            // discount written before V60 is one. It is just not a FLASH sale, so the flash-sale API
            // insists on an end and the plain store-product API does not.
            return;
        }
        if (startsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("discountEndsAt must be after discountStartsAt");
        }
        if (endsAt.isBefore(now)) {
            throw new IllegalArgumentException("discountEndsAt is already in the past — the sale would never run");
        }
    }

    /**
     * A window is only meaningful on a discount. Dates stored without one are refused.
     *
     * <p>{@code effectivePrice} never consults the window unless a type and a value are both set, so
     * a lone window is inert — which is precisely why it must not be storable. It shows up on the
     * admin screen as a product with an end date and no sale, survives every later edit, and is
     * exactly the state a re-set discount would then inherit an expiry from.
     */
    public static void requireDiscountForWindow(Product.DiscountType discountType, BigDecimal discountValue,
                                                Instant startsAt, Instant endsAt) {
        if ((startsAt != null || endsAt != null) && (discountType == null || discountValue == null)) {
            throw new IllegalArgumentException(
                    "A discount window needs a discount: send discountType and discountValue with the dates, "
                            + "or clear the window");
        }
    }

    /**
     * Whether a window has run out as of {@code now} — i.e. it can never apply again.
     *
     * <p>Strictly before, matching {@link com.buyology.ecommerce.store.domain.StoreProduct} and
     * PromoCodeService: the end instant itself is still inside the window, so a sale ending exactly
     * now has not ended. One definition of "over", in one place, or the admin screen and the pricing
     * code disagree by an instant about whether a sale is still running.
     */
    public static boolean windowHasEnded(Instant endsAt, Instant now) {
        return endsAt != null && endsAt.isBefore(now);
    }

    /** The flash sale needs an end date; without one it is a permanent markdown by another name. */
    public static void requireEnd(Instant endsAt) {
        if (endsAt == null) {
            throw new IllegalArgumentException(
                    "An end date is required — send endsOn (a date in Asia/Dubai) or endsAt (an instant). "
                            + "A discount with no end is a permanent markdown, not a flash sale; set it through "
                            + "the store product endpoint instead");
        }
    }

    // The two variant refusals that used to live here — validateNoActiveVariants and
    // validateNoTimedDiscount — are GONE. They existed for exactly one reason, stated in their own
    // comments: a variant cart line was charged store_product_variants.store_price undiscounted while
    // the card advertised the parent listing's sale price, so the shop advertised a sale it did not
    // honour.
    //
    // That is no longer possible. Every cart line, variant-bearing or not, is priced from the parent
    // listing through StoreProduct.effectivePrice — a variantId picks the SKU and the stock ceiling,
    // never the price (the reasoning is on CartLinePricing). So the state they refused cannot be
    // reached, and a refusal whose stated reason has stopped being true is worse than no refusal:
    // the next person reads it as documentation of how pricing works. They also cost something real —
    // between them they made a flash sale on ANY variant-bearing product flatly impossible, which is a
    // large part of the catalogue.
    //
    // Nothing replaces them, because there is nothing left to guard: the only price a discount can move
    // is the one on the listing row, which is also the only price any customer surface quotes.
}
