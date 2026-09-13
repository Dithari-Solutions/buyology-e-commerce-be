package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
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
import com.buyology.ecommerce.store.dto.StoreProductResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreProductRequest;
import com.buyology.ecommerce.store.dto.UpdateStoreVariantRequest;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class StoreProductService {

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
        List<StoreProductResponse> responses = storeProductRepository
                .findByStore_IdAndDeletedAtIsNull(storeId).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(responses, "Store products fetched successfully");
    }

    // ── Update store product (price / discount / active) ──────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<StoreProductResponse>> updateStoreProduct(
            UUID storeId, UUID storeProductId, UpdateStoreProductRequest request) {

        StoreProduct sp = resolveStoreProduct(storeId, storeProductId);

        if (request.getStorePrice() != null) {
            sp.setStorePrice(request.getStorePrice());
        }
        if (request.getDiscountType() != null || request.getDiscountValue() != null) {
            validateDiscount(request.getDiscountType(), request.getDiscountValue(),
                    request.getStorePrice() != null ? request.getStorePrice() : sp.getStorePrice());
            sp.setDiscountType(request.getDiscountType());
            sp.setDiscountValue(request.getDiscountValue());
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

        return ApiResponse.success(toResponse(storeProductRepository.save(sp)), "Store product updated");
    }

    // ── Remove product from store (soft-delete) ───────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeProduct(UUID storeId, UUID storeProductId) {
        StoreProduct sp = resolveStoreProduct(storeId, storeProductId);
        sp.setIsActive(false);
        sp.setDeletedAt(java.time.Instant.now());
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
        return ApiResponse.created(toVariantResponse(saved), "Variant assigned to store product");
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

        return ApiResponse.success(toVariantResponse(storeProductVariantRepository.save(spv)), "Store variant updated");
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
        return storeProductVariantRepository.save(spv);
    }

    private void validateDiscount(Product.DiscountType discountType, BigDecimal discountValue, BigDecimal storePrice) {
        if (discountType != null && discountValue == null) {
            throw new IllegalArgumentException("discountValue is required when discountType is set");
        }
        if (discountValue != null && discountType == null) {
            throw new IllegalArgumentException("discountType is required when discountValue is set");
        }
        if (discountType == Product.DiscountType.FIXED && storePrice != null
                && discountValue != null && discountValue.compareTo(storePrice) >= 0) {
            throw new IllegalArgumentException("Fixed discount value must be lower than storePrice");
        }
        if (discountType == Product.DiscountType.PERCENTAGE && discountValue != null
                && (discountValue.compareTo(BigDecimal.ZERO) <= 0
                        || discountValue.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Percentage discount must be between 0 and 100");
        }
    }

    private BigDecimal computeEffectivePrice(StoreProduct sp) {
        return sp.effectivePrice();
    }

    private StoreProductResponse toResponse(StoreProduct sp) {
        StoreProductResponse r = new StoreProductResponse();
        r.setId(sp.getId());
        r.setStoreId(sp.getStore().getId());
        r.setProductId(sp.getProduct().getId());
        r.setProductSku(sp.getProduct().getSku());

        translationRepository.findByProductId(sp.getProduct().getId()).stream()
                .filter(t -> "EN".equalsIgnoreCase(t.getLanguage()))
                .findFirst()
                .ifPresent(t -> r.setProductTitle(t.getTitle()));

        r.setStorePrice(sp.getStorePrice());
        r.setEffectivePrice(computeEffectivePrice(sp));
        r.setDiscountType(sp.getDiscountType() != null ? sp.getDiscountType().name() : null);
        r.setDiscountValue(sp.getDiscountValue());
        r.setIsActive(sp.getIsActive());
        r.setB2cEnabled(sp.getB2cEnabled());
        r.setB2bEnabled(sp.getB2bEnabled());
        r.setCreatedAt(sp.getCreatedAt());
        r.setUpdatedAt(sp.getUpdatedAt());

        List<StoreProductResponse.StoreVariantResponse> variantResponses =
                storeProductVariantRepository.findByStoreProduct_Id(sp.getId()).stream()
                        .map(this::toVariantResponse)
                        .toList();
        r.setVariants(variantResponses);

        return r;
    }

    private StoreProductResponse.StoreVariantResponse toVariantResponse(StoreProductVariant spv) {
        StoreProductResponse.StoreVariantResponse r = new StoreProductResponse.StoreVariantResponse();
        r.setId(spv.getId());
        r.setVariantId(spv.getVariant().getId());
        r.setVariantSku(spv.getVariant().getSku());
        r.setStorePrice(spv.getStorePrice());
        r.setStock(spv.getStock());
        r.setIsActive(spv.getIsActive());
        r.setUpdatedAt(spv.getUpdatedAt());
        return r;
    }
}
