package com.buyology.ecommerce.store.domain;

import com.buyology.ecommerce.product.domain.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_products", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "store_id", "product_id" })
}, indexes = {
        @Index(name = "idx_store_products_store_id", columnList = "store_id"),
        @Index(name = "idx_store_products_product_id", columnList = "product_id"),
        @Index(name = "idx_store_products_deleted_at", columnList = "deleted_at"),
        @Index(name = "idx_store_products_active", columnList = "store_id, is_active"),
        // "What is on the flash sale right now" — see V60. Partial in the migration; Hibernate can
        // only create the plain form on a fresh database, which answers the same query.
        @Index(name = "idx_store_products_flash_sale", columnList = "discount_ends_at")
})
public class StoreProduct {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Store-specific price — all pricing lives here, not on the global product
    @Column(name = "store_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal storePrice;

    // Reuses Product.DiscountType (FIXED / PERCENTAGE) — no duplication
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20)
    private Product.DiscountType discountType;

    @Column(name = "discount_value", precision = 12, scale = 2)
    private BigDecimal discountValue;

    // When the discount above is live. Both ends INCLUSIVE, and NULL is meaningful on each:
    // discountStartsAt NULL means "already started", discountEndsAt NULL means "never ends".
    // Every discount that existed before V60 has both NULL and therefore prices exactly as it
    // always did — that is the compatibility contract, and it is why neither column has a default.
    //
    // A flash sale is simply a discount with an END. Expiry is a property of this data, not of a
    // scheduled job: nothing has to run for a sale to stop discounting.
    @Column(name = "discount_starts_at")
    private Instant discountStartsAt;

    @Column(name = "discount_ends_at")
    private Instant discountEndsAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Channel flags — independent of isActive. b2c_enabled (default true) keeps the product in the
    // consumer shop as before; b2b_enabled (default false) opts it into the B2B quote catalog.
    @Column(name = "b2c_enabled", nullable = false)
    private Boolean b2cEnabled = true;

    @Column(name = "b2b_enabled", nullable = false)
    private Boolean b2bEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Constructors

    public StoreProduct() {
    }

    public StoreProduct(Store store, Product product, BigDecimal storePrice) {
        this.store = store;
        this.product = product;
        this.storePrice = storePrice;
    }

    // Lifecycle hooks

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) this.isActive = true;
        if (this.b2cEnabled == null) this.b2cEnabled = true;
        if (this.b2bEnabled == null) this.b2bEnabled = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getStorePrice() {
        return storePrice;
    }

    public void setStorePrice(BigDecimal storePrice) {
        this.storePrice = storePrice;
    }

    public Product.DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(Product.DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public Instant getDiscountStartsAt() {
        return discountStartsAt;
    }

    public void setDiscountStartsAt(Instant discountStartsAt) {
        this.discountStartsAt = discountStartsAt;
    }

    public Instant getDiscountEndsAt() {
        return discountEndsAt;
    }

    public void setDiscountEndsAt(Instant discountEndsAt) {
        this.discountEndsAt = discountEndsAt;
    }

    /**
     * The price the customer actually pays after any store-level discount, as of {@code now}.
     *
     * <p>FIXED → {@code discountValue} is the final price; PERCENTAGE → percent off
     * {@code storePrice}. No discount, or a discount whose window does not contain the moment
     * asked about → {@code storePrice}. Single source of truth for discount math (used by the
     * product API, cart, checkout and store-admin view).
     *
     * <p>There is deliberately NO no-argument overload. One existed until this change with zero
     * callers left in {@code src/main}, and it read the clock itself — so the next caller to reach
     * for the obvious-looking name would have had two lines of one cart judged against two
     * different instants, straddling the second a sale ends. One {@code now} per request is not a
     * convention here, it is the only reason a response cannot disagree with itself; making the
     * instant a mandatory parameter is what enforces it.
     */
    public BigDecimal effectivePrice(Instant now) {
        return effectivePrice(storePrice, discountType, discountValue, discountStartsAt, discountEndsAt, now);
    }

    /**
     * The lowest unit price any discount may produce, enforced in the one place a discounted unit
     * price is computed — the static {@link #effectivePrice} below, which every surface in the shop
     * prices through.
     *
     * <p>The reachable case is a PERCENTAGE of exactly 100, which {@code FlashSalePolicy} allows
     * (it refuses only ABOVE 100) and which the arithmetic turns into 0.00. Legacy rows can also
     * hold a PERCENTAGE above 100 or a FIXED value of zero or less, both of which give a negative
     * or free line. A zero-price line sails straight past the free-delivery threshold and the VAT
     * extraction in {@code CartService.buildCartResponse} and produces a free order; a cent is
     * visibly wrong to whoever set the discount instead of silently free.
     *
     * <p>Only a DISCOUNTED price is floored. A listing whose {@code storePrice} is itself zero and
     * carries no discount still answers zero — that is the price somebody typed, not arithmetic
     * that ran away.
     */
    public static final BigDecimal MIN_UNIT_PRICE = new BigDecimal("0.01");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Static variant for callers that only have the raw price + discount columns (e.g. projection
     * query rows) and not a managed entity.
     *
     * <p>There is deliberately NO overload without the two dates. One existed until V60 and three
     * projection call sites in ProductService compiled against it; leaving it in place would have
     * let every one of them keep the old, unwindowed behaviour in silence — product lists and
     * search advertising a sale price for weeks after the sale ended, while the cart charged full
     * price. Making the dates mandatory parameters turns that into a compile error instead.
     */
    public static BigDecimal effectivePrice(BigDecimal storePrice,
                                            Product.DiscountType discountType,
                                            BigDecimal discountValue,
                                            Instant discountStartsAt,
                                            Instant discountEndsAt,
                                            Instant now) {
        if (storePrice == null || discountType == null || discountValue == null) {
            return storePrice;
        }
        if (!discountWindowContains(discountStartsAt, discountEndsAt, now)) {
            return storePrice;
        }
        BigDecimal unit = switch (discountType) {
            case FIXED -> discountValue;
            case PERCENTAGE -> storePrice
                    .multiply(BigDecimal.ONE.subtract(discountValue.divide(HUNDRED)))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        };
        // The floor belongs HERE and not in each caller: this is the only place a discounted unit
        // price is produced, so it is the only place that can guarantee no surface in the shop ever
        // quotes a free or negative one. See MIN_UNIT_PRICE for which inputs reach it.
        return unit.compareTo(MIN_UNIT_PRICE) < 0 ? MIN_UNIT_PRICE : unit;
    }

    // There is deliberately NO effectiveVariantPrice. One existed briefly: it took a variant's own
    // store_price and applied the parent listing's discount to it, so that a variant line could be
    // charged from the variant row. It is gone, and so is every caller, because a variantId must not
    // decide a price.
    //
    // The reason is the one invariant this whole area rests on: there is exactly ONE number a
    // customer is shown for a product, and it comes from the parent listing row — store_products.
    // Every charge path has to agree with THAT number. store_product_variants.store_price is not
    // quoted by any card, rail, search result or product page, so pricing a cart line from it is a
    // second notion of "the price" and guarantees a divergence in one direction or the other. The
    // web storefront does not even send a variantId, so the same basket priced from two clients
    // would have disagreed with itself.
    //
    // A variantId therefore identifies the line and caps its stock (StoreProductVariant.getStock);
    // the money comes from effectivePrice above, parent discount and parent window included.
    // Showing and charging a genuine per-variant price is the correct e-commerce model and is a
    // deliberately deferred project: it needs the catalogue to quote the variant's price everywhere
    // first, which is a backend change plus both clients, not a pricing helper.

    /**
     * Whether a discount window is open at {@code now}. NULL start = already started,
     * NULL end = never ends, so two NULLs are always open — the pre-V60 behaviour.
     *
     * <p>Both boundaries are inclusive, matching PromoCodeService (a code is expired only once
     * {@code expiresAt} is strictly before now). The admin API converts a calendar end date into
     * the end of that day in Asia/Dubai before it gets here, so the single instant of overrun an
     * inclusive end allows is midnight itself, never a whole day.
     */
    public static boolean discountWindowContains(Instant discountStartsAt, Instant discountEndsAt, Instant now) {
        Instant at = now != null ? now : Instant.now();
        if (discountStartsAt != null && at.isBefore(discountStartsAt)) return false;
        if (discountEndsAt != null && discountEndsAt.isBefore(at)) return false;
        return true;
    }

    /**
     * True when a discount is configured, inside its window at {@code now}, and actually lowers the
     * price. Outside the window this is false, so the struck-through "was" price stops rendering the
     * moment the sale ends — a discount nobody is getting must not leave a fake saving on the page.
     *
     * <p>No no-argument overload, for the reason given on {@link #effectivePrice(Instant)}.
     */
    public boolean hasDiscount(Instant now) {
        BigDecimal eff = effectivePrice(now);
        return eff != null && storePrice != null && eff.compareTo(storePrice) < 0;
    }

    /**
     * Whether this listing is on the FLASH SALE — a live discount that HAS an end in the future.
     *
     * <p>Deliberately not the same question as {@link #hasDiscount(Instant)}: a permanent markdown
     * (a discount with no end date) is also discounted, and rendering it in a rail with a countdown
     * would promise an urgency that does not exist. A flash sale is a discount that runs out.
     */
    public boolean onFlashSale(Instant now) {
        return discountEndsAt != null && hasDiscount(now);
    }

    /** When the flash sale ends, or null when this listing is not on one — the countdown value. */
    public Instant flashSaleEndsAt(Instant now) {
        return onFlashSale(now) ? discountEndsAt : null;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getB2cEnabled() {
        return b2cEnabled;
    }

    public void setB2cEnabled(Boolean b2cEnabled) {
        this.b2cEnabled = b2cEnabled;
    }

    public Boolean getB2bEnabled() {
        return b2bEnabled;
    }

    public void setB2bEnabled(Boolean b2bEnabled) {
        this.b2bEnabled = b2bEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
