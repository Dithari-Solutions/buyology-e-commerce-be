package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.dto.AssignProductRequest;
import com.buyology.ecommerce.store.dto.AssignVariantRequest;
import com.buyology.ecommerce.store.dto.StoreProductResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreProductRequest;
import com.buyology.ecommerce.store.dto.UpdateStoreVariantRequest;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StoreProductService {

    private static final Logger log = LoggerFactory.getLogger(StoreProductService.class);

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductTranslationRepository translationRepository;
    private final StoreProductRepository storeProductRepository;
    private final StoreProductVariantRepository storeProductVariantRepository;

    public StoreProductService(
            StoreRepository storeRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductTranslationRepository translationRepository,
            StoreProductRepository storeProductRepository,
            StoreProductVariantRepository storeProductVariantRepository) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.translationRepository = translationRepository;
        this.storeProductRepository = storeProductRepository;
        this.storeProductVariantRepository = storeProductVariantRepository;
    }

    // ── Assign product to store ───────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<StoreProductResponse>> assignProduct(UUID storeId, AssignProductRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));

        if ("DELETED".equals(product.getStatus())) {
            throw new IllegalArgumentException("Cannot assign a deleted product");
        }

        validateDiscount(request.getDiscountType(), request.getDiscountValue(), request.getStorePrice());
        FlashSalePolicy.requireDiscountForWindow(request.getDiscountType(), request.getDiscountValue(),
                request.getDiscountStartsAt(), request.getDiscountEndsAt());
        FlashSalePolicy.validateWindow(request.getDiscountStartsAt(), request.getDiscountEndsAt(), Instant.now());

        // Resolved WITHOUT the isActive filter, because removing an assignment soft-deletes the row
        // rather than deleting it. Asking only for active rows answered "not assigned" while the row was
        // still in the table, so this method went on to INSERT and the unconditional
        // UNIQUE (store_id, product_id) constraint refused it — surfacing to the admin as "A record with
        // the same unique value already exists". A product could be removed from a store exactly once
        // and then never added back.
        //
        // The row is also soft-deleted rather than deleted for a reason: b2b_quote_items.store_product_id
        // points at it (a plain UUID with no FK), so destroying it would silently orphan quote lines.
        // Reviving the same row keeps those pointers valid, which is why this revives rather than
        // relaxing the constraint and inserting a second row.
        StoreProduct existing = storeProductRepository
                .findByStore_IdAndProduct_Id(storeId, product.getId())
                .orElse(null);

        if (existing != null) {
            if (existing.getDeletedAt() == null) {
                throw new IllegalArgumentException("Product is already assigned to this store");
            }
            return ApiResponse.created(
                    toResponse(reviveAssignment(existing, request)),
                    "Product assigned to store successfully");
        }

        StoreProduct storeProduct = new StoreProduct(store, product, request.getStorePrice());
        storeProduct.setDiscountType(request.getDiscountType());
        storeProduct.setDiscountValue(request.getDiscountValue());
        storeProduct.setDiscountStartsAt(request.getDiscountStartsAt());
        storeProduct.setDiscountEndsAt(request.getDiscountEndsAt());
        storeProduct.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        storeProduct.setB2cEnabled(request.getB2cEnabled() != null ? request.getB2cEnabled() : true);
        storeProduct.setB2bEnabled(request.getB2bEnabled() != null ? request.getB2bEnabled() : false);
        StoreProduct saved = storeProductRepository.save(storeProduct);

        // Assign variants if provided inline
        if (request.getVariants() != null) {
            for (AssignVariantRequest varReq : request.getVariants()) {
                assignVariantToStoreProduct(saved, varReq);
            }
        }

        return ApiResponse.created(toResponse(saved), "Product assigned to store successfully");
    }

    /**
     * Brings a removed assignment back, as though it had just been created.
     *
     * <p>Every field the request carries is applied, so a re-assignment is not quietly bound to the price
     * and flags it had when it was removed — an admin re-adding a product at a new price expects the new
     * price, not a resurrected old one.
     *
     * <p>Its old variant rows are still attached and still {@code isActive} (removing the parent never
     * touched them). They are deactivated first and then re-applied from the request, so a revived
     * assignment cannot silently resurrect a stale price or a stale stock count for a variant the admin
     * did not mention this time.
     */
    private StoreProduct reviveAssignment(StoreProduct sp, AssignProductRequest request) {
        sp.setDeletedAt(null);
        sp.setStorePrice(request.getStorePrice());
        sp.setDiscountType(request.getDiscountType());
        sp.setDiscountValue(request.getDiscountValue());
        // The window too, and unconditionally: a re-assignment that inherited the dates of the sale
        // the listing was removed during would come back already expired, or worse, already running.
        sp.setDiscountStartsAt(request.getDiscountStartsAt());
        sp.setDiscountEndsAt(request.getDiscountEndsAt());
        sp.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        sp.setB2cEnabled(request.getB2cEnabled() != null ? request.getB2cEnabled() : true);
        sp.setB2bEnabled(request.getB2bEnabled() != null ? request.getB2bEnabled() : false);
        StoreProduct saved = storeProductRepository.save(sp);

        for (StoreProductVariant stale : storeProductVariantRepository.findByStoreProduct_Id(saved.getId())) {
            stale.setIsActive(false);
            storeProductVariantRepository.save(stale);
        }
        if (request.getVariants() != null) {
            for (AssignVariantRequest varReq : request.getVariants()) {
                assignVariantToStoreProduct(saved, varReq);
            }
        }
        return saved;
    }

    // ── List products in a store ──────────────────────────────────────────────

    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> getStoreProducts(UUID storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new IllegalArgumentException("Store not found: " + storeId);
        }
        Instant now = Instant.now();
        List<StoreProductResponse> responses = toResponseBatch(
                storeProductRepository.findByStore_IdAndDeletedAtIsNull(storeId), now);
        return ApiResponse.success(responses, "Store products fetched successfully");
    }

    // ── Update store product (price / discount / active) ──────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<StoreProductResponse>> updateStoreProduct(
            UUID storeId, UUID storeProductId, UpdateStoreProductRequest request) {

        StoreProduct sp = resolveStoreProduct(storeId, storeProductId);

        // One `now` for the whole update, so the validation and the window it stores agree.
        Instant now = Instant.now();

        if (Boolean.TRUE.equals(request.getClearDiscount())) {
            // The ONLY way to take a product off a sale through this endpoint. Sending both discount
            // fields as null does not clear anything — null means "leave unchanged" on every field of
            // this PATCH, so the branch below is never even entered — and sending one of them trips
            // validateDiscount and 400s. An explicit flag, rather than a null that means two things.
            clearDiscount(sp);
            if (request.getStorePrice() != null) {
                sp.setStorePrice(request.getStorePrice());
            }
        } else {
            // The WHOLE final state of price + discount + window is resolved first, then validated as
            // one thing, then written.
            //
            // Field-by-field was the bug: storePrice was written before anything looked at it, and the
            // discount was only checked when the request happened to carry one. So PATCH
            // {"storePrice": 800} on a listing holding FIXED 999 stored a listing that CHARGES 999 for
            // an 800 item — invisibly, because hasDiscount() is false at that point, so no surface
            // renders a struck-through price and none says onFlashSale. Since cart lines are now
            // re-priced on every read, that stopped being only a new-order problem: it walks existing
            // baskets UP to 999 on their next read.
            BigDecimal storePrice = request.getStorePrice() != null
                    ? request.getStorePrice() : sp.getStorePrice();

            boolean discountReplaced = request.getDiscountType() != null || request.getDiscountValue() != null;
            Product.DiscountType discountType = discountReplaced
                    ? request.getDiscountType() : sp.getDiscountType();
            BigDecimal discountValue = discountReplaced
                    ? request.getDiscountValue() : sp.getDiscountValue();

            boolean windowReplaced = Boolean.TRUE.equals(request.getClearDiscountWindow())
                    || request.getDiscountStartsAt() != null || request.getDiscountEndsAt() != null;
            Instant startsAt;
            Instant endsAt;
            if (Boolean.TRUE.equals(request.getClearDiscountWindow())) {
                startsAt = null;
                endsAt = null;
            } else if (request.getDiscountStartsAt() != null || request.getDiscountEndsAt() != null) {
                // Each date defaults to what is already stored, so moving only the end of a running
                // sale does not silently discard its start.
                startsAt = request.getDiscountStartsAt() != null
                        ? request.getDiscountStartsAt() : sp.getDiscountStartsAt();
                endsAt = request.getDiscountEndsAt() != null
                        ? request.getDiscountEndsAt() : sp.getDiscountEndsAt();
            } else if (discountReplaced && FlashSalePolicy.windowHasEnded(sp.getDiscountEndsAt(), now)) {
                // A NEW discount does not inherit a DEAD window.
                //
                // The rule: a discount sent with no dates means what it has always meant — a markdown
                // that is live now and has no end. Left to inherit a window that has already run out,
                // the save would produce a discount that is over before it is stored: effectivePrice
                // ignores it, the dashboard badge (built from discountValue) claims a sale, and the
                // flash-sale screen cannot even show the row because it excludes ended sales. Nothing
                // an admin could see would explain why the price never moved.
                //
                // Only a DEAD window is dropped. A window still to come is a scheduled sale whose
                // value the admin may legitimately be amending, so it survives — dropping that would
                // silently start a sale early, which is the same class of mistake in the other
                // direction.
                startsAt = null;
                endsAt = null;
            } else {
                startsAt = sp.getDiscountStartsAt();
                endsAt = sp.getDiscountEndsAt();
            }

            // Re-validated whenever the price, the discount OR ITS WINDOW moves, not only when a
            // discount arrives — that is what closes the price-rise-as-a-sale hole above. A PATCH that
            // touches none of the three is left alone, so an edit to isActive or a channel flag cannot
            // start 400ing on a listing whose numbers were already stored badly.
            //
            // The window belongs in that list because putting a window on a discount is what makes the
            // discount PRICE something. A date-only PATCH — {"discountEndsAt": ...} and nothing else —
            // set windowReplaced, wrote the window and never looked at the value it had just scheduled:
            // a legacy row holding storePrice=1000 with a FIXED discountValue=1200 then charged 1200
            // inside the window, on the card and in the cart, while looking like a sale to nobody
            // (hasDiscount is false at 1200, so no strike-through, no badge, and the flash-sale rail
            // filters the row out). The resolved discount is checked, not the request's, so an untouched
            // bad value cannot be scheduled by sending only dates.
            //
            // clearDiscountWindow on such a row is refused as well, and has to be: taking the window off
            // an above-list discount does not make it harmless, it makes it PERMANENT. The way off a bad
            // sale is clearDiscount, which removes the value and the dates together and is validated by
            // nothing because there is nothing left to price.
            if (request.getStorePrice() != null || discountReplaced || windowReplaced) {
                validateDiscount(discountType, discountValue, storePrice);
            }
            FlashSalePolicy.requireDiscountForWindow(discountType, discountValue, startsAt, endsAt);
            // Only a window the request MOVED is checked against now. An untouched stored window is
            // allowed to be in the past — that is exactly what an expired sale is — and re-validating
            // it would 400 every later edit of a listing that once had one.
            if (windowReplaced) {
                FlashSalePolicy.validateWindow(startsAt, endsAt, now);
            }

            sp.setStorePrice(storePrice);
            sp.setDiscountType(discountType);
            sp.setDiscountValue(discountValue);
            sp.setDiscountStartsAt(startsAt);
            sp.setDiscountEndsAt(endsAt);
        }
        if (request.getIsActive() != null) {
            sp.setIsActive(request.getIsActive());
            // Reactivating has to clear the tombstone as well. Setting isActive=true while deletedAt
            // stayed set produced a row hidden from the admin list AND from every storefront query (they
            // all require deletedAt IS NULL) that nevertheless blocked re-assignment — invisible and
            // permanently stuck.
            if (Boolean.TRUE.equals(request.getIsActive())) {
                sp.setDeletedAt(null);
            }
        }
        if (request.getB2cEnabled() != null) {
            sp.setB2cEnabled(request.getB2cEnabled());
        }
        if (request.getB2bEnabled() != null) {
            sp.setB2bEnabled(request.getB2bEnabled());
        }

        StoreProduct saved = storeProductRepository.save(sp);
        // The same divergence, seen from the LISTING side: lowering a listing to 1000 under variants
        // that still say 5000 reaches the identical state as attaching a 5000 variant to a 1000 listing,
        // and this is the edit an admin is far more likely to make. One query on an admin path.
        for (StoreProductVariant spv : storeProductVariantRepository.findByStoreProduct_Id(saved.getId())) {
            warnIfVariantPriceWillNotBeCharged(saved, spv);
        }
        return ApiResponse.success(toResponse(saved, now), "Store product updated");
    }

    // ── Remove product from store (soft-delete) ───────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeProduct(UUID storeId, UUID storeProductId) {
        StoreProduct sp = resolveStoreProduct(storeId, storeProductId);
        sp.setIsActive(false);
        sp.setDeletedAt(Instant.now());
        storeProductRepository.save(sp);

        // The variant rows too. Removing the parent used to leave them isActive, and their `stock` is
        // real inventory the order path decrements — so a removed assignment could still have units taken
        // off it. Deactivating them also means a later revive starts from a clean slate rather than
        // inheriting stock counts nobody has looked at since.
        for (StoreProductVariant spv : storeProductVariantRepository.findByStoreProduct_Id(sp.getId())) {
            spv.setIsActive(false);
            storeProductVariantRepository.save(spv);
        }
        return ApiResponse.success(null, "Product removed from store");
    }

    // ── Assign variant to store product ───────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<StoreProductResponse.StoreVariantResponse>> assignVariant(
            UUID storeId, UUID storeProductId, AssignVariantRequest request) {

        StoreProduct sp = resolveStoreProduct(storeId, storeProductId);

        StoreProductVariant saved = assignVariantToStoreProduct(sp, request);
        return ApiResponse.created(toVariantResponse(saved, sp, Instant.now()),
                "Variant assigned to store product");
    }

    // ── Update store variant (price / stock / active) ─────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<StoreProductResponse.StoreVariantResponse>> updateStoreVariant(
            UUID storeId, UUID storeProductId, UUID storeVariantId, UpdateStoreVariantRequest request) {

        StoreProduct sp = resolveStoreProduct(storeId, storeProductId);
        StoreProductVariant spv = storeProductVariantRepository.findById(storeVariantId)
                .filter(v -> v.getStoreProduct().getId().equals(sp.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Store variant not found: " + storeVariantId));

        if (request.getStorePrice() != null) spv.setStorePrice(request.getStorePrice());
        if (request.getStock() != null) spv.setStock(request.getStock());
        if (request.getIsActive() != null) spv.setIsActive(request.getIsActive());

        Instant now = Instant.now();
        StoreProductVariant saved = storeProductVariantRepository.save(spv);
        // Both write paths, not just the assign: a variant is far more likely to acquire a price of its
        // own by a later edit than at the moment it is attached.
        warnIfVariantPriceWillNotBeCharged(sp, saved);
        return ApiResponse.success(toVariantResponse(saved, sp, now), "Store variant updated");
    }

    // ── Remove variant from store ─────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeVariant(UUID storeId, UUID storeProductId, UUID storeVariantId) {
        StoreProduct sp = resolveStoreProduct(storeId, storeProductId);
        StoreProductVariant spv = storeProductVariantRepository.findById(storeVariantId)
                .filter(v -> v.getStoreProduct().getId().equals(sp.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Store variant not found: " + storeVariantId));

        spv.setIsActive(false);
        storeProductVariantRepository.save(spv);
        return ApiResponse.success(null, "Variant removed from store product");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private StoreProduct resolveStoreProduct(UUID storeId, UUID storeProductId) {
        StoreProduct sp = storeProductRepository.findById(storeProductId)
                .orElseThrow(() -> new IllegalArgumentException("Store product not found: " + storeProductId));
        if (!sp.getStore().getId().equals(storeId)) {
            throw new IllegalArgumentException("Store product does not belong to store: " + storeId);
        }
        return sp;
    }

    private StoreProductVariant assignVariantToStoreProduct(StoreProduct storeProduct, AssignVariantRequest req) {
        ProductVariant variant = variantRepository.findById(req.getVariantId())
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + req.getVariantId()));

        if (!variant.getProduct().getId().equals(storeProduct.getProduct().getId())) {
            throw new IllegalArgumentException("Variant does not belong to the assigned product");
        }

        // An upsert, not an insert-or-refuse. Removing a variant only sets isActive=false — there is no
        // deletedAt on this entity — and this lookup does NOT filter isActive, so a removed variant was
        // found and re-adding it was refused outright with "Variant is already assigned to this store
        // product". Same bug as the parent assignment had, one layer down and with a different message.
        //
        // It has to be an upsert rather than a delete-and-insert for the same reason the parent is
        // revived: UNIQUE (store_product_id, variant_id) is unconditional, so a second row for the pair
        // cannot exist, and the stock count on the row is real inventory that the order path decrements.
        StoreProductVariant spv = storeProductVariantRepository
                .findByStoreProduct_IdAndVariant_Id(storeProduct.getId(), variant.getId())
                .orElseGet(() -> new StoreProductVariant(storeProduct, variant, req.getStorePrice()));

        spv.setStorePrice(req.getStorePrice());
        spv.setStock(req.getStock() != null ? req.getStock() : 0);
        spv.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
        StoreProductVariant saved = storeProductVariantRepository.save(spv);
        warnIfVariantPriceWillNotBeCharged(storeProduct, saved);
        return saved;
    }

    /**
     * Logs a variant whose {@code store_price} is not the price the shop will charge for it.
     *
     * <p>This is the accepted landmine of the pricing decision, made visible. Every cart line is priced
     * from the PARENT listing — {@code store_product_variants.store_price} is never consulted for money
     * (see {@code CartLinePricing}) — so a 5000 variant sitting under a 1000 listing sells at 1000, in
     * silence, for as long as nobody adds up a revenue report. The two refusals that used to make that
     * state unreachable were removed on purpose: they were written for behaviour that has stopped being
     * true, and between them they made a flash sale impossible on any product with variants.
     *
     * <p>A WARN and not an exception, deliberately. The state is not corrupt — the price charged is the
     * advertised one, which is the whole point — so refusing the write would reinstate exactly the
     * refusal this changeset removed and block a legitimate campaign again. What was missing was not a
     * guard but a signal: the FIRST time it happens should be in a log, not in a quarterly report.
     *
     * <p>Compared against the listing's LIST price rather than its discounted one, and with no clock
     * involved. A variant that simply mirrors its listing is the ordinary case and must stay silent
     * through every sale the listing goes on; a variant carrying a figure of its own is the finding,
     * whether or not a discount happens to be running at the moment somebody saves it.
     */
    private void warnIfVariantPriceWillNotBeCharged(StoreProduct sp, StoreProductVariant spv) {
        BigDecimal variantPrice = spv.getStorePrice();
        BigDecimal listingPrice = sp.getStorePrice();
        if (variantPrice == null || listingPrice == null || variantPrice.compareTo(listingPrice) == 0) {
            return;
        }
        log.warn("[STORE-PRODUCT] Variant {} (sku={}) of listing {} carries storePrice {}, but every cart "
                        + "line is priced from the parent listing (storePrice={}, charged now {}) — so the "
                        + "variant's own figure is never billed. Align it, or price that option as its own "
                        + "product until per-variant pricing exists.",
                spv.getId(), spv.getVariant() != null ? spv.getVariant().getSku() : null, sp.getId(),
                variantPrice, listingPrice, sp.effectivePrice(Instant.now()));
    }

    // requireVariantFreePricing lived here, and refused a timed discount on a listing priced per
    // variant. It is deleted rather than relaxed. It existed for exactly one reason: a variant cart
    // line took its price from store_product_variants.store_price while the card, the rail and the
    // store option quoted the parent listing WITH its discount, so the shop advertised a sale the cart
    // did not charge. Every line is now priced from the parent listing — a variantId picks the SKU and
    // the stock ceiling, never the price (see CartLinePricing) — so that state is unreachable and the
    // refusal was only stopping admins from running a legitimate campaign on a large part of the
    // catalogue, while its comment documented pricing behaviour that had stopped being true.

    /**
     * Delegates to {@link FlashSalePolicy}, which is also what the flash-sale endpoints validate
     * through — two copies of "a fixed discount must be below the store price" is exactly how one of
     * them ends up one release behind the other.
     */
    private void validateDiscount(Product.DiscountType discountType, BigDecimal discountValue, BigDecimal storePrice) {
        if (discountType == null && discountValue == null) return;
        FlashSalePolicy.validateDiscount(discountType, discountValue, storePrice);
    }

    /** Off the sale entirely: no discount and no window, so nothing is left to expire or revive. */
    private void clearDiscount(StoreProduct sp) {
        sp.setDiscountType(null);
        sp.setDiscountValue(null);
        sp.setDiscountStartsAt(null);
        sp.setDiscountEndsAt(null);
    }

    /** NONE / SCHEDULED / LIVE / ENDED — the four states a dashboard row can honestly be in. */
    private static String discountStatus(StoreProduct sp, Instant now) {
        if (sp.getDiscountType() == null || sp.getDiscountValue() == null) return "NONE";
        if (sp.getDiscountStartsAt() != null && now.isBefore(sp.getDiscountStartsAt())) return "SCHEDULED";
        if (sp.getDiscountEndsAt() != null && sp.getDiscountEndsAt().isBefore(now)) return "ENDED";
        return "LIVE";
    }

    StoreProductResponse toResponse(StoreProduct sp) {
        return toResponse(sp, Instant.now());
    }

    /**
     * @param now one instant for the whole response — a list of 300 store products resolved against
     *            300 clocks could show two rows of the same sale as LIVE and ENDED.
     */
    StoreProductResponse toResponse(StoreProduct sp, Instant now) {
        return toResponse(sp, now,
                translationRepository.findByProductId(sp.getProduct().getId()),
                storeProductVariantRepository.findByStoreProduct_Id(sp.getId()));
    }

    /**
     * A whole listing in three queries instead of two per row.
     *
     * <p>Every row needs its English title and its variant rows, and asking per row is what made the
     * store-product list and the flash-sale screen N+1 — 200 listings meant 401 queries, all of them
     * to fill in two columns.
     */
    List<StoreProductResponse> toResponseBatch(List<StoreProduct> rows, Instant now) {
        if (rows.isEmpty()) return List.of();

        List<UUID> productIds = rows.stream().map(sp -> sp.getProduct().getId()).distinct().toList();
        Map<UUID, List<ProductTranslation>> translations = translationRepository.findByProductIdIn(productIds)
                .stream().collect(Collectors.groupingBy(t -> t.getProduct().getId()));

        List<UUID> storeProductIds = rows.stream().map(StoreProduct::getId).toList();
        Map<UUID, List<StoreProductVariant>> variants =
                storeProductVariantRepository.findByStoreProduct_IdIn(storeProductIds)
                        .stream().collect(Collectors.groupingBy(v -> v.getStoreProduct().getId()));

        return rows.stream()
                .map(sp -> toResponse(sp, now,
                        translations.getOrDefault(sp.getProduct().getId(), List.of()),
                        variants.getOrDefault(sp.getId(), List.of())))
                .toList();
    }

    private StoreProductResponse toResponse(StoreProduct sp, Instant now,
                                            List<ProductTranslation> translations,
                                            List<StoreProductVariant> variantRows) {
        StoreProductResponse r = new StoreProductResponse();
        r.setId(sp.getId());
        r.setStoreId(sp.getStore().getId());
        r.setProductId(sp.getProduct().getId());
        r.setProductSku(sp.getProduct().getSku());

        translations.stream()
                .filter(t -> "EN".equalsIgnoreCase(t.getLanguage()))
                .findFirst()
                .ifPresent(t -> r.setProductTitle(t.getTitle()));

        r.setStorePrice(sp.getStorePrice());
        r.setEffectivePrice(sp.effectivePrice(now));
        r.setDiscountType(sp.getDiscountType() != null ? sp.getDiscountType().name() : null);
        r.setDiscountValue(sp.getDiscountValue());
        r.setDiscountStartsAt(sp.getDiscountStartsAt());
        r.setDiscountEndsAt(sp.getDiscountEndsAt());
        // effectivePrice alone is not enough for the admin screen: a SCHEDULED sale returns
        // effectivePrice == storePrice while discountType/discountValue are set, and the dashboard
        // builds its "25% OFF" badge from discountValue — so it would label a sale that has not
        // started as live, and keep the badge on one that has ended. Say which it is.
        r.setDiscountActive(sp.hasDiscount(now));
        r.setDiscountStatus(discountStatus(sp, now));
        r.setOnFlashSale(sp.onFlashSale(now));
        r.setFlashSaleEndsAt(sp.flashSaleEndsAt(now));
        r.setIsActive(sp.getIsActive());
        r.setB2cEnabled(sp.getB2cEnabled());
        r.setB2bEnabled(sp.getB2bEnabled());
        r.setCreatedAt(sp.getCreatedAt());
        r.setUpdatedAt(sp.getUpdatedAt());

        r.setVariants(variantRows.stream().map(v -> toVariantResponse(v, sp, now)).toList());

        return r;
    }

    /**
     * @param parent the listing the variant hangs off, which is what a line of this variant is actually
     *               charged: {@code effectivePrice} is the PARENT's, not arithmetic on
     *               {@code spv.storePrice}, because a variantId does not decide a price (see
     *               {@code CartLinePricing}). The admin screen shows both, so it is visible that the
     *               per-variant figure is recorded and not billed.
     */
    private StoreProductResponse.StoreVariantResponse toVariantResponse(StoreProductVariant spv,
                                                                        StoreProduct parent, Instant now) {
        StoreProductResponse.StoreVariantResponse r = new StoreProductResponse.StoreVariantResponse();
        r.setId(spv.getId());
        r.setVariantId(spv.getVariant().getId());
        r.setVariantSku(spv.getVariant().getSku());
        r.setStorePrice(spv.getStorePrice());
        r.setEffectivePrice(parent.effectivePrice(now));
        r.setStock(spv.getStock());
        r.setIsActive(spv.getIsActive());
        r.setUpdatedAt(spv.getUpdatedAt());
        return r;
    }
}
