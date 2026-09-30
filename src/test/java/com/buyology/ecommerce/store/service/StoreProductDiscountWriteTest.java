package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.dto.AssignProductRequest;
import com.buyology.ecommerce.store.dto.AssignVariantRequest;
import com.buyology.ecommerce.store.dto.UpdateStoreProductRequest;
import com.buyology.ecommerce.store.dto.UpdateStoreVariantRequest;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins the discount rules on the ORDINARY store-product endpoints — the ones the existing dashboard
 * screens use.
 *
 * <p>That is the point of this class. The flash-sale endpoint validates carefully; these two write the
 * same four columns, gained the same two dates, and validated almost nothing. Every case below is a
 * price a customer would have been charged through a screen that already ships.
 */
class StoreProductDiscountWriteTest {

    private static final UUID STORE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LISTING = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VARIANT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    /** The store's own row for that variant — store_product_variants.id, not the catalogue variant id. */
    private static final UUID STORE_VARIANT = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final Instant PAST = Instant.now().minus(30, ChronoUnit.DAYS);
    private static final Instant SOON = Instant.now().plus(7, ChronoUnit.DAYS);
    private static final Instant LATER = Instant.now().plus(30, ChronoUnit.DAYS);

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final ProductTranslationRepository translationRepository = mock(ProductTranslationRepository.class);
    private final StoreProductRepository storeProductRepository = mock(StoreProductRepository.class);
    private final StoreProductVariantRepository storeProductVariantRepository =
            mock(StoreProductVariantRepository.class);

    private final StoreProductService service = new StoreProductService(
            storeRepository, productRepository, variantRepository, translationRepository,
            storeProductRepository, storeProductVariantRepository);

    private static Store store() {
        Store store = new Store();
        store.setId(STORE);
        return store;
    }

    private static Product product() {
        Product product = new Product();
        product.setId(PRODUCT);
        product.setSku("MBP-14-M4");
        product.setStatus("ACTIVE");
        return product;
    }

    /** A listing at 2000 with whatever discount the test needs. */
    private StoreProduct listing() {
        StoreProduct sp = new StoreProduct(store(), product(), new BigDecimal("2000.00"));
        sp.setId(LISTING);
        when(storeProductRepository.findById(LISTING)).thenReturn(Optional.of(sp));
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(inv -> inv.getArgument(0));
        return sp;
    }

    private void listingHasAnActiveVariant(StoreProduct sp) {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT);
        variant.setProduct(sp.getProduct());
        variant.setSku("MBP-14-M4-1TB");

        StoreProductVariant spv = new StoreProductVariant();
        spv.setStoreProduct(sp);
        spv.setVariant(variant);
        spv.setStorePrice(new BigDecimal("2400.00"));
        spv.setIsActive(true);
        when(storeProductVariantRepository.findByStoreProduct_Id(LISTING)).thenReturn(List.of(spv));
    }

    // ── A price rise wearing a sale badge ────────────────────────────────────

    @Test
    void droppingTheStorePriceBelowAFixedDiscountIsRefusedAsAPriceRise() {
        // PATCH {"storePrice": 800} on a listing carrying FIXED 999. effectivePrice returns the
        // discountValue unconditionally, so the customer is CHARGED 999 for an 800 item — and
        // hasDiscount() is false, so no surface renders a struck-through price and none says
        // onFlashSale. Invisible everywhere except the receipt. Since cart lines are now re-priced on
        // read, it also walks live baskets UP to 999.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.FIXED);
        sp.setDiscountValue(new BigDecimal("999.00"));

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setStorePrice(new BigDecimal("800.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateStoreProduct(STORE, LISTING, request));
        assertTrue(ex.getMessage().contains("price rise"), ex.getMessage());
        assertEquals(new BigDecimal("2000.00"), sp.getStorePrice(),
                "the refusal must leave the listing exactly as it was");
    }

    @Test
    void aPriceChangeThatKeepsTheDiscountBelowItIsStillAllowed() {
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.FIXED);
        sp.setDiscountValue(new BigDecimal("999.00"));

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setStorePrice(new BigDecimal("1500.00"));

        service.updateStoreProduct(STORE, LISTING, request);

        assertEquals(new BigDecimal("1500.00"), sp.getStorePrice());
        assertEquals(new BigDecimal("999.00"), sp.getDiscountValue());
    }

    @Test
    void anEditThatTouchesNeitherPriceNorDiscountIsNotJudgedOnTheOldNumbers() {
        // A listing whose numbers were already stored badly, before any of this validated. Refusing an
        // unrelated flag edit would leave an admin unable to deactivate the very row they need to.
        StoreProduct sp = listing();
        sp.setStorePrice(new BigDecimal("800.00"));
        sp.setDiscountType(Product.DiscountType.FIXED);
        sp.setDiscountValue(new BigDecimal("999.00"));

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setB2bEnabled(true);

        assertDoesNotThrow(() -> service.updateStoreProduct(STORE, LISTING, request));
        assertTrue(sp.getB2bEnabled());
    }

    // ── A date-only PATCH is still a discount being scheduled ────────────────

    @Test
    void aDateOnlyPatchValidatesTheDiscountItIsAboutToSchedule() {
        // The hole: validateDiscount only ran when the request carried a price or a discount VALUE. A
        // PATCH of nothing but {"discountEndsAt": ...} wrote the window and never looked at what it was
        // scheduling — so a legacy row holding storePrice=1000 with a FIXED discountValue=1200 charged
        // 1200 inside the window, on the card and in the cart, while looking like a sale to nobody:
        // hasDiscount() is false at 1200, so there is no strike-through, no badge, and onlyOnFlashSale
        // filters the row off the rail. Putting a window on a discount is what makes it price something,
        // so it is exactly when the value has to be checked.
        StoreProduct sp = listing();
        sp.setStorePrice(new BigDecimal("1000.00"));
        sp.setDiscountType(Product.DiscountType.FIXED);
        sp.setDiscountValue(new BigDecimal("1200.00"));

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountEndsAt(LATER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateStoreProduct(STORE, LISTING, request));
        assertTrue(ex.getMessage().contains("price rise"), ex.getMessage());
        assertNull(sp.getDiscountEndsAt(), "and the window must not have been written");
    }

    @Test
    void aDateOnlyPatchOnASoundDiscountIsStillJustAWindowEdit() {
        // The rule must not cost an admin the ordinary edit it exists inside: moving the end of a
        // perfectly good sale.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.FIXED);
        sp.setDiscountValue(new BigDecimal("1500.00"));
        sp.setDiscountEndsAt(SOON);

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountEndsAt(LATER);

        service.updateStoreProduct(STORE, LISTING, request);

        assertEquals(LATER, sp.getDiscountEndsAt());
        assertEquals(new BigDecimal("1500.00"), sp.effectivePrice(Instant.now()));
    }

    @Test
    void clearingTheWINDOWOfAnAboveListDiscountIsRefusedBecauseItWouldMakeItPermanent() {
        // Taking the dates off an above-list discount does not make it harmless — it makes it forever.
        // clearDiscount is the way off a bad sale, and it is unaffected: it removes the value and the
        // dates together, leaving nothing to price.
        StoreProduct sp = listing();
        sp.setStorePrice(new BigDecimal("1000.00"));
        sp.setDiscountType(Product.DiscountType.FIXED);
        sp.setDiscountValue(new BigDecimal("1200.00"));
        sp.setDiscountEndsAt(SOON);

        UpdateStoreProductRequest windowOnly = new UpdateStoreProductRequest();
        windowOnly.setClearDiscountWindow(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStoreProduct(STORE, LISTING, windowOnly));

        UpdateStoreProductRequest off = new UpdateStoreProductRequest();
        off.setClearDiscount(true);
        service.updateStoreProduct(STORE, LISTING, off);

        assertNull(sp.getDiscountValue());
        assertNull(sp.getDiscountEndsAt());
        assertEquals(new BigDecimal("1000.00"), sp.effectivePrice(Instant.now()));
    }

    // ── A re-set discount must not inherit a dead window ─────────────────────

    @Test
    void settingADiscountOnAListingWithAnExpiredWindowClearsTheDeadWindow() {
        // Without this the save produces a discount that is already over: effectivePrice ignores it,
        // the dashboard badge (built from discountValue) claims a sale, and the flash-sale screen
        // cannot even show the row because it excludes ended sales. Nothing visible explains why the
        // price never moved.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("10"));
        sp.setDiscountStartsAt(PAST.minus(7, ChronoUnit.DAYS));
        sp.setDiscountEndsAt(PAST);

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountType(Product.DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("25"));

        service.updateStoreProduct(STORE, LISTING, request);

        assertNull(sp.getDiscountEndsAt(), "a discount sent with no dates must be live, not expired");
        assertNull(sp.getDiscountStartsAt());
        assertEquals(new BigDecimal("25"), sp.getDiscountValue());
        assertEquals(new BigDecimal("1500.00"), sp.effectivePrice(Instant.now()),
                "and it must actually discount");
    }

    @Test
    void settingADiscountOnAListingWithAScheduledWindowKeepsThatWindow() {
        // The other half of the rule. A window still to come is a sale the admin scheduled and may be
        // amending the value of; dropping it would start that sale early.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("10"));
        sp.setDiscountStartsAt(SOON);
        sp.setDiscountEndsAt(LATER);

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountType(Product.DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("25"));

        service.updateStoreProduct(STORE, LISTING, request);

        assertEquals(SOON, sp.getDiscountStartsAt());
        assertEquals(LATER, sp.getDiscountEndsAt());
        assertEquals(new BigDecimal("2000.00"), sp.effectivePrice(Instant.now()),
                "a scheduled sale must not price anything yet");
    }

    @Test
    void anExpiredWindowSurvivesAnEditThatDoesNotTouchTheDiscount() {
        // Clearing it here would silently revive a sale that ended: the discount value is still on the
        // row, so dropping its dead end date would make it live again at the old price.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("10"));
        sp.setDiscountEndsAt(PAST);

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setIsActive(true);

        service.updateStoreProduct(STORE, LISTING, request);

        assertEquals(PAST, sp.getDiscountEndsAt());
        assertEquals(new BigDecimal("2000.00"), sp.effectivePrice(Instant.now()));
    }

    // ── A window with nothing to apply to ────────────────────────────────────

    @Test
    void datesWithoutADiscountAreRefusedRatherThanStoredInertly()  {
        StoreProduct sp = listing();

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountEndsAt(LATER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateStoreProduct(STORE, LISTING, request));
        assertTrue(ex.getMessage().contains("window needs a discount"), ex.getMessage());
        assertNull(sp.getDiscountEndsAt());
    }

    @Test
    void assigningAProductWithDatesButNoDiscountIsRefusedToo() {
        when(storeRepository.findById(STORE)).thenReturn(Optional.of(store()));
        when(productRepository.findById(PRODUCT)).thenReturn(Optional.of(product()));
        when(storeProductRepository.findByStore_IdAndProduct_Id(STORE, PRODUCT)).thenReturn(Optional.empty());

        AssignProductRequest request = new AssignProductRequest();
        request.setProductId(PRODUCT);
        request.setStorePrice(new BigDecimal("2000.00"));
        request.setDiscountEndsAt(LATER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.assignProduct(STORE, request));
        assertTrue(ex.getMessage().contains("window needs a discount"), ex.getMessage());
        verify(storeProductRepository, never()).save(any(StoreProduct.class));
    }

    // ── Variant-priced listings, from both directions ────────────────────────
    //
    // Both of these were REFUSALS, and both are now allowed. They existed for one reason: a variant cart
    // line took its price from store_product_variants.store_price and never consulted its parent's
    // discount, so the card, the rail and the store option advertised a sale that the cart did not
    // charge. Every line is now priced from the parent listing — a variantId picks the SKU and the stock
    // ceiling, never the price — so the state they refused cannot be reached and the refusals were only
    // stopping admins from running a legitimate campaign on a large part of the catalogue.
    // PriceAgreementTest is where the honesty is now pinned.

    @Test
    void aTimedDiscountIsNowAllowedOnAListingPricedPerVariant() {
        StoreProduct sp = listing();
        listingHasAnActiveVariant(sp);

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountType(Product.DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("25"));
        request.setDiscountEndsAt(LATER);

        assertDoesNotThrow(() -> service.updateStoreProduct(STORE, LISTING, request));

        assertEquals(LATER, sp.getDiscountEndsAt(), "the window must actually be stored");
        assertEquals(new BigDecimal("25"), sp.getDiscountValue());
    }

    @Test
    void anUnWindowedDiscountOnAVariantListingIsStillAllowed() {
        // Unchanged, and kept as the control: it was always allowed, and it is the state live rows are
        // already in. It now also actually reaches the variant's price.
        StoreProduct sp = listing();
        listingHasAnActiveVariant(sp);

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountType(Product.DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("25"));

        assertDoesNotThrow(() -> service.updateStoreProduct(STORE, LISTING, request));
        assertEquals(new BigDecimal("25"), sp.getDiscountValue());
    }

    @Test
    void addingAnActiveVariantToAListingOnATimedDiscountIsNowAllowed() {
        // The walk-around the refusal above was paired with (put a variant-free listing on sale, then add
        // the variant). With nothing left to refuse there is nothing left to walk around.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("25"));
        sp.setDiscountEndsAt(LATER);

        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT);
        variant.setProduct(product());
        variant.setSku("MBP-14-M4-1TB");
        when(variantRepository.findById(VARIANT)).thenReturn(Optional.of(variant));
        when(storeProductVariantRepository.findByStoreProduct_IdAndVariant_Id(LISTING, VARIANT))
                .thenReturn(Optional.empty());
        when(storeProductVariantRepository.save(any(StoreProductVariant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AssignVariantRequest request = new AssignVariantRequest();
        request.setVariantId(VARIANT);
        request.setStorePrice(new BigDecimal("2400.00"));

        var response = service.assignVariant(STORE, LISTING, request);

        assertEquals(new BigDecimal("2400.00"), response.getBody().getData().getStorePrice(),
                "the per-variant figure is recorded as typed");
        // And next to it, what a line of this variant will actually SELL for: 25% off the LISTING's 2000,
        // not off the 2400 that was just typed. The two are shown together deliberately — the variant
        // price is recorded and never billed, and an admin who cannot see that will keep setting it and
        // wondering why it does nothing.
        assertEquals(new BigDecimal("1500.00"), response.getBody().getData().getEffectivePrice());
    }

    @Test
    void addingAVariantToAListingWithAPermanentMarkdownIsNotBlocked() {
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("25"));

        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT);
        variant.setProduct(product());
        variant.setSku("MBP-14-M4-1TB");
        when(variantRepository.findById(VARIANT)).thenReturn(Optional.of(variant));
        when(storeProductVariantRepository.findByStoreProduct_IdAndVariant_Id(LISTING, VARIANT))
                .thenReturn(Optional.empty());
        when(storeProductVariantRepository.save(any(StoreProductVariant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AssignVariantRequest request = new AssignVariantRequest();
        request.setVariantId(VARIANT);
        request.setStorePrice(new BigDecimal("2400.00"));

        assertDoesNotThrow(() -> service.assignVariant(STORE, LISTING, request));
    }

    // ── The window rules the flash sale already had, now on this path too ────

    @Test
    void anEndAlreadyInThePastIsRefusedHereAsWell() {
        StoreProduct sp = listing();

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setDiscountType(Product.DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("25"));
        request.setDiscountEndsAt(PAST);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateStoreProduct(STORE, LISTING, request));
        assertTrue(ex.getMessage().contains("already in the past"), ex.getMessage());
    }

    @Test
    void clearDiscountRemovesTheWindowAsWellAndStillAcceptsANewPrice() {
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.FIXED);
        sp.setDiscountValue(new BigDecimal("999.00"));
        sp.setDiscountEndsAt(LATER);

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setClearDiscount(true);
        // Below the old discount value on purpose: with the discount gone this is a legitimate price.
        request.setStorePrice(new BigDecimal("800.00"));

        service.updateStoreProduct(STORE, LISTING, request);

        assertNull(sp.getDiscountType());
        assertNull(sp.getDiscountValue());
        assertNull(sp.getDiscountStartsAt());
        assertNull(sp.getDiscountEndsAt());
        assertEquals(new BigDecimal("800.00"), sp.getStorePrice());
    }

    // ── The third way in: REACTIVATING a variant ─────────────────────────────

    /**
     * A variant row on {@code sp}, switched off, findable by id — the state the walk-around leaves
     * behind after {@code removeVariant}.
     */
    private StoreProductVariant listingHasAnInactiveVariant(StoreProduct sp) {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT);
        variant.setProduct(sp.getProduct());
        variant.setSku("MBP-14-M4-1TB");

        StoreProductVariant spv = new StoreProductVariant();
        spv.setId(STORE_VARIANT);
        spv.setStoreProduct(sp);
        spv.setVariant(variant);
        spv.setStorePrice(new BigDecimal("2400.00"));
        spv.setStock(5);
        spv.setIsActive(false);
        when(storeProductVariantRepository.findById(STORE_VARIANT)).thenReturn(Optional.of(spv));
        when(storeProductVariantRepository.save(any(StoreProductVariant.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return spv;
    }

    @Test
    void reactivatingAVariantOnAListingOnATimedDiscountIsNowAllowed() {
        // This was a refusal, and it closed the third walk-around into the mispricing:
        //   1. DELETE the listing's only variant        -> isActive = false
        //   2. PATCH the listing onto a timed discount  -> activeVariantCount is 0, so it passed
        //   3. PATCH the variant back on, here          -> the state neither endpoint would create
        // The state it was protecting is no longer a mispricing. A reactivated variant is priced from its
        // own store_price with the listing's discount applied, like every other variant line, so all
        // three steps now end somewhere honest and there is nothing left to walk around.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("25"));
        sp.setDiscountEndsAt(LATER);
        StoreProductVariant spv = listingHasAnInactiveVariant(sp);

        UpdateStoreVariantRequest request = new UpdateStoreVariantRequest();
        request.setIsActive(true);

        assertDoesNotThrow(() -> service.updateStoreVariant(STORE, LISTING, STORE_VARIANT, request));

        assertTrue(spv.getIsActive(), "the variant must actually come back on");
        verify(storeProductVariantRepository, times(1)).save(spv);
    }

    @Test
    void reactivatingAVariantOnAListingWithAPermanentMarkdownIsStillAllowed() {
        // Unchanged, and kept as the control: it was allowed before because an un-windowed markdown on a
        // variant-bearing listing is a state live rows are already in. It is allowed now for the stronger
        // reason that the markdown actually reaches the variant.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("25"));
        StoreProductVariant spv = listingHasAnInactiveVariant(sp);

        UpdateStoreVariantRequest request = new UpdateStoreVariantRequest();
        request.setIsActive(true);

        service.updateStoreVariant(STORE, LISTING, STORE_VARIANT, request);

        assertTrue(spv.getIsActive());
    }

    @Test
    void aStockEditOnAnAlreadyActiveVariantOfADiscountedListingIsNotBlocked() {
        // Nothing about this edit creates the mispricing — the variant was already active — so it must
        // not 400. A legacy-bad row still has to be administrable, and an idempotent isActive=true
        // must stay a no-op rather than a refusal.
        StoreProduct sp = listing();
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("25"));
        sp.setDiscountEndsAt(LATER);
        StoreProductVariant spv = listingHasAnInactiveVariant(sp);
        spv.setIsActive(true);

        UpdateStoreVariantRequest request = new UpdateStoreVariantRequest();
        request.setStock(12);
        request.setIsActive(true);

        service.updateStoreVariant(STORE, LISTING, STORE_VARIANT, request);

        assertEquals(12, spv.getStock());
        assertTrue(spv.getIsActive());
    }
}
