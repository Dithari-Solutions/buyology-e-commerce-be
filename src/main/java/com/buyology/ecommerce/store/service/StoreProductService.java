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

        if (storeProductRepository.findByStore_IdAndProduct_IdAndIsActiveTrue(storeId, product.getId()).isPresent()) {
            throw new IllegalArgumentException("Product is already assigned to this store");
        }

        validateDiscount(request.getDiscountType(), request.getDiscountValue(), request.getStorePrice());

        StoreProduct storeProduct = new StoreProduct(store, product, request.getStorePrice());
        storeProduct.setDiscountType(request.getDiscountType());
        storeProduct.setDiscountValue(request.getDiscountValue());
        storeProduct.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        StoreProduct saved = storeProductRepository.save(storeProduct);

        // Assign variants if provided inline
        if (request.getVariants() != null) {
            for (AssignVariantRequest varReq : request.getVariants()) {
                assignVariantToStoreProduct(saved, varReq);
            }
        }

        return ApiResponse.created(toResponse(saved), "Product assigned to store successfully");
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

        if (storeProductVariantRepository
                .findByStoreProduct_IdAndVariant_Id(storeProduct.getId(), variant.getId()).isPresent()) {
            throw new IllegalArgumentException("Variant is already assigned to this store product");
        }

        StoreProductVariant spv = new StoreProductVariant(storeProduct, variant, req.getStorePrice());
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
        if (sp.getDiscountType() == null || sp.getDiscountValue() == null) {
            return sp.getStorePrice();
        }
        return switch (sp.getDiscountType()) {
            case FIXED -> sp.getDiscountValue();
            case PERCENTAGE -> sp.getStorePrice()
                    .multiply(BigDecimal.ONE.subtract(
                            sp.getDiscountValue().divide(new BigDecimal("100"))))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        };
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
