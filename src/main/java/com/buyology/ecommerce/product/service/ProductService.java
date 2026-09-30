package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.enums.SpecUnit;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.service.DeliveryFeePolicy;
import com.buyology.ecommerce.product.domain.Brand;
import com.buyology.ecommerce.product.domain.GlobalSpecGroup;
import com.buyology.ecommerce.product.domain.GlobalSpecGroupTranslation;
import com.buyology.ecommerce.product.domain.GlobalSpecOption;
import com.buyology.ecommerce.product.domain.GlobalSpecOptionTranslation;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductAccessory;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.product.domain.ProductNotFoundException;
import com.buyology.ecommerce.product.domain.ProductSpecGroup;
import com.buyology.ecommerce.product.domain.ProductSpecGroupTranslation;
import com.buyology.ecommerce.product.domain.ProductSpecOption;
import com.buyology.ecommerce.product.domain.ProductSpecOptionTranslation;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.product.domain.ProductVariantOption;
import com.buyology.ecommerce.product.dto.CreateColorRequest;
import com.buyology.ecommerce.product.dto.CreateProductRequest;
import com.buyology.ecommerce.common.utils.FileValidationUtils;
import com.buyology.ecommerce.common.utils.SlugUtils;
import com.buyology.ecommerce.product.dto.CreateSpecGroupRequest;
import com.buyology.ecommerce.product.dto.CreateSpecOptionRequest;
import com.buyology.ecommerce.product.dto.CreateVariantRequest;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.dto.ProductTranslationRequest;
import com.buyology.ecommerce.product.dto.UpdateProductRequest;
import com.buyology.ecommerce.product.dto.ProductFilterRequest;
import com.buyology.ecommerce.product.repository.BrandRepository;
import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupTranslationRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecOptionRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecOptionTranslationRepository;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.product.repository.ProductAccessoryRepository;
import com.buyology.ecommerce.product.repository.ProductSpecification;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductMediaRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductSpecGroupRepository;
import com.buyology.ecommerce.product.repository.ProductSpecGroupTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.common.utils.HtmlSanitizer;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.product.repository.ProductVariantOptionRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.repository.CountryRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.product.search.service.ProductSearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductService.class);

    /**
     * Browse channel. B2C = consumer shop (only b2cEnabled store products, real buyable price).
     * B2B = bulk/quote catalog (only b2bEnabled store products in B2B-enabled countries; no
     * buyable price exposed — responses are marked quoteOnly=true).
     */
    public enum Channel { B2C, B2B }

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    /** Only for resolving the primary category by name — see {@link #primaryCategoryIds()}. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository categoryTranslationRepository;

    /** The category the default catalogue order leads with, by its English name. */
    @org.springframework.beans.factory.annotation.Value("${app.catalog.first-category:Laptop}")
    private String primaryCategoryName;

    private volatile java.util.Set<UUID> primaryCategoryIdsCache;
    private volatile long primaryCategoryIdsAt;
    private final BrandRepository brandRepository;
    private final BrandTranslationRepository brandTranslationRepository;
    private final GlobalSpecGroupRepository globalSpecGroupRepository;
    private final GlobalSpecGroupTranslationRepository globalSpecGroupTranslationRepository;
    private final GlobalSpecOptionRepository globalSpecOptionRepository;
    private final GlobalSpecOptionTranslationRepository globalSpecOptionTranslationRepository;
    private final ProductSpecGroupRepository specGroupRepository;
    private final ProductSpecGroupTranslationRepository specGroupTranslationRepository;
    private final ProductSpecOptionRepository specOptionRepository;
    private final ProductSpecOptionTranslationRepository specOptionTranslationRepository;
    private final ProductTranslationRepository translationRepository;
    private final ProductMediaRepository mediaRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductVariantOptionRepository variantOptionRepository;
    private final ProductAccessoryRepository accessoryRepository;
    private final StoreProductRepository storeProductRepository;
    private final CountryRepository countryRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final DeliveryFeePolicy deliveryFeePolicy;
    private final StoreLocationRepository storeLocationRepository;
    private final ContaboObjectService contaboObjectService;
    private final ProductSearchService productSearchService;
    private final com.buyology.ecommerce.review.repository.ProductReviewStatsRepository productReviewStatsRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            BrandRepository brandRepository,
            BrandTranslationRepository brandTranslationRepository,
            GlobalSpecGroupRepository globalSpecGroupRepository,
            GlobalSpecGroupTranslationRepository globalSpecGroupTranslationRepository,
            GlobalSpecOptionRepository globalSpecOptionRepository,
            GlobalSpecOptionTranslationRepository globalSpecOptionTranslationRepository,
            ProductSpecGroupRepository specGroupRepository,
            ProductSpecGroupTranslationRepository specGroupTranslationRepository,
            ProductSpecOptionRepository specOptionRepository,
            ProductSpecOptionTranslationRepository specOptionTranslationRepository,
            ProductTranslationRepository translationRepository,
            ProductMediaRepository mediaRepository,
            ProductVariantRepository variantRepository,
            ProductVariantOptionRepository variantOptionRepository,
            ProductAccessoryRepository accessoryRepository,
            StoreProductRepository storeProductRepository,
            CountryRepository countryRepository,
            CurrencyExchangeService currencyExchangeService,
            DeliveryFeePolicy deliveryFeePolicy,
            StoreLocationRepository storeLocationRepository,
            ContaboObjectService contaboObjectService,
            ProductSearchService productSearchService,
            com.buyology.ecommerce.review.repository.ProductReviewStatsRepository productReviewStatsRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.brandTranslationRepository = brandTranslationRepository;
        this.globalSpecGroupRepository = globalSpecGroupRepository;
        this.globalSpecGroupTranslationRepository = globalSpecGroupTranslationRepository;
        this.globalSpecOptionRepository = globalSpecOptionRepository;
        this.globalSpecOptionTranslationRepository = globalSpecOptionTranslationRepository;
        this.specGroupRepository = specGroupRepository;
        this.specGroupTranslationRepository = specGroupTranslationRepository;
        this.specOptionRepository = specOptionRepository;
        this.specOptionTranslationRepository = specOptionTranslationRepository;
        this.translationRepository = translationRepository;
        this.mediaRepository = mediaRepository;
        this.variantRepository = variantRepository;
        this.variantOptionRepository = variantOptionRepository;
        this.accessoryRepository = accessoryRepository;
        this.storeProductRepository = storeProductRepository;
        this.countryRepository = countryRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.deliveryFeePolicy = deliveryFeePolicy;
        this.storeLocationRepository = storeLocationRepository;
        this.contaboObjectService = contaboObjectService;
        this.productSearchService = productSearchService;
        this.productReviewStatsRepository = productReviewStatsRepository;
    }

    /**
     * Creates a new product with all associated data in a single atomic transaction:
     * translations (AZ, EN, AR), spec groups/options (descriptive only — there is no per-option price),
     * colors (each with their own media), remaining media, variants, and accessories.
     *
     * @param request    the product creation request
     * @param mediaFiles flat list of uploaded media files; colors claim files by index
     * @return the created product wrapped in an ApiResponse
     */
    /**
     * Supplier-side wrapper around {@link #createProduct} that runs the full admin
     * product-creation pipeline (translations, specs, colors, media, variants,
     * accessories) and then stamps supplier-specific moderation flags onto the
     * resulting product so it stays hidden until admin approves AND the supplier
     * publishes:
     *   - supplierId = current supplier
     *   - supplierStatus = PENDING_REVIEW
     *   - isActive = false (draft)
     *   - status = "INACTIVE" (catalog filters skip non-ACTIVE)
     */
    @Transactional
    public ResponseEntity<ApiResponse<ProductResponse>> createProductForSupplier(
            CreateProductRequest request,
            List<MultipartFile> mediaFiles,
            java.util.UUID supplierId) {
        ResponseEntity<ApiResponse<ProductResponse>> resp = createProduct(request, mediaFiles);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null
                || resp.getBody().getData() == null) {
            return resp;
        }
        java.util.UUID productId = resp.getBody().getData().getId();
        Product p = productRepository.findById(productId).orElse(null);
        if (p == null) return resp;
        p.setSupplierId(supplierId);
        p.setSupplierStatus(Product.SupplierStatus.PENDING_REVIEW);
        p.setIsActive(false);
        p.setStatus("INACTIVE");
        productRepository.save(p);
        return resp;
    }

    @Transactional
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            CreateProductRequest request,
            List<MultipartFile> mediaFiles) {

        // 1. Resolve and validate the category
        ProductCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found with id: " + request.getCategoryId()));

        // 1b. Optionally resolve brand
        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Brand not found with id: " + request.getBrandId()));
        }

        // 2. Refurb grade is mandatory when the product is refurbished
        if (Boolean.TRUE.equals(request.getIsRefurbished()) && request.getRefurbGrade() == null) {
            throw new IllegalArgumentException(
                    "Refurb grade (A, B, or C) is required when isRefurbished is true");
        }

        // 3. Persist base product — use the caller-supplied SKU if present, otherwise auto-generate
        String sku;
        if (request.getSku() != null && !request.getSku().isBlank()) {
            sku = request.getSku().trim();
            if (productRepository.existsBySku(sku)) {
                throw new IllegalArgumentException("Product SKU already exists: " + sku);
            }
        } else {
            sku = generateSku(request.getProductType());
        }
        Product product = new Product(
                category,
                brand,
                request.getProductType(),
                request.getIsRefurbished(),
                request.getRefurbGrade(),
                sku,
                request.getStatus(),
                request.getAvailabilityStatus(),
                request.getIsSuperDeal(),
                request.getIsLimitedStock());
        product.setStockQuantity(request.getStockQuantity());
        // Null means this product's stock is not tracked and it sells without a ceiling — which is
        // how the whole catalogue behaves until somebody states a figure. See V55.
        product.setAvailableQuantity(request.getAvailableQuantity());
        Product savedProduct = productRepository.save(product);

        // 5. Save translations
        List<ProductTranslation> savedTranslations = saveTranslations(savedProduct, request.getTranslations());

        // 6. Create inline spec groups/options — builds localKey → option map for variant resolution
        Map<String, ProductSpecOption> localKeyToOption = new HashMap<>();
        saveSpecs(savedProduct, request.getSpecs(), localKeyToOption);

        // 7. Create color spec options with their own media; track which file indices are claimed
        Set<Integer> claimedMediaIndices = new HashSet<>();
        List<ProductResponse.ColorOptionDto> colorDtos = saveColors(
                savedProduct, request.getColors(), mediaFiles, localKeyToOption, claimedMediaIndices);

        // 8. Save remaining (product-level) media files — those not claimed by any color
        List<ProductResponse.MediaDto> mediaDtos = saveProductMedia(savedProduct, mediaFiles, claimedMediaIndices);

        // 9. Create variants — spec options resolved by existing UUID or inline localKey
        saveVariants(savedProduct, request.getVariants(), localKeyToOption);

        // 10. Link accessories
        List<UUID> resolvedAccessoryIds = saveAccessories(savedProduct, request.getAccessoryIds());

        // 11. Build response — fetch all nested data from DB for a consistent response
        ProductTranslation first = savedTranslations.get(0);
        List<ProductResponse.SpecGroupDto> specGroupDtos = buildSpecGroupDtos(savedProduct.getId(), "EN");
        List<ProductResponse.VariantDto> variantDtos = variantRepository.findByProductId(savedProduct.getId()).stream()
                .map(v -> {
                    List<UUID> optionIds = variantOptionRepository.findByVariantId(v.getId()).stream()
                            .map(vo -> vo.getOption().getId())
                            .toList();
                    return new ProductResponse.VariantDto(v.getId(), v.getSku(), optionIds);
                })
                .toList();
        ProductResponse response = buildResponse(
                savedProduct, first.getTitle(), first.getDescription(), first.getSlug(),
                mediaDtos, specGroupDtos, colorDtos, variantDtos, resolvedAccessoryIds, true, "EN");

        // Index in Elasticsearch
        productSearchService.indexProduct(savedProduct, savedTranslations);

        return ApiResponse.created(response, "Product created successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProductsAdmin(String lang) {
        // Admin lists all products (for client-side search/filters), so no paging —
        // but batch-load to avoid the per-product N+1 that made 252 products time out.
        List<ProductResponse> responses = toResponseBatch(productRepository.findByStatusNot("DELETED"), lang, true);
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    /** Server-side paginated + searchable admin product list (SKU / any-language title). */
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.common.response.PageResponse<ProductResponse>>> getAllProductsAdminPaged(
            String lang, String search, String status, int page, int size) {
        // Empty string (not null) so Postgres can type the LIKE/CONCAT param — a null
        // bind in '%'||?||'%' is inferred as bytea and blows up lower(bytea).
        String q = (search == null) ? "" : search.trim();
        String st = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) ? null : status.toUpperCase();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), Math.min(Math.max(1, size), 100),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<Product> pg = productRepository.searchAdmin(q, st, pageable);
        List<ProductResponse> content = toResponseBatch(pg.getContent(), lang, true);
        return ApiResponse.success(
                com.buyology.ecommerce.common.response.PageResponse.of(pg, content),
                "Products fetched successfully");
    }

    /** Cheap status counts for the admin product dashboard cards. */
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAdminProductStats() {
        return ApiResponse.success(Map.of(
                "total", productRepository.countByStatusNot("DELETED"),
                "active", productRepository.countByStatus("ACTIVE"),
                "inactive", productRepository.countByStatus("INACTIVE")),
                "Product stats");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategoryAdmin(UUID categoryId, String lang) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));

        List<ProductResponse> responses = toResponseBatch(
                productRepository.findByStatusNotAndCategoryId("DELETED", categoryId), lang, true);
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    /**
     * Activate / deactivate a product (admin). Toggles between ACTIVE and
     * INACTIVE. A trashed (DELETED) product must be restored first.
     */
    @Transactional
    public ResponseEntity<ApiResponse<Void>> setProductStatus(UUID id, String status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        if ("DELETED".equals(product.getStatus())) {
            throw new IllegalArgumentException("Restore the product from trash before changing its status");
        }
        product.setStatus(normalized);
        product.setIsActive("ACTIVE".equals(normalized));
        productRepository.save(product);
        return ApiResponse.success(null, "Product status updated to " + normalized);
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> softDeleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if ("DELETED".equals(product.getStatus())) {
            throw new IllegalArgumentException("Product is already in trash");
        }
        product.setStatus("DELETED");
        product.setDeletedAt(Instant.now());
        productRepository.save(product);

        // Free up slugs so the same product name can be reused after deletion
        String idSuffix = "-" + id.toString().replace("-", "").substring(0, 8);
        List<ProductTranslation> translations = translationRepository.findByProductId(id);
        for (ProductTranslation translation : translations) {
            translation.setSlug(translation.getSlug() + idSuffix);
        }
        translationRepository.saveAll(translations);

        return ApiResponse.success(null, "Product moved to trash");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getTrash(String lang) {
        List<ProductResponse> responses = productRepository.findByStatus("DELETED").stream()
                .map(p -> toResponse(p, lang, true))
                .toList();
        return ApiResponse.success(responses, "Trash fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<ProductResponse>> restoreFromTrash(UUID id, String lang) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (!"DELETED".equals(product.getStatus())) {
            throw new IllegalArgumentException("Product is not in trash");
        }

        // Block restore if another active product has since taken any of this product's names.
        List<ProductTranslation> translations = translationRepository.findByProductId(id);
        for (ProductTranslation t : translations) {
            assertTitleAvailable(t.getLanguage(), t.getTitle(), id);
        }

        product.setStatus("ACTIVE");
        product.setDeletedAt(null);
        productRepository.save(product);

        // Restore the original (un-suffixed) slug when it's free again.
        String idSuffix = "-" + id.toString().replace("-", "").substring(0, 8);
        for (ProductTranslation t : translations) {
            if (t.getSlug() != null && t.getSlug().endsWith(idSuffix)) {
                String original = t.getSlug().substring(0, t.getSlug().length() - idSuffix.length());
                if (!translationRepository.existsActiveByLanguageAndSlug(t.getLanguage(), original)) {
                    t.setSlug(original);
                }
            }
        }
        translationRepository.saveAll(translations);

        return ApiResponse.success(toResponse(product, lang, true), "Product restored successfully");
    }

    /**
     * Partial update of a product. Only the non-null fields on {@code request}
     * are applied. Media can be edited: existing items removed by id, new files
     * appended, and an existing item promoted to primary. Variants, specs and
     * colors are out of scope and left untouched.
     */
    @Transactional
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            UUID id, UpdateProductRequest request, List<MultipartFile> newFiles) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if ("DELETED".equals(product.getStatus())) {
            throw new IllegalArgumentException("Restore the product from trash before editing it");
        }

        // ── Scalar fields ────────────────────────────────────────────────────
        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Brand not found with id: " + request.getBrandId()));
            product.setBrand(brand);
        }
        if (request.getProductType() != null) {
            product.setProductType(request.getProductType());
        }
        if (request.getAvailabilityStatus() != null) {
            product.setAvailabilityStatus(request.getAvailabilityStatus());
        }
        if (request.getIsSuperDeal() != null) {
            product.setIsSuperDeal(request.getIsSuperDeal());
        }
        if (request.getIsLimitedStock() != null) {
            product.setIsLimitedStock(request.getIsLimitedStock());
        }
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        // Untracking is checked FIRST and wins, so "stop limiting this product" cannot be defeated by
        // a stale number the client happened to send alongside it. An omitted availableQuantity leaves
        // the figure alone, as every other field here does — the explicit flag exists because that
        // convention otherwise leaves no way to say "back to not tracked".
        if (Boolean.TRUE.equals(request.getUntrackAvailableQuantity())) {
            product.setAvailableQuantity(null);
        } else if (request.getAvailableQuantity() != null) {
            product.setAvailableQuantity(request.getAvailableQuantity());
        }
        if (request.getIsRefurbished() != null) {
            product.setIsRefurbished(request.getIsRefurbished());
        }
        if (request.getRefurbGrade() != null) {
            product.setRefurbGrade(request.getRefurbGrade());
        }
        // Keep refurbished/grade consistent
        if (Boolean.TRUE.equals(product.getIsRefurbished()) && product.getRefurbGrade() == null) {
            throw new IllegalArgumentException(
                    "Refurb grade (A, B, or C) is required when isRefurbished is true");
        }
        if (Boolean.FALSE.equals(product.getIsRefurbished())) {
            product.setRefurbGrade(null);
        }
        if (request.getStatus() != null) {
            String normalized = request.getStatus().trim().toUpperCase();
            if (!normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
                throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
            }
            product.setStatus(normalized);
            product.setIsActive("ACTIVE".equals(normalized));
        }

        // ── Translations (partial) ───────────────────────────────────────────
        if (request.getTranslations() != null) {
            applyTranslationPatch(product, request.getTranslations());
        }

        // ── SKU (optional; must stay unique) ─────────────────────────────────
        if (request.getSku() != null && !request.getSku().isBlank()) {
            String newSku = request.getSku().trim();
            if (!newSku.equals(product.getSku()) && productRepository.existsBySku(newSku)) {
                throw new IllegalArgumentException("Product SKU already exists: " + newSku);
            }
            product.setSku(newSku);
        }

        productRepository.save(product);

        // ── Accessories: full replacement when provided ──────────────────────
        if (request.getAccessoryIds() != null) {
            accessoryRepository.deleteAllInBatch(accessoryRepository.findByProductId(id));
            accessoryRepository.flush();
            saveAccessories(product, request.getAccessoryIds());
        }

        // ── Specs: full replacement when provided (null = leave untouched, [] = clear) ──
        if (request.getSpecs() != null) {
            // Variants reference spec options by FK; replacing specs would orphan them.
            if (!variantRepository.findByProductId(id).isEmpty()) {
                throw new IllegalArgumentException(
                        "Cannot edit specifications while the product has variants. Remove variants first.");
            }
            deleteProductSpecs(id, false); // keep the synthetic color_* group
            specGroupRepository.flush();
            saveSpecs(product, request.getSpecs(), new HashMap<>());
        }

        // ── Media: remove ────────────────────────────────────────────────────
        if (request.getRemoveMediaIds() != null) {
            for (UUID mediaId : request.getRemoveMediaIds()) {
                ProductMedia media = mediaRepository.findById(mediaId)
                        .orElseThrow(() -> new IllegalArgumentException("Media not found with id: " + mediaId));
                if (!media.getProduct().getId().equals(id)) {
                    throw new IllegalArgumentException("Media does not belong to this product");
                }
                contaboObjectService.deleteFile(media.getUrl());
                mediaRepository.delete(media);
            }
        }

        // ── Media: add new product-level files ───────────────────────────────
        if (newFiles != null && !newFiles.isEmpty()) {
            List<ProductMedia> existing = mediaRepository.findByProductIdAndColorOptionIsNull(id);
            int nextOrder = existing.stream().mapToInt(ProductMedia::getOrderIndex).max().orElse(-1) + 1;
            boolean hasPrimary = existing.stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsPrimary()));
            List<ProductMedia> toAdd = new ArrayList<>();
            for (MultipartFile file : newFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String url = uploadToContabo(id, file, "product_" + nextOrder);
                boolean isPrimary = !hasPrimary && toAdd.isEmpty();
                toAdd.add(new ProductMedia(
                        product, resolveMediaType(file.getContentType()), url, null, isPrimary, nextOrder));
                nextOrder++;
            }
            mediaRepository.saveAll(toAdd);
        }

        // ── Media: primary selection ─────────────────────────────────────────
        if (request.getPrimaryMediaId() != null) {
            setPrimaryMedia(id, request.getPrimaryMediaId());
        } else {
            ensureSomePrimary(id);
        }

        // Keep the search index in sync
        List<ProductTranslation> translations = translationRepository.findByProductId(id);
        productSearchService.indexProduct(product, translations);

        return ApiResponse.success(toResponse(product, "EN", true), "Product updated successfully");
    }

    private void applyTranslationPatch(Product product, UpdateProductRequest.TranslationPatch patch) {
        List<ProductTranslation> translations = translationRepository.findByProductId(product.getId());
        for (ProductTranslation t : translations) {
            String lang = t.getLanguage() == null ? "" : t.getLanguage().toUpperCase();
            String newTitle = switch (lang) {
                case "AZ" -> patch.getTitleAz();
                case "EN" -> patch.getTitleEn();
                case "AR" -> patch.getTitleAr();
                default -> null;
            };
            String newDescription = switch (lang) {
                case "AZ" -> patch.getDescriptionAz();
                case "EN" -> patch.getDescriptionEn();
                case "AR" -> patch.getDescriptionAr();
                default -> null;
            };

            if (newTitle != null && !newTitle.isBlank() && !newTitle.equals(t.getTitle())) {
                // Reject if another active product already uses this name in this language.
                assertTitleAvailable(lang, newTitle, product.getId());
                String newSlug = SlugUtils.toSlug(newTitle);
                if (!newSlug.equals(t.getSlug())) {
                    if (translationRepository.existsActiveByLanguageAndSlug(lang, newSlug)) {
                        throw new IllegalArgumentException(
                                "A product with the name '" + newTitle + "' already exists");
                    }
                    freeDeletedSlug(lang, newSlug);
                    translationRepository.flush();
                    t.setSlug(newSlug);
                }
                t.setTitle(HtmlSanitizer.stripHtml(newTitle));
            }
            // A non-null description (including empty string) overwrites; null = unchanged
            if (newDescription != null) {
                t.setDescription(HtmlSanitizer.stripHtml(newDescription));
            }
        }
        translationRepository.saveAll(translations);
    }

    /** Marks the given product-level media as primary and clears the flag on the rest. */
    private void setPrimaryMedia(UUID productId, UUID primaryMediaId) {
        List<ProductMedia> media = mediaRepository.findByProductIdAndColorOptionIsNull(productId);
        boolean found = media.stream().anyMatch(m -> m.getId().equals(primaryMediaId));
        if (!found) {
            throw new IllegalArgumentException("Primary media not found on this product: " + primaryMediaId);
        }
        for (ProductMedia m : media) {
            m.setIsPrimary(m.getId().equals(primaryMediaId));
        }
        mediaRepository.saveAll(media);
    }

    /** Ensures at least one product-level media is primary (used after removals). */
    private void ensureSomePrimary(UUID productId) {
        List<ProductMedia> media = mediaRepository.findByProductIdAndColorOptionIsNull(productId);
        if (media.isEmpty() || media.stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsPrimary()))) {
            return;
        }
        media.stream()
                .min(java.util.Comparator.comparingInt(ProductMedia::getOrderIndex))
                .ifPresent(m -> {
                    m.setIsPrimary(true);
                    mediaRepository.save(m);
                });
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeTrashedProducts() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Product> expired = productRepository.findByStatusAndDeletedAtBefore("DELETED", cutoff);
        for (Product product : expired) {
            hardDeleteProduct(product);
        }
    }

    private void hardDeleteProduct(Product product) {
        UUID productId = product.getId();

        // 1. Variant options → variants
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        for (ProductVariant variant : variants) {
            variantOptionRepository.deleteAllInBatch(variantOptionRepository.findByVariantId(variant.getId()));
        }
        variantRepository.deleteAllInBatch(variants);

        // 2. Spec options (with translations) → spec groups (with translations)
        deleteProductSpecs(productId, true);

        // 3. Media
        mediaRepository.deleteAllInBatch(mediaRepository.findByProductId(productId));

        // 4. Accessory links (where this product is the main product or the accessory)
        accessoryRepository.deleteAllInBatch(accessoryRepository.findByProductId(productId));
        accessoryRepository.deleteAllInBatch(accessoryRepository.findByAccessoryId(productId));

        // 5. Translations
        translationRepository.deleteAllInBatch(translationRepository.findByProductId(productId));

        // 6. Product
        productRepository.delete(product);

        // 7. Remove media files from Contabo S3
        contaboObjectService.deleteFolder("products/" + productId);
    }

    /**
     * Delete a product's per-product spec groups/options (and their translations). Never touches the
     * shared global spec library. When {@code includeColorGroups} is false the synthetic {@code color_*}
     * group is preserved (it backs color media/variant options) — used when replacing specs on update.
     */
    private void deleteProductSpecs(UUID productId, boolean includeColorGroups) {
        List<ProductSpecGroup> groups = specGroupRepository.findByProduct_Id(productId).stream()
                .filter(g -> includeColorGroups || g.getCode() == null || !g.getCode().startsWith("color_"))
                .toList();
        for (ProductSpecGroup group : groups) {
            List<ProductSpecOption> options = specOptionRepository.findByGroup_Id(group.getId());
            for (ProductSpecOption option : options) {
                specOptionTranslationRepository.deleteAllInBatch(
                        specOptionTranslationRepository.findAllByOption_Id(option.getId()));
            }
            specOptionRepository.deleteAllInBatch(options);
            specGroupTranslationRepository.deleteAllInBatch(
                    specGroupTranslationRepository.findAllByGroup_Id(group.getId()));
        }
        specGroupRepository.deleteAllInBatch(groups);
    }

    public ResponseEntity<ApiResponse<ProductResponse>> getProductByIdAdmin(UUID id, String lang) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ApiResponse.success(toResponse(product, lang, true), "Product fetched successfully");
    }

    public ResponseEntity<ApiResponse<ProductResponse>> getProductByIdPublic(
            UUID id, String lang, String countryCode, String currency, Double lat, Double lng) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new ProductNotFoundException(id);
        }
        // Consumer channel: a B2B-only product (no active b2cEnabled store assignment) must not
        // be reachable by direct id/slug — otherwise its full detail (title/media/specs) leaks
        // even though its price is suppressed. The B2B catalog is served by the /b2b* endpoints.
        if (!storeProductRepository.existsB2cActiveByProductId(id)) {
            throw new ProductNotFoundException(id);
        }
        ProductResponse response = toResponse(product, lang, false);
        applyCountryPricing(response, product.getId(), countryCode, currency, lat, lng);
        applyDeliveryInfo(response, countryCode);
        applyRatingsSingle(response, product.getId());
        return ApiResponse.success(response, "Product fetched successfully");
    }

    /**
     * Resolve a product by slug regardless of which language that slug belongs to, then render
     * it in the requested language. Fixes "not found" when the URL keeps the source-language
     * slug after a language switch (slugs are per-language).
     */
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlugPublic(
            String slug, String lang, String countryCode, String currency, Double lat, Double lng) {
        UUID productId = translationRepository.findActiveBySlugAnyLang(slug).stream()
                .findFirst()
                .map(t -> t.getProduct().getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found for slug: " + slug));
        return getProductByIdPublic(productId, lang, countryCode, currency, lat, lng);
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getRelatedProducts(
            UUID productId, String lang, String countryCode, String currency, Double lat, Double lng) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // Get products from same category, exclude current product, keep only those
        // available (consumer channel) in the selected country — or globally B2C-visible
        // when no country — then limit to 4.
        List<Product> sameCategory = productRepository.findByStatusAndCategoryId("ACTIVE", product.getCategory().getId())
                .stream()
                .filter(p -> !p.getId().equals(productId))
                .toList();
        List<Product> related = ((countryCode != null && !countryCode.isBlank())
                ? filterToCountry(sameCategory, countryCode)
                : filterToB2cVisible(sameCategory))
                .stream().limit(4).toList();

        List<ProductResponse> responses = toResponseBatch(related, lang, false);
        
        applyBatchCountryPricing(responses, related, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Related products fetched successfully");
    }

    /**
     * Popular-for-you suggestions based on a list of product IDs (typically the
     * user's current cart). Aggregates ACTIVE products from the categories of
     * the supplied items, excludes the items themselves, and returns up to 8.
     */
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getPopularForYou(
            List<UUID> productIds, String lang, String countryCode, String currency, Double lat, Double lng) {
        if (productIds == null || productIds.isEmpty()) {
            return ApiResponse.success(List.of(), "No products supplied");
        }

        List<Product> seedProducts = productRepository.findAllById(productIds);
        Set<UUID> excludeIds = new java.util.HashSet<>(productIds);
        Set<UUID> categoryIds = seedProducts.stream()
                .map(p -> p.getCategory() != null ? p.getCategory().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (categoryIds.isEmpty()) {
            return ApiResponse.success(List.of(), "No category context");
        }

        java.util.LinkedHashMap<UUID, Product> aggregated = new java.util.LinkedHashMap<>();
        for (UUID categoryId : categoryIds) {
            for (Product p : productRepository.findByStatusAndCategoryId("ACTIVE", categoryId)) {
                if (excludeIds.contains(p.getId())) continue;
                aggregated.putIfAbsent(p.getId(), p);
                if (aggregated.size() >= 8) break;
            }
            if (aggregated.size() >= 8) break;
        }

        List<Product> candidates = new java.util.ArrayList<>(aggregated.values());
        List<Product> popular = (countryCode != null && !countryCode.isBlank())
                ? filterToCountry(candidates, countryCode)
                : filterToB2cVisible(candidates);
        List<ProductResponse> responses = toResponseBatch(popular, lang, false);
        applyBatchCountryPricing(responses, popular, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Popular for you fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProductsPublic(
            String lang, String countryCode, String currency, Double lat, Double lng, int page, int size, String sort) {
        List<Product> all = (countryCode != null && !countryCode.isBlank())
                ? storeProductRepository.findB2cActiveProductsByCountryCode(countryCode.toUpperCase())
                // No country: still exclude B2B-only products from the consumer catalog.
                : filterToB2cVisible(productRepository.findByStatus("ACTIVE"));

        // NEWEST can be ordered on the entity before paging (createdAt lives on Product);
        // price sorts need the resolved display price, so they're applied to the page below.
        if (sort != null && "NEWEST".equalsIgnoreCase(sort)) {
            all = all.stream()
                    .sorted(java.util.Comparator.comparing(Product::getCreatedAt,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                    .toList();
        } else {
            // Default order leads with the category this shop is actually known for, so the first
            // screen of "All products" is laptops rather than whatever the catalogue happened to
            // list first. Applied before paging, so it holds as the shopper scrolls, and only when
            // they have not chosen a sort of their own.
            all = leadWithPrimaryCategory(all);
        }

        // toResponse() is expensive PER product (per-product association queries), so
        // only map the requested page — never the whole catalog in one response. The ceiling
        // matches the admin path: an uncapped ?size= would let one request map everything.
        int pageSize = Math.min(100, Math.max(1, size));
        long skip = (long) Math.max(0, page) * pageSize;
        List<Product> products = all.stream().skip(skip).limit(pageSize).toList();

        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng);
        responses = applySort(responses, sort);
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    /**
     * The flash-sale rail: products whose store discount is LIVE and has an end in the future.
     *
     * <p>Note what this is NOT: {@code products.is_super_deal} is a manual editorial flag driving
     * the separate "Super Deals" rail and has no prices and no dates. This rail is derived entirely
     * from the discount windows on store_products, so an item leaves it the moment its sale ends,
     * with nothing to switch off by hand.
     *
     * <p>Ordered by the soonest end — a countdown is the point of the thing — and ordered AND PAGED in
     * the DATABASE. It used to select every matching product id in the shop and slice the page out in
     * Java, so a home screen asking for twelve items paid for a GROUP BY over the whole discounted
     * catalogue on the most-hit endpoint there is. The ids come back already sorted; products are
     * re-sorted into that order after loading because findAllById does not preserve it.
     *
     * <p>Then filtered by {@link #onlyOnFlashSale}, because "on sale in some store" and "the price on
     * this card is discounted" are different questions and the rail may only show the second. A page
     * can therefore come back shorter than {@code size}.
     */
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getFlashSaleProducts(
            String lang, String countryCode, String currency, Double lat, Double lng, int page, int size) {
        // ONE instant for the whole rail — the query that decides WHICH products are on sale and the
        // pricing that decides what each card SAYS must be judged against the same moment. Read twice,
        // a sale ending in between put a product in the rail and then priced it at full price.
        Instant now = Instant.now();
        int pageSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(Math.max(0, page), pageSize);
        List<Object[]> rows = (countryCode != null && !countryCode.isBlank())
                ? storeProductRepository.findB2cFlashSaleProductIdsByCountryCode(
                        countryCode.toUpperCase(), now, pageable)
                : storeProductRepository.findB2cFlashSaleProductIds(now, pageable);

        List<UUID> pageIds = rows.stream().map(row -> (UUID) row[0]).toList();
        if (pageIds.isEmpty()) {
            return ApiResponse.success(List.of(), "Flash sale products fetched successfully");
        }

        Map<UUID, Product> byId = productRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        List<Product> products = pageIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();

        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        // Prices (and with them onFlashSale / flashSaleEndsAt) resolve through exactly the same path
        // as every other list, so the rail cannot quote a price the product page disagrees with.
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng, true, now);
        return ApiResponse.success(onlyOnFlashSale(responses, now), "Flash sale products fetched successfully");
    }

    /**
     * Keeps only the products whose RESOLVED price is on a live flash sale.
     *
     * <p>The query behind the rail asks "is this product on sale in ANY consumer-visible store", and
     * that is a different question from the one the card answers. The price on the card comes from ONE
     * store option — the express one, else the cheapest the query found — and with no market selected
     * it comes from the globally cheapest store instead. Either can easily be a store that is not on
     * the sale, and then the rail lists a full-price product with no struck-through price, no badge
     * and no countdown, which is the feature visibly not working. With no country at all that was the
     * usual case, not the edge one.
     *
     * <p>So the rail is filtered by what the response actually says: {@code onFlashSale} is set by
     * applyPrimaryOption from the very option the price came from. A page may therefore come back
     * shorter than {@code size} — deliberately. A short honest rail is worth more than a full one
     * that quotes prices the shop is not discounting.
     *
     * <p>The flag alone was not enough, and three kinds of full-price card were still getting through
     * it. Each is now checked against the RESPONSE ITSELF rather than trusted from the flag, because
     * the flag is set several fields and one currency conversion earlier:
     *
     * <ol>
     *   <li><b>No price at all.</b> {@code onFlashSale} is set inside the discount branch while
     *       {@code storePrice} is set outside it, so a conversion that yields null (no FX rate for the
     *       requested display currency) left a badge and a countdown over a blank price.</li>
     *   <li><b>No saving in the currency the customer sees.</b> The discount test ran on the store's
     *       own figures; the card shows converted ones. Two prices a cent apart round to the same
     *       number at many rates, and the result is a strike-through and a countdown over an identical
     *       price — a full-price card wearing a sale badge. Requiring
     *       {@code originalPrice > storePrice} in the display currency is the honest test, and
     *       buildStoreOption now applies it at source too; this is the backstop for the two global
     *       fallback paths.</li>
     *   <li><b>A countdown that has already run out.</b> The rail query and the pricing pass each read
     *       their own clock, so a sale ending between the two produced an entry whose
     *       {@code flashSaleEndsAt} was in the past — a countdown starting at zero. The single
     *       {@code now} threaded from the caller closes that at source; this keeps it closed if a
     *       future caller forgets.</li>
     * </ol>
     */
    static List<ProductResponse> onlyOnFlashSale(List<ProductResponse> responses, Instant now) {
        return responses.stream()
                .filter(r -> Boolean.TRUE.equals(r.getOnFlashSale()))
                .filter(r -> r.getStorePrice() != null)
                .filter(r -> r.getOriginalPrice() != null
                        && r.getOriginalPrice().compareTo(r.getStorePrice()) > 0)
                .filter(r -> r.getFlashSaleEndsAt() != null && !r.getFlashSaleEndsAt().isBefore(now))
                .toList();
    }

    // ======================================================================
    // B2B browse (PUBLIC) — channel = B2B. Same DTO shape as the consumer
    // catalog, but sourced from b2bEnabled store products in B2B-enabled
    // countries and with NO buyable price (quoteOnly = true).
    // ======================================================================

    /**
     * Puts the primary category's products first, keeping every product and the order within each
     * group. Stable, so "featured" still means the catalogue's own order inside the two blocks.
     */
    private List<Product> leadWithPrimaryCategory(List<Product> products) {
        return leadWithCategories(products, primaryCategoryIds());
    }

    /** The pure half of {@link #leadWithPrimaryCategory}, so the ordering itself is testable. */
    static List<Product> leadWithCategories(List<Product> products, java.util.Set<UUID> lead) {
        if (lead == null || lead.isEmpty() || products == null || products.isEmpty()) {
            return products;
        }
        return products.stream()
                .sorted(java.util.Comparator.comparing(
                        (Product p) -> !(p.getCategory() != null && lead.contains(p.getCategory().getId()))))
                .toList();
    }

    /**
     * The primary category and everything under it, by its English name (configurable).
     *
     * <p>By name rather than a pasted id, so this survives a rebuilt category tree, and including
     * descendants so a future "Laptops > Gaming" still leads. Cached for a few minutes: it is read
     * on every catalogue page and the tree changes rarely.
     */
    private java.util.Set<UUID> primaryCategoryIds() {
        java.util.Set<UUID> cached = primaryCategoryIdsCache;
        if (cached != null && System.currentTimeMillis() - primaryCategoryIdsAt < 300_000L) {
            return cached;
        }
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        try {
            categoryTranslationRepository
                    .findFirstByLanguageIgnoreCaseAndNameIgnoreCase("EN", primaryCategoryName)
                    .ifPresent(translation -> {
                        UUID rootId = translation.getCategory().getId();
                        ids.add(rootId);
                        // Descendants, walked over the whole (small) tree rather than recursively
                        // querying: a category list is tens of rows, and this runs every 5 minutes.
                        List<com.buyology.ecommerce.product.domain.ProductCategory> allCategories =
                                categoryRepository.findAll();
                        boolean grew = true;
                        while (grew) {
                            grew = false;
                            for (var category : allCategories) {
                                if (category.getParent() != null
                                        && ids.contains(category.getParent().getId())
                                        && ids.add(category.getId())) {
                                    grew = true;
                                }
                            }
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("[CATALOG] Could not resolve the primary category '{}': {}",
                    primaryCategoryName, e.getMessage());
        }
        primaryCategoryIdsCache = ids;
        primaryCategoryIdsAt = System.currentTimeMillis();
        return ids;
    }

    /** Selects the B2B catalog candidate products (country-scoped when a country is supplied). */
    private List<Product> selectB2bProducts(String countryCode) {
        return (countryCode != null && !countryCode.isBlank())
                ? storeProductRepository.findB2bActiveProductsByCountryCode(countryCode.toUpperCase())
                : storeProductRepository.findB2bActiveProductsInB2bCountries();
    }

    /**
     * Resolves, for each product in the country-scoped B2B catalog, the id of the b2bEnabled
     * StoreProduct assignment the storefront must reference when adding it to the RFQ quote cart.
     * The B2B assignment query is ordered by store-product id, so keeping the first row per product
     * (merge keeps the existing/earlier one) picks a single deterministic assignment per product.
     */
    private java.util.Map<UUID, UUID> b2bStoreProductIdsByProduct(String countryCode) {
        List<StoreProduct> assignments = (countryCode != null && !countryCode.isBlank())
                ? storeProductRepository.findB2bActiveAssignmentsByCountryCode(countryCode.toUpperCase())
                : storeProductRepository.findB2bActiveAssignmentsInB2bCountries();
        java.util.Map<UUID, UUID> byProduct = new java.util.HashMap<>();
        for (StoreProduct sp : assignments) {
            byProduct.merge(sp.getProduct().getId(), sp.getId(), (existing, ignored) -> existing);
        }
        return byProduct;
    }

    /**
     * Marks a B2B batch as quote-only: suppresses every price/delivery/store field so the
     * storefront shows "Request a Quote" and can never derive a buyable price, while exposing the
     * backing b2bEnabled store-product id (storeProductId) needed to add the product to the RFQ
     * quote cart. Ratings are still populated (useful on B2B cards).
     */
    private void applyB2bQuoteOnly(List<ProductResponse> responses, List<Product> products,
            java.util.Map<UUID, UUID> storeProductIdsByProduct) {
        for (ProductResponse r : responses) {
            r.setQuoteOnly(true);
            r.setStoreId(null);
            r.setStorePrice(null);
            r.setOriginalPrice(null);
            r.setCurrency(null);
            r.setStoreOptions(null);
            r.setExpressDelivery(null);
            r.setFreeDelivery(null);
            r.setDeliveryFee(null);
            r.setStoreProductId(storeProductIdsByProduct.get(r.getId()));
            // variants[] carries no price — see VariantDto — so there is nothing buyable to clear there.
        }
        applyRatingsBatch(responses, products);
    }

    /** B2B list — mirrors {@link #getAllProductsPublic} params; channel = B2B (quoteOnly). */
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllB2bProductsPublic(
            String lang, String countryCode, String currency, Double lat, Double lng, int page, int size, String sort) {
        List<Product> all = selectB2bProducts(countryCode);

        if (sort != null && "NEWEST".equalsIgnoreCase(sort)) {
            all = all.stream()
                    .sorted(java.util.Comparator.comparing(Product::getCreatedAt,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                    .toList();
        }

        int pageSize = Math.max(1, size);
        long skip = (long) Math.max(0, page) * pageSize;
        List<Product> products = all.stream().skip(skip).limit(pageSize).toList();

        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyB2bQuoteOnly(responses, products, b2bStoreProductIdsByProduct(countryCode));
        // Price sorts are meaningless with no price; keep source order for POPULAR/PRICE_*,
        // honour NEWEST (already applied above).
        return ApiResponse.success(responses, "B2B products fetched successfully");
    }

    /** B2B search — mirrors {@link #searchProducts} params; channel = B2B (quoteOnly). */
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchB2bProducts(
            ProductFilterRequest filter, String lang, String countryCode, String currency, Double lat, Double lng) {
        // Price bounds are irrelevant on the B2B channel (no buyable price) — ignore them.
        filter.setMinPrice(null);
        filter.setMaxPrice(null);

        List<Product> matched = productRepository.findAll(ProductSpecification.from(filter)).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList();

        Set<UUID> b2bIds = selectB2bProducts(countryCode).stream()
                .map(Product::getId).collect(Collectors.toSet());
        List<Product> products = matched.stream()
                .filter(p -> b2bIds.contains(p.getId()))
                .toList();

        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyB2bQuoteOnly(responses, products, b2bStoreProductIdsByProduct(countryCode));
        return ApiResponse.success(responses, "B2B products fetched successfully");
    }

    /** B2B product detail by slug — quoteOnly, only when the product is B2B-available. */
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlugB2bPublic(
            String slug, String lang, String countryCode, String currency, Double lat, Double lng) {
        UUID productId = translationRepository.findActiveBySlugAnyLang(slug).stream()
                .findFirst()
                .map(t -> t.getProduct().getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found for slug: " + slug));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new ProductNotFoundException(productId);
        }
        // Must be part of the B2B catalog (b2bEnabled in a B2B-enabled country).
        boolean inB2bCatalog = selectB2bProducts(countryCode).stream()
                .anyMatch(p -> p.getId().equals(productId));
        if (!inB2bCatalog) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Product not available for B2B: " + slug);
        }

        ProductResponse response = toResponse(product, lang, false);
        List<ProductResponse> single = new java.util.ArrayList<>(List.of(response));
        applyB2bQuoteOnly(single, List.of(product), b2bStoreProductIdsByProduct(countryCode));
        return ApiResponse.success(response, "B2B product fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProducts(
            ProductFilterRequest filter, String lang, String countryCode, String currency, Double lat, Double lng) {
        // The price bounds arrive in the user's DISPLAY currency (matching the slider).
        // storePrice in store_products is in each store's NATIVE currency, and the same
        // product can be listed in several stores/countries — so an EXISTS predicate on
        // native storePrice can match a different store than the one whose converted
        // price we actually show, leaking out-of-range items. Instead, drop the DB-level
        // price predicate and filter on the resolved DISPLAY price after country pricing,
        // so the range matches exactly what the user sees.
        BigDecimal minPrice = filter.getMinPrice();
        BigDecimal maxPrice = filter.getMaxPrice();
        filter.setMinPrice(null);
        filter.setMaxPrice(null);

        List<Product> products = productRepository.findAll(ProductSpecification.from(filter)).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList();

        // If country filter is active, intersect with country-available products
        if (countryCode != null && !countryCode.isBlank()) {
            List<UUID> countryProductIds = storeProductRepository
                    .findB2cActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = products.stream()
                    .filter(p -> countryProductIds.contains(p.getId()))
                    .toList();
        } else {
            // No country: still exclude B2B-only products from the consumer catalog.
            products = filterToB2cVisible(products);
        }

        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng);
        responses = applyPriceRange(responses, minPrice, maxPrice);
        responses = applySort(responses, filter.getSort());
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    /**
     * Keeps only responses whose resolved DISPLAY price (storePrice, already converted
     * to the display currency) falls within [min, max]. Bounds are in the display
     * currency — the same currency the storefront slider uses — so the filter matches
     * exactly the price shown on each card. Products without a resolvable price are
     * dropped when a bound is set, since they can't be placed in a price range.
     */
    private List<ProductResponse> applyPriceRange(List<ProductResponse> responses,
            BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return responses;
        }
        return responses.stream()
                .filter(r -> {
                    BigDecimal price = r.getStorePrice();
                    if (price == null) {
                        return false;
                    }
                    if (min != null && price.compareTo(min) < 0) {
                        return false;
                    }
                    if (max != null && price.compareTo(max) > 0) {
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Resolves the DISPLAY price range — [min, max] of the storePrice the storefront
     * actually shows — across the active catalog (scoped to a country when given),
     * using the SAME country/global pricing + discount + currency conversion as the
     * search. This keeps the filter slider bounds in the display currency and exactly
     * aligned with what {@link #applyPriceRange} filters on (and what the cards show).
     * Prices are resolved on lightweight responses (no translations/specs/media).
     * Returns null when nothing in the catalog is priced.
     */
    public BigDecimal[] resolveDisplayPriceRange(String countryCode, String currency, Double lat, Double lng) {
        List<Product> products = productRepository.findByStatus("ACTIVE");

        if (countryCode != null && !countryCode.isBlank()) {
            Set<UUID> countryProductIds = new HashSet<>(storeProductRepository
                    .findB2cActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList());
            products = products.stream()
                    .filter(p -> countryProductIds.contains(p.getId()))
                    .toList();
        }
        if (products.isEmpty()) return null;

        // Price-only resolution (no ratings/delivery/DTO build) — same pricing path as the
        // search, so the bounds match exactly what the cards show and the search filters on.
        List<ProductResponse> responses = new java.util.ArrayList<>(products.size());
        for (int i = 0; i < products.size(); i++) responses.add(new ProductResponse());
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng, false);

        BigDecimal min = null;
        BigDecimal max = null;
        for (ProductResponse r : responses) {
            BigDecimal price = r.getStorePrice();
            if (price == null) continue;
            if (min == null || price.compareTo(min) < 0) min = price;
            if (max == null || price.compareTo(max) > 0) max = price;
        }
        return min == null ? null : new BigDecimal[]{ min, max };
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProductsElastic(
            String query, String lang, String countryCode, String currency, Double lat, Double lng) {
        List<com.buyology.ecommerce.product.search.domain.ProductDocument> searchResults;
        try {
            searchResults = productSearchService.search(query);
        } catch (Exception ex) {
            // Elasticsearch unreachable → fall back to the DB-backed search so search never 500s.
            log.warn("[SEARCH] Elasticsearch search failed ('{}'), falling back to DB search: {}",
                    query, ex.getMessage());
            com.buyology.ecommerce.product.dto.ProductFilterRequest filter =
                    new com.buyology.ecommerce.product.dto.ProductFilterRequest();
            filter.setQ(query);
            return searchProducts(filter, lang, countryCode, currency, lat, lng);
        }

        // The index first (it ranks, and it matches prefixes and typos), then the database's own
        // title/SKU match for anything the index has not caught up with. A product added, renamed
        // or re-activated since it was last indexed used to be invisible to search while sitting
        // on the shelf; the shop is small enough that asking twice costs nothing.
        java.util.LinkedHashSet<UUID> productIds = mergeKeepingOrder(
                searchResults.stream()
                        .map(com.buyology.ecommerce.product.search.domain.ProductDocument::getId)
                        .toList(),
                databaseMatchIds(query));

        if (productIds.isEmpty()) {
            return ApiResponse.success(List.of(), "No products found matching the query");
        }

        List<Product> products = productRepository.findAllById(productIds).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList();

        // Maintain order from search results
        Map<UUID, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        List<Product> ordered = productIds.stream()
                .map(productMap::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        // Consumer channel: exclude B2B-only products (country-scoped, or globally when no country).
        List<Product> orderedProducts = (countryCode != null && !countryCode.isBlank())
                ? filterToCountry(ordered, countryCode)
                : filterToB2cVisible(ordered);

        List<ProductResponse> responses = toResponseBatch(orderedProducts, lang, false);
        
        applyBatchCountryPricing(responses, orderedProducts, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Search results fetched successfully");
    }

    /** Ranked ids first, then anything the second list adds. Order is the ranking, so it is kept. */
    static java.util.LinkedHashSet<UUID> mergeKeepingOrder(List<UUID> ranked, List<UUID> extra) {
        java.util.LinkedHashSet<UUID> merged = new java.util.LinkedHashSet<>(ranked);
        merged.addAll(extra);
        return merged;
    }

    /**
     * Products whose title or SKU contains the words typed, straight from the database.
     *
     * <p>The recall net under the search index: it needs no index at all, so a stale or
     * half-rebuilt index cannot hide a product that is on sale. Capped, and never allowed to fail
     * the search — an empty list simply means the index result stands on its own.
     */
    private List<UUID> databaseMatchIds(String query) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        try {
            return productRepository
                    .searchAdmin(text, "ACTIVE", org.springframework.data.domain.PageRequest.of(0, 100))
                    .getContent().stream()
                    .map(Product::getId)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("[SEARCH] Database match failed for '{}': {}", text, e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> reindexElasticsearch() {
        List<Product> products = productRepository.findAll();
        // Clear index is handled by the force-reindex implementation we'll use
        // or just by the repository saveAll which overwrites if IDs match.
        // However, to be safe and clean up deleted products, a clear is better.
        // We'll update the search service to support a force reindex.
        productSearchService.forceReindex(
                products,
                product -> translationRepository.findByProductId(product.getId())
        );
        return ApiResponse.success(null, "Elasticsearch reindexing triggered successfully for " + products.size() + " products");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategoryPublic(
            UUID categoryId, String lang, String countryCode, String currency, Double lat, Double lng) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));

        List<Product> products;
        if (countryCode != null && !countryCode.isBlank()) {
            // Only products in the selected country that also match the category
            List<UUID> countryProductIds = storeProductRepository
                    .findB2cActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = productRepository.findByStatusAndCategoryId("ACTIVE", categoryId).stream()
                    .filter(p -> countryProductIds.contains(p.getId()))
                    .toList();
        } else {
            // No country: still exclude B2B-only products from the consumer catalog.
            products = filterToB2cVisible(productRepository.findByStatusAndCategoryId("ACTIVE", categoryId));
        }

        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getSuperDeals(
            String lang, String countryCode, String currency) {
        List<Product> products = productRepository.findByStatusAndIsSuperDeal("ACTIVE", true);
        if (countryCode != null && !countryCode.isBlank()) {
            List<UUID> countryProductIds = storeProductRepository
                    .findB2cActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = products.stream().filter(p -> countryProductIds.contains(p.getId())).toList();
        } else {
            products = filterToB2cVisible(products);
        }
        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyBatchCountryPricing(responses, products, countryCode, currency, null, null);
        return ApiResponse.success(responses, "Super deal products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getLimitedStockProducts(
            String lang, String countryCode, String currency) {
        List<Product> products = productRepository.findByStatusAndIsLimitedStock("ACTIVE", true);
        if (countryCode != null && !countryCode.isBlank()) {
            List<UUID> countryProductIds = storeProductRepository
                    .findB2cActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = products.stream().filter(p -> countryProductIds.contains(p.getId())).toList();
        } else {
            products = filterToB2cVisible(products);
        }
        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyBatchCountryPricing(responses, products, countryCode, currency, null, null);
        return ApiResponse.success(responses, "Limited stock products fetched successfully");
    }

    /**
     * Returns active products for the given IDs, mapped to the requested language.
     * Used by the quick-delivery flow to convert pre-filtered product IDs to full responses.
     */
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByIds(
            List<UUID> productIds, String lang, String countryCode, String currency) {
        if (productIds.isEmpty()) {
            return ApiResponse.success(List.of(), "No quick delivery products available in your area");
        }
        List<Product> products = filterToB2cVisible(productRepository.findAllById(productIds).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList());
        List<ProductResponse> responses = toResponseBatch(products, lang, false);
        applyBatchCountryPricing(responses, products, countryCode, currency, null, null);
        return ApiResponse.success(responses, "Quick delivery products fetched successfully");
    }

    // ========================
    // Private helpers
    // ========================

    private ProductResponse toResponse(Product product, String lang, boolean includeStatus) {
        List<ProductTranslation> all = translationRepository.findByProductId(product.getId());
        List<ProductTranslation> translations = all.stream()
                .filter(t -> t.getLanguage().equalsIgnoreCase(lang))
                .toList();
        // Fall back to EN (then any) so a product missing the requested-language
        // translation still renders instead of 404-ing the whole detail page.
        if (translations.isEmpty()) {
            translations = all.stream().filter(t -> "EN".equalsIgnoreCase(t.getLanguage())).toList();
        }
        if (translations.isEmpty()) {
            translations = all;
        }
        if (translations.isEmpty()) {
            throw new IllegalArgumentException("No translation found for product: " + product.getId());
        }

        // Product-level media (not linked to a color)
        List<ProductResponse.MediaDto> mediaDtos = mediaRepository
                .findByProductIdAndColorOptionIsNull(product.getId()).stream()
                .map(m -> toMediaDto(m))
                .toList();

        // Colors — each color option with its own media
        List<ProductMedia> allMedia = mediaRepository.findByProductId(product.getId());
        Map<UUID, List<ProductResponse.MediaDto>> colorMediaMap = new HashMap<>();
        for (ProductMedia m : allMedia) {
            if (m.getColorOption() != null) {
                colorMediaMap
                        .computeIfAbsent(m.getColorOption().getId(), k -> new ArrayList<>())
                        .add(toMediaDto(m));
            }
        }

        // Collect distinct color options from media
        List<ProductResponse.ColorOptionDto> colorDtos = allMedia.stream()
                .filter(m -> m.getColorOption() != null)
                .map(m -> m.getColorOption())
                .distinct()
                .map(opt -> new ProductResponse.ColorOptionDto(
                        opt.getId(),
                        opt.getValue(),
                        opt.getColorCode(),
                        colorMediaMap.getOrDefault(opt.getId(), List.of())))
                .toList();

        List<ProductResponse.VariantDto> variantDtos = variantRepository.findByProductId(product.getId()).stream()
                .map(v -> {
                    List<UUID> optionIds = variantOptionRepository.findByVariantId(v.getId()).stream()
                            .map(vo -> vo.getOption().getId())
                            .toList();
                    return new ProductResponse.VariantDto(v.getId(), v.getSku(), optionIds);
                })
                .toList();

        List<UUID> accessoryIds = accessoryRepository.findByProductId(product.getId()).stream()
                .map(a -> a.getAccessory().getId())
                .toList();

        List<ProductResponse.SpecGroupDto> specGroupDtos = buildSpecGroupDtos(product.getId(), lang);

        ProductTranslation translation = translations.get(0);
        return buildResponse(product, translation.getTitle(), translation.getDescription(), translation.getSlug(),
                mediaDtos, specGroupDtos, colorDtos, variantDtos, accessoryIds, includeStatus, lang);
    }

    /**
     * Batch equivalent of {@link #toResponse} for LISTS. Loads every association in a
     * handful of bulk queries (findByProductIdIn / findByGroup_IdIn …) instead of the
     * ~6+ per-product queries toResponse() does — turning an O(products) query
     * explosion into a constant ~9 queries. Products with no translation for the
     * requested language are skipped (a single bad product can't fail the whole list).
     */
    private List<ProductResponse> toResponseBatch(List<Product> products, String lang, boolean includeStatus) {
        if (products.isEmpty()) return List.of();
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Language language;
        try { language = Language.valueOf(lang.toUpperCase()); }
        catch (IllegalArgumentException e) { language = Language.EN; }

        // 1. Translations for the requested language → first per product
        Map<UUID, ProductTranslation> translationByProduct = new HashMap<>();
        for (ProductTranslation t : translationRepository.findByProductIdIn(productIds)) {
            if (t.getLanguage().equalsIgnoreCase(lang)) {
                translationByProduct.putIfAbsent(t.getProduct().getId(), t);
            }
        }

        // 2. Media (all) grouped by product
        Map<UUID, List<ProductMedia>> mediaByProduct = mediaRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()));

        // 3. Variants + their option ids
        List<ProductVariant> allVariants = variantRepository.findByProductIdIn(productIds);
        Map<UUID, List<UUID>> optionIdsByVariant = new HashMap<>();
        List<UUID> variantIds = allVariants.stream().map(ProductVariant::getId).toList();
        if (!variantIds.isEmpty()) {
            for (ProductVariantOption vo : variantOptionRepository.findByVariantIdIn(variantIds)) {
                optionIdsByVariant.computeIfAbsent(vo.getVariant().getId(), k -> new ArrayList<>())
                        .add(vo.getOption().getId());
            }
        }
        Map<UUID, List<ProductVariant>> variantsByProduct = allVariants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        // 4. Accessories
        Map<UUID, List<UUID>> accessoryIdsByProduct = new HashMap<>();
        for (ProductAccessory a : accessoryRepository.findByProductIdIn(productIds)) {
            accessoryIdsByProduct.computeIfAbsent(a.getProduct().getId(), k -> new ArrayList<>())
                    .add(a.getAccessory().getId());
        }

        // 5. Spec groups (non-color, with a global ref) + their localized names
        List<ProductSpecGroup> allGroups = specGroupRepository.findByProduct_IdIn(productIds).stream()
                .filter(g -> !g.getCode().startsWith("color_") && g.getGlobalSpecGroup() != null)
                .toList();
        List<UUID> groupIds = allGroups.stream().map(ProductSpecGroup::getId).toList();
        List<UUID> globalGroupIds = allGroups.stream().map(g -> g.getGlobalSpecGroup().getId()).distinct().toList();
        Map<UUID, String> groupNameByGlobalId = new HashMap<>();
        if (!globalGroupIds.isEmpty()) {
            for (var gt : globalSpecGroupTranslationRepository.findByGroup_IdInAndLanguageIgnoreCase(globalGroupIds, lang)) {
                groupNameByGlobalId.putIfAbsent(gt.getGroup().getId(), gt.getName());
            }
        }

        // 6. Spec options (with a global ref) grouped by group + their localized values
        List<ProductSpecOption> allOptions = groupIds.isEmpty() ? List.of()
                : specOptionRepository.findByGroup_IdIn(groupIds).stream()
                        .filter(o -> o.getGlobalSpecOption() != null)
                        .toList();
        List<UUID> globalOptionIds = allOptions.stream().map(o -> o.getGlobalSpecOption().getId()).distinct().toList();
        Map<UUID, String> optionValueByGlobalId = new HashMap<>();
        Map<UUID, com.buyology.ecommerce.common.enums.SpecUnit> optionUnitByGlobalId = new HashMap<>();
        if (!globalOptionIds.isEmpty()) {
            for (var ot : globalSpecOptionTranslationRepository.findByOption_IdInAndLanguage(globalOptionIds, language)) {
                optionValueByGlobalId.putIfAbsent(ot.getOption().getId(), ot.getValue());
            }
            // The unit is batch-loaded here rather than read off the lazy proxy during response
            // assembly. Reading it there worked only while open-session-in-view held a session open
            // for the whole request; with that off it threw LazyInitializationException and took the
            // entire product listing down. Batching is also simply better — it is one query for the
            // page instead of one per option.
            for (var g : globalSpecOptionRepository.findAllById(globalOptionIds)) {
                if (g.getUnit() != null) optionUnitByGlobalId.put(g.getId(), g.getUnit());
            }
        }
        Map<UUID, List<ProductSpecOption>> optionsByGroup = allOptions.stream()
                .collect(Collectors.groupingBy(o -> o.getGroup().getId()));
        Map<UUID, List<ProductSpecGroup>> groupsByProduct = allGroups.stream()
                .collect(Collectors.groupingBy(g -> g.getProduct().getId()));

        // Assemble each product from the preloaded maps (no further queries)
        List<ProductResponse> result = new ArrayList<>(products.size());
        for (Product product : products) {
            UUID pid = product.getId();
            ProductTranslation translation = translationByProduct.get(pid);
            if (translation == null) continue;

            List<ProductMedia> media = mediaByProduct.getOrDefault(pid, List.of());
            List<ProductResponse.MediaDto> mediaDtos = media.stream()
                    .filter(m -> m.getColorOption() == null).map(this::toMediaDto).toList();

            Map<UUID, List<ProductResponse.MediaDto>> colorMediaMap = new HashMap<>();
            for (ProductMedia m : media) {
                if (m.getColorOption() != null) {
                    colorMediaMap.computeIfAbsent(m.getColorOption().getId(), k -> new ArrayList<>()).add(toMediaDto(m));
                }
            }
            List<ProductResponse.ColorOptionDto> colorDtos = media.stream()
                    .filter(m -> m.getColorOption() != null).map(ProductMedia::getColorOption).distinct()
                    .map(opt -> new ProductResponse.ColorOptionDto(opt.getId(), opt.getValue(), opt.getColorCode(),
                            colorMediaMap.getOrDefault(opt.getId(), List.of())))
                    .toList();

            List<ProductResponse.VariantDto> variantDtos = variantsByProduct.getOrDefault(pid, List.of()).stream()
                    .map(v -> new ProductResponse.VariantDto(v.getId(), v.getSku(),
                            optionIdsByVariant.getOrDefault(v.getId(), List.of())))
                    .toList();

            List<UUID> accessoryIds = accessoryIdsByProduct.getOrDefault(pid, List.of());

            List<ProductResponse.SpecGroupDto> specGroupDtos = groupsByProduct.getOrDefault(pid, List.of()).stream()
                    .map(group -> {
                        String groupName = groupNameByGlobalId.getOrDefault(group.getGlobalSpecGroup().getId(), group.getCode());
                        List<ProductResponse.SpecOptionDto> optionDtos = optionsByGroup.getOrDefault(group.getId(), List.of()).stream()
                                .map(opt -> new ProductResponse.SpecOptionDto(opt.getId(),
                                        optionValueByGlobalId.getOrDefault(opt.getGlobalSpecOption().getId(), opt.getValue()),
                                        // From the batch map, never the proxy: getId() is free on a
                                        // proxy, getUnit() is not — it initialises it.
                                        optionUnitByGlobalId.get(opt.getGlobalSpecOption().getId())))
                                .toList();
                        return new ProductResponse.SpecGroupDto(group.getId(), group.getCode(), groupName, optionDtos);
                    })
                    .toList();

            result.add(buildResponse(product, translation.getTitle(), translation.getDescription(), translation.getSlug(),
                    mediaDtos, specGroupDtos, colorDtos, variantDtos, accessoryIds, includeStatus, lang));
        }
        return result;
    }

    private List<ProductResponse.SpecGroupDto> buildSpecGroupDtos(UUID productId, String lang) {
        Language language;
        try {
            language = Language.valueOf(lang.toUpperCase());
        } catch (IllegalArgumentException e) {
            language = Language.EN;
        }

        List<ProductSpecGroup> groups = specGroupRepository.findByProduct_Id(productId).stream()
                .filter(g -> !g.getCode().startsWith("color_"))
                .toList();

        List<ProductResponse.SpecGroupDto> groupDtos = new ArrayList<>();
        for (ProductSpecGroup group : groups) {
            // Skip orphaned spec groups that lost their global spec reference
            if (group.getGlobalSpecGroup() == null) {
                continue;
            }

            // Read group name from global spec translations
            UUID globalGroupId = group.getGlobalSpecGroup().getId();
            String groupName = globalSpecGroupTranslationRepository
                    .findByGroup_IdAndLanguageIgnoreCase(globalGroupId, lang)
                    .map(t -> t.getName())
                    .orElse(group.getCode());

            Language finalLanguage = language;
            List<ProductResponse.SpecOptionDto> optionDtos = specOptionRepository.findByGroup_Id(group.getId()).stream()
                    .filter(opt -> opt.getGlobalSpecOption() != null)
                    .map(opt -> {
                        // Read option value and unit from global spec translations
                        UUID globalOptionId = opt.getGlobalSpecOption().getId();
                        String optValue = globalSpecOptionTranslationRepository
                                .findByOption_IdAndLanguage(globalOptionId, finalLanguage)
                                .map(t -> t.getValue())
                                .orElse(opt.getValue());
                        // Fetched by id rather than read off the proxy. getId() is free on a lazy
                        // proxy; getUnit() initialises it, which throws once the session has closed.
                        SpecUnit unit = globalSpecOptionRepository.findById(globalOptionId)
                                .map(g -> g.getUnit())
                                .orElse(null);
                        return new ProductResponse.SpecOptionDto(opt.getId(), optValue, unit);
                    })
                    .toList();

            groupDtos.add(new ProductResponse.SpecGroupDto(group.getId(), group.getCode(), groupName, optionDtos));
        }
        return groupDtos;
    }

    /** Throw if another non-deleted product already uses this name in this language (case-insensitive). */
    private void assertTitleAvailable(String language, String title, UUID excludeProductId) {
        if (title == null || title.isBlank()) {
            return;
        }
        if (translationRepository.existsActiveByLanguageAndTitleIgnoreCase(language, title.trim(), excludeProductId)) {
            throw new IllegalArgumentException("A product with the name '" + title.trim() + "' already exists");
        }
    }

    private List<ProductTranslation> saveTranslations(Product product, ProductTranslationRequest tr) {
        String slugAz = SlugUtils.toSlug(tr.getTitleAz());
        String slugEn = SlugUtils.toSlug(tr.getTitleEn());
        String slugAr = SlugUtils.toSlug(tr.getTitleAr());

        // Reject duplicate product names (case-insensitive, per language) up front.
        assertTitleAvailable("AZ", tr.getTitleAz(), product.getId());
        assertTitleAvailable("EN", tr.getTitleEn(), product.getId());
        assertTitleAvailable("AR", tr.getTitleAr(), product.getId());

        // Reject if an active product already uses the same name/slug
        if (translationRepository.existsActiveByLanguageAndSlug("AZ", slugAz)) {
            throw new IllegalArgumentException("A product with the name '" + tr.getTitleAz() + "' already exists");
        }
        if (translationRepository.existsActiveByLanguageAndSlug("EN", slugEn)) {
            throw new IllegalArgumentException("A product with the name '" + tr.getTitleEn() + "' already exists");
        }
        if (translationRepository.existsActiveByLanguageAndSlug("AR", slugAr)) {
            throw new IllegalArgumentException("A product with the name '" + tr.getTitleAr() + "' already exists");
        }

        // If a deleted product holds the same slug, free it up before inserting.
        // flush() is required to push the UPDATEs to the DB immediately so the
        // unique constraint is released before the INSERTs below run.
        freeDeletedSlug("AZ", slugAz);
        freeDeletedSlug("EN", slugEn);
        freeDeletedSlug("AR", slugAr);
        translationRepository.flush();

        // Strip HTML from supplier/admin-supplied product copy (stored-XSS defense).
        List<ProductTranslation> translations = new ArrayList<>();
        translations.add(new ProductTranslation(product, "AZ",
                HtmlSanitizer.stripHtml(tr.getTitleAz()), HtmlSanitizer.stripHtml(tr.getDescriptionAz()), slugAz));
        translations.add(new ProductTranslation(product, "EN",
                HtmlSanitizer.stripHtml(tr.getTitleEn()), HtmlSanitizer.stripHtml(tr.getDescriptionEn()), slugEn));
        translations.add(new ProductTranslation(product, "AR",
                HtmlSanitizer.stripHtml(tr.getTitleAr()), HtmlSanitizer.stripHtml(tr.getDescriptionAr()), slugAr));
        return translationRepository.saveAll(translations);
    }

    private void freeDeletedSlug(String language, String slug) {
        translationRepository.findByLanguageAndSlug(language, slug).ifPresent(existing -> {
            existing.setSlug(slug + "-" + existing.getProduct().getId().toString().replace("-", "").substring(0, 8));
            translationRepository.save(existing);
        });
    }

    /**
     * Creates spec groups and their options, resolving each against the global spec library.
     * Each spec group can be referenced by globalSpecGroupId (existing) or defined inline
     * (code + name translations) — the latter will find-or-create the global spec group.
     * Each spec option can be referenced by globalOptionId (existing) or defined inline
     * (value translations + optional unit) — the latter always creates a new global spec option.
     * Spec options are DESCRIPTIVE only and carry no price of their own — ProductSpecOption has
     * value/unit/colorCode and nothing else. An "additionalPrice" was documented for years and both
     * clients implemented DisplayedPrice = BasePrice + additionalPrice against it; the field has never
     * existed, so the term was always +0.
     *
     * <p>Nor is a VARIANT the answer today, which is what this comment used to claim: a variant
     * identifies which SKU is shipped and caps its stock, and {@code variants[].storePrice} does not
     * exist either — VariantDto carries no price, and {@code store_product_variants.store_price} is
     * never consulted for money. There is exactly ONE price per listing, the store product's, and every
     * surface in the shop quotes it (see {@code CartLinePricing} for why a variantId must not decide a
     * price). So a spec option that genuinely costs more money cannot be priced as one today: it needs
     * its own product/listing until per-variant pricing is built, which is a backend change plus both
     * clients. Saying otherwise here is worse than saying nothing — it is what both clients built
     * against.
     */
    private void saveSpecs(
            Product product,
            List<CreateSpecGroupRequest> specRequests,
            Map<String, ProductSpecOption> localKeyToOption) {

        if (specRequests == null || specRequests.isEmpty()) {
            return;
        }

        for (CreateSpecGroupRequest groupReq : specRequests) {
            GlobalSpecGroup globalGroup = resolveOrCreateGlobalSpecGroup(groupReq);

            ProductSpecGroup group = specGroupRepository.save(
                    new ProductSpecGroup(product, globalGroup, globalGroup.getCode()));

            for (CreateSpecOptionRequest optReq : groupReq.getOptions()) {
                if (optReq.getLocalKey() != null && localKeyToOption.containsKey(optReq.getLocalKey())) {
                    throw new IllegalArgumentException("Duplicate localKey in specs: " + optReq.getLocalKey());
                }

                GlobalSpecOption globalOption = resolveOrCreateGlobalSpecOption(optReq, globalGroup);

                // Cache EN value for the denormalized value column
                String valueEn = globalSpecOptionTranslationRepository
                        .findByOption_IdAndLanguage(globalOption.getId(), Language.EN)
                        .map(t -> t.getValue())
                        .orElse(globalOption.getId().toString());

                ProductSpecOption option = specOptionRepository.save(
                        new ProductSpecOption(group, globalOption, valueEn, globalOption.getUnit()));

                if (optReq.getLocalKey() != null) {
                    localKeyToOption.put(optReq.getLocalKey(), option);
                }
            }
        }
    }

    /**
     * If globalSpecGroupId is provided, looks up the existing global spec group.
     * Otherwise, requires code + name translations and finds or creates the global spec group by code.
     */
    private GlobalSpecGroup resolveOrCreateGlobalSpecGroup(CreateSpecGroupRequest req) {
        if (req.getGlobalSpecGroupId() != null) {
            return globalSpecGroupRepository.findById(req.getGlobalSpecGroupId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Global spec group not found: " + req.getGlobalSpecGroupId()));
        }

        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new IllegalArgumentException(
                    "Either globalSpecGroupId or code (+ nameAz/nameEn/nameAr) must be provided for each spec group");
        }
        if (req.getNameAz() == null || req.getNameAz().isBlank()
                || req.getNameEn() == null || req.getNameEn().isBlank()
                || req.getNameAr() == null || req.getNameAr().isBlank()) {
            throw new IllegalArgumentException(
                    "nameAz, nameEn, and nameAr are required when creating a new global spec group (code: " + req.getCode() + ")");
        }

        Optional<GlobalSpecGroup> existing = globalSpecGroupRepository.findByCode(req.getCode());
        if (existing.isPresent()) {
            return existing.get();
        }

        GlobalSpecGroup newGroup = globalSpecGroupRepository.save(new GlobalSpecGroup(req.getCode()));
        globalSpecGroupTranslationRepository.saveAll(List.of(
                new GlobalSpecGroupTranslation(newGroup, "AZ", req.getNameAz()),
                new GlobalSpecGroupTranslation(newGroup, "EN", req.getNameEn()),
                new GlobalSpecGroupTranslation(newGroup, "AR", req.getNameAr())));
        return newGroup;
    }

    /**
     * If globalOptionId is provided, looks up the existing global spec option and verifies
     * it belongs to the given group. Otherwise, requires value translations and creates
     * a new global spec option in the given group.
     */
    private GlobalSpecOption resolveOrCreateGlobalSpecOption(CreateSpecOptionRequest req, GlobalSpecGroup globalGroup) {
        if (req.getGlobalOptionId() != null) {
            GlobalSpecOption existing = globalSpecOptionRepository.findById(req.getGlobalOptionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Global spec option not found: " + req.getGlobalOptionId()));
            if (!existing.getGroup().getId().equals(globalGroup.getId())) {
                throw new IllegalArgumentException(
                        "Global spec option " + req.getGlobalOptionId() + " does not belong to group " + globalGroup.getId());
            }
            return existing;
        }

        if (req.getValueAz() == null || req.getValueAz().isBlank()
                || req.getValueEn() == null || req.getValueEn().isBlank()
                || req.getValueAr() == null || req.getValueAr().isBlank()) {
            throw new IllegalArgumentException(
                    "Either globalOptionId or valueAz/valueEn/valueAr must be provided for each spec option (localKey: " + req.getLocalKey() + ")");
        }

        GlobalSpecOption newOption = globalSpecOptionRepository.save(new GlobalSpecOption(globalGroup, req.getUnit()));
        globalSpecOptionTranslationRepository.saveAll(List.of(
                new GlobalSpecOptionTranslation(newOption, Language.AZ, req.getValueAz()),
                new GlobalSpecOptionTranslation(newOption, Language.EN, req.getValueEn()),
                new GlobalSpecOptionTranslation(newOption, Language.AR, req.getValueAr())));
        return newOption;
    }

    /**
     * Creates a "color" spec group, saves each color as a spec option,
     * and assigns the media files referenced by mediaIndices to that color.
     * Claimed file indices are added to claimedMediaIndices so product-level
     * media saving can skip them.
     */
    private List<ProductResponse.ColorOptionDto> saveColors(
            Product product,
            List<CreateColorRequest> colorRequests,
            List<MultipartFile> mediaFiles,
            Map<String, ProductSpecOption> localKeyToOption,
            Set<Integer> claimedMediaIndices) {

        if (colorRequests == null || colorRequests.isEmpty()) {
            return List.of();
        }

        // Create a shared "color" spec group for this product's colors
        ProductSpecGroup colorGroup = specGroupRepository.save(new ProductSpecGroup(product, "color_" + product.getId()));
        specGroupTranslationRepository.saveAll(List.of(
                new ProductSpecGroupTranslation(colorGroup, "AZ", "Rəng"),
                new ProductSpecGroupTranslation(colorGroup, "EN", "Color"),
                new ProductSpecGroupTranslation(colorGroup, "AR", "اللون")
        ));

        List<ProductResponse.ColorOptionDto> colorDtos = new ArrayList<>();

        for (CreateColorRequest colorReq : colorRequests) {
            if (colorReq.getLocalKey() != null && localKeyToOption.containsKey(colorReq.getLocalKey())) {
                throw new IllegalArgumentException("Duplicate localKey in colors: " + colorReq.getLocalKey());
            }

            // Save the color as a spec option (colors have no price — price comes from variants)
            ProductSpecOption colorOption = specOptionRepository.save(
                    new ProductSpecOption(colorGroup, colorReq.getValueEn(), colorReq.getColorCode()));

            specOptionTranslationRepository.saveAll(List.of(
                    new ProductSpecOptionTranslation(colorOption, Language.AZ, colorReq.getValueAz()),
                    new ProductSpecOptionTranslation(colorOption, Language.EN, colorReq.getValueEn()),
                    new ProductSpecOptionTranslation(colorOption, Language.AR, colorReq.getValueAr())
            ));

            if (colorReq.getLocalKey() != null) {
                localKeyToOption.put(colorReq.getLocalKey(), colorOption);
            }

            // Save media files claimed by this color
            List<ProductResponse.MediaDto> colorMediaDtos = new ArrayList<>();
            if (colorReq.getMediaIndices() != null && mediaFiles != null) {
                for (int i = 0; i < colorReq.getMediaIndices().size(); i++) {
                    int fileIndex = colorReq.getMediaIndices().get(i);
                    if (fileIndex < 0 || fileIndex >= mediaFiles.size()) {
                        throw new IllegalArgumentException(
                                "mediaIndex " + fileIndex + " is out of range for color: " + colorReq.getValueEn());
                    }
                    if (claimedMediaIndices.contains(fileIndex)) {
                        throw new IllegalArgumentException(
                                "mediaIndex " + fileIndex + " is already claimed by another color");
                    }
                    claimedMediaIndices.add(fileIndex);

                    MultipartFile file = mediaFiles.get(fileIndex);
                    String url = uploadToContabo(product.getId(), file, "color_" + colorOption.getId() + "_" + i);
                    boolean isPrimary = (i == 0);
                    ProductMedia media = mediaRepository.save(new ProductMedia(
                            product, colorOption, resolveMediaType(file.getContentType()), url, null, isPrimary, i));
                    colorMediaDtos.add(toMediaDto(media));
                }
            }

            colorDtos.add(new ProductResponse.ColorOptionDto(
                    colorOption.getId(), colorOption.getValue(), colorOption.getColorCode(), colorMediaDtos));
        }

        return colorDtos;
    }

    /**
     * Saves media files that were NOT claimed by any color as product-level media.
     */
    private List<ProductResponse.MediaDto> saveProductMedia(
            Product product,
            List<MultipartFile> mediaFiles,
            Set<Integer> claimedMediaIndices) {

        if (mediaFiles == null || mediaFiles.isEmpty()) {
            return List.of();
        }

        List<ProductMedia> mediaEntities = new ArrayList<>();
        int orderIndex = 0;
        for (int i = 0; i < mediaFiles.size(); i++) {
            if (claimedMediaIndices.contains(i)) {
                continue; // skip files already owned by a color
            }
            MultipartFile file = mediaFiles.get(i);
            String url = uploadToContabo(product.getId(), file, "product_" + orderIndex);
            boolean isPrimary = (orderIndex == 0);
            mediaEntities.add(new ProductMedia(product, resolveMediaType(file.getContentType()), url, null, isPrimary, orderIndex));
            orderIndex++;
        }

        return mediaRepository.saveAll(mediaEntities).stream()
                .map(m -> toMediaDto(m))
                .toList();
    }

    private List<ProductResponse.VariantDto> saveVariants(
            Product product,
            List<CreateVariantRequest> variantRequests,
            Map<String, ProductSpecOption> localKeyToOption) {

        if (variantRequests == null || variantRequests.isEmpty()) {
            return List.of();
        }

        List<ProductResponse.VariantDto> variantDtos = new ArrayList<>();

        for (CreateVariantRequest variantReq : variantRequests) {
            ProductVariant variant = new ProductVariant(product, variantReq.getSku());
            ProductVariant savedVariant = variantRepository.save(variant);

            List<UUID> linkedOptionIds = new ArrayList<>();

            if (variantReq.getSpecOptionIds() != null) {
                for (UUID optionId : variantReq.getSpecOptionIds()) {
                    ProductSpecOption option = specOptionRepository.findById(optionId)
                            .orElseThrow(() -> new IllegalArgumentException("Spec option not found with id: " + optionId));
                    variantOptionRepository.save(new ProductVariantOption(savedVariant, option));
                    linkedOptionIds.add(optionId);
                }
            }

            if (variantReq.getSpecOptionLocalKeys() != null) {
                for (String localKey : variantReq.getSpecOptionLocalKeys()) {
                    ProductSpecOption option = localKeyToOption.get(localKey);
                    if (option == null) {
                        throw new IllegalArgumentException("No spec option found for localKey: " + localKey);
                    }
                    variantOptionRepository.save(new ProductVariantOption(savedVariant, option));
                    linkedOptionIds.add(option.getId());
                }
            }

            variantDtos.add(new ProductResponse.VariantDto(
                    savedVariant.getId(), savedVariant.getSku(), linkedOptionIds));
        }

        return variantDtos;
    }

    private List<UUID> saveAccessories(Product product, List<UUID> accessoryIds) {
        if (accessoryIds == null || accessoryIds.isEmpty()) {
            return List.of();
        }
        List<UUID> resolvedIds = new ArrayList<>();
        for (UUID accessoryId : accessoryIds) {
            if (accessoryId.equals(product.getId())) {
                throw new IllegalArgumentException("A product cannot be linked as its own accessory");
            }
            Product accessory = productRepository.findById(accessoryId)
                    .orElseThrow(() -> new ProductNotFoundException(accessoryId));
            accessoryRepository.save(new ProductAccessory(product, accessory));
            resolvedIds.add(accessoryId);
        }
        return resolvedIds;
    }

    private String generateSku(Product.ProductType productType) {
        String prefix = (productType == Product.ProductType.ACCESSORY) ? "DTAX" : "DTDX";
        String sku;
        do {
            int digits = ThreadLocalRandom.current().nextInt(100000, 1000000);
            sku = prefix + "-" + digits;
        } while (productRepository.existsBySku(sku));
        return sku;
    }

    private String uploadToContabo(UUID productId, MultipartFile file, String baseName) {
        FileValidationUtils.validateImage(file);
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = baseName + extension;
        String key = "products/" + productId + "/" + fileName;
        return contaboObjectService.uploadFile(key, file);
    }

    private ProductMedia.MediaType resolveMediaType(String contentType) {
        if (contentType != null && contentType.startsWith("video/")) {
            return ProductMedia.MediaType.VIDEO;
        }
        return ProductMedia.MediaType.IMAGE;
    }

    private ProductResponse.MediaDto toMediaDto(ProductMedia m) {
        String presignedUrl = contaboObjectService.getPresignedUrl(m.getUrl());
        String presignedThumbnailUrl = contaboObjectService.getPresignedUrl(m.getThumbnailUrl());

        return new ProductResponse.MediaDto(
                m.getId(), m.getMediaType().name(), presignedUrl,
                presignedThumbnailUrl, m.getIsPrimary(), m.getOrderIndex());
    }

    /**
     * Looks up the store price for a single product in the given country and applies
     * live currency conversion to the requested display currency.
     * No-ops when countryCode is null/blank.
     */
    private static final double EXPRESS_RADIUS_KM = 12.5;

    // Delivery display values come from DeliveryFeePolicy, the same bean the cart and the order use.
    // This used to be its own pair of constants "mirroring OrderService" — a third copy of the fee,
    // aligned by comment. Once 30-minute and standard delivery were priced differently, that would
    // have advertised one fee on the product page and charged another at checkout.

    /**
     * Populates freeDelivery + deliveryFee on a priced response. Call after storePrice +
     * currency are set (works for both the single and batch pricing paths).
     */
    private void applyDeliveryInfo(ProductResponse resp, String countryCode) {
        BigDecimal price = resp.getStorePrice();
        String ccy = resp.getCurrency();
        if (price == null || ccy == null) return;
        BigDecimal priceAed = "AED".equalsIgnoreCase(ccy)
                ? price
                : currencyExchangeService.convert(price, ccy, "AED");
        // Standard-delivery rate for this country — the same figure the cart will show. A product
        // page cannot know the delivery address, so it cannot know whether the order will resolve to
        // 30-minute delivery; that is settled at checkout, where the customer sees the final total.
        BigDecimal feeAed = deliveryFeePolicy.cartPreviewFeeAed(countryCode, priceAed);
        if (feeAed.signum() == 0) {
            resp.setFreeDelivery(true);
            resp.setDeliveryFee(BigDecimal.ZERO);
        } else {
            resp.setFreeDelivery(false);
            resp.setDeliveryFee("AED".equalsIgnoreCase(ccy)
                    ? feeAed
                    : currencyExchangeService.convert(feeAed, "AED", ccy));
        }

        // When this product IS within a store's 30-minute radius, the badge promising 30-minute
        // delivery and the fee beside it used to disagree: the badge offered EXPRESS while the fee
        // was the REGULAR rate, and checkout then charged the express rate. Quote the express fee
        // too, so the number shown next to the badge is the number that will be charged. Free
        // delivery applies to express exactly as it does to standard, hence the same zero check.
        if (Boolean.TRUE.equals(resp.getExpressDelivery())) {
            BigDecimal expressAed = deliveryFeePolicy.expressFeeAedForSubtotal(priceAed);
            resp.setExpressDeliveryFee("AED".equalsIgnoreCase(ccy)
                    ? expressAed
                    : currencyExchangeService.convert(expressAed, "AED", ccy));
        }
    }

    /**
     * Drops products not stocked by any store in {@code countryCode}. No-op when no
     * country is supplied. Used so country-scoped lists never surface "browse only" items.
     * Consumer path only: uses the B2C-scoped query so B2B-only assignments are excluded.
     */
    private List<Product> filterToCountry(List<Product> products, String countryCode) {
        if (countryCode == null || countryCode.isBlank() || products.isEmpty()) return products;
        Set<UUID> countryIds = storeProductRepository
                .findB2cActiveProductsByCountryCode(countryCode.toUpperCase())
                .stream().map(Product::getId).collect(Collectors.toSet());
        return products.stream().filter(p -> countryIds.contains(p.getId())).toList();
    }

    /**
     * Restricts a candidate list to products that are consumer-visible (b2cEnabled) in at
     * least one active store, for the GLOBAL (no country) consumer path. Keeps B2B-only
     * products out of the consumer catalog even when no country is selected.
     */
    private List<Product> filterToB2cVisible(List<Product> products) {
        if (products.isEmpty()) return products;
        Set<UUID> b2cIds = new HashSet<>(storeProductRepository.findB2cActiveProductIds());
        return products.stream().filter(p -> b2cIds.contains(p.getId())).toList();
    }

    /**
     * Sorts mapped+priced responses. POPULAR / unknown / null keep the source order.
     * Price sorts use the already-resolved display price, so call AFTER pricing.
     */
    private List<ProductResponse> applySort(List<ProductResponse> responses, String sort) {
        if (sort == null || sort.isBlank()) return responses;
        java.util.Comparator<ProductResponse> cmp = switch (sort.toUpperCase()) {
            case "NEWEST" -> java.util.Comparator.comparing(ProductResponse::getCreatedAt,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
            case "PRICE_ASC" -> java.util.Comparator.comparing(ProductResponse::getStorePrice,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            case "PRICE_DESC" -> java.util.Comparator.comparing(ProductResponse::getStorePrice,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
            default -> null;
        };
        if (cmp == null) return responses;
        return responses.stream().sorted(cmp).toList();
    }

    /** Populates averageRating + totalReviews for a list, from pre-aggregated review stats. */
    private void applyRatingsBatch(List<ProductResponse> responses, List<Product> products) {
        if (products.isEmpty()) return;
        List<UUID> ids = products.stream().map(Product::getId).toList();
        Map<UUID, com.buyology.ecommerce.review.domain.ProductReviewStats> byId =
                productReviewStatsRepository.findByProductIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                com.buyology.ecommerce.review.domain.ProductReviewStats::getProductId,
                                s -> s, (a, b) -> a));
        for (int i = 0; i < responses.size(); i++) {
            var stats = byId.get(products.get(i).getId());
            responses.get(i).setAverageRating(stats != null ? stats.getAverageRating() : BigDecimal.ZERO);
            responses.get(i).setTotalReviews(stats != null ? stats.getTotalReviews() : 0);
        }
    }

    /** Populates averageRating + totalReviews for a single product. */
    private void applyRatingsSingle(ProductResponse resp, UUID productId) {
        var stats = productReviewStatsRepository.findByProductId(productId).orElse(null);
        resp.setAverageRating(stats != null ? stats.getAverageRating() : BigDecimal.ZERO);
        resp.setTotalReviews(stats != null ? stats.getTotalReviews() : 0);
    }

    private void applyCountryPricing(ProductResponse response, UUID productId,
                                     String countryCode, String displayCurrency,
                                     Double lat, Double lng) {
        // ONE instant for the whole response. Discounts now expire (V60), and Instant.now() read
        // per option would let two stores of the same product be judged against different clocks —
        // one showing a sale price the other has just stopped showing.
        Instant now = Instant.now();

        if (countryCode == null || countryCode.isBlank()) {
            // No country code provided - try to find global cheapest price
            setGlobalPrice(response, productId, displayCurrency, now);
            return;
        }

        String code = countryCode.toUpperCase();
        Country country = countryRepository.findByCode(code).orElse(null);
        if (country == null) {
            setGlobalPrice(response, productId, displayCurrency, now);
            return;
        }

        List<Object[]> allStores = storeProductRepository.findCheapestB2cStoreByProductAndCountry(productId, code);
        boolean available = !allStores.isEmpty();
        response.setAvailableInSelectedCountry(available);

        if (available) {
            String storeCurrency = country.getCurrency();
            String target = (displayCurrency != null && !displayCurrency.isBlank())
                    ? displayCurrency.toUpperCase()
                    : storeCurrency;

            Set<UUID> expressStoreIds = (lat != null && lng != null)
                    ? new HashSet<>(storeLocationRepository.findStoreIdsWithinRadius(lat, lng, EXPRESS_RADIUS_KM))
                    : null;

            List<ProductResponse.StoreOptionDto> options = new java.util.ArrayList<>();
            for (Object[] row : allStores) {
                UUID sid = (UUID) row[0];
                Boolean express = expressStoreIds != null ? expressStoreIds.contains(sid) : null;
                options.add(buildStoreOption(sid, (BigDecimal) row[1], row[2], (BigDecimal) row[3],
                        (Instant) row[4], (Instant) row[5], now, storeCurrency, target, express));
            }
            response.setStoreOptions(options);

            // Primary store: first express store, or cheapest if none are express
            int primaryIndex = 0;
            for (int i = 0; i < options.size(); i++) {
                if (Boolean.TRUE.equals(options.get(i).getExpressDelivery())) {
                    primaryIndex = i;
                    break;
                }
            }
            applyPrimaryOption(response, options.get(primaryIndex), target);
        } else {
            // Not available in this specific country - fall back to global price for display
            setGlobalPrice(response, productId, displayCurrency, now);
        }
    }

    // Store options keep the ORDER THE QUERY GAVE THEM — raw storePrice ASC — and the primary option
    // is still the first express store, else the first of that list. Re-ordering them by the
    // discounted price was tried here and reverted: response.storeId is the store the client sends
    // back as the one to fulfil from, so sorting this list silently moves every discounted product's
    // order to a different store, which is a fulfilment change nobody asked for and nothing else in
    // the checkout path expects. A discounted store therefore stays where the query put it; that it
    // may be listed below a dearer one is pre-existing and cosmetic, and fixing it needs a decision
    // about fulfilment, not a comparator.

    /** Copies the chosen store option onto the product response, flash-sale fields included. */
    private void applyPrimaryOption(ProductResponse response, ProductResponse.StoreOptionDto primary,
                                    String currency) {
        response.setStoreId(primary.getStoreId());
        response.setStorePrice(primary.getStorePrice());
        response.setOriginalPrice(primary.getOriginalPrice());
        response.setCurrency(currency);
        response.setExpressDelivery(primary.getExpressDelivery());
        // The rail and the countdown read these. Taken from the SAME option the price came from, so
        // a product can never advertise a countdown belonging to a store it is not priced from.
        response.setOnFlashSale(primary.getFlashSaleEndsAt() != null ? Boolean.TRUE : null);
        response.setFlashSaleEndsAt(primary.getFlashSaleEndsAt());
        // A sale still to come carries no countdown and no onFlashSale — it is not running — but the
        // instant travels with the pre-sale price so the caches know when that price stops being true.
        response.setFlashSaleStartsAt(primary.getFlashSaleStartsAt());
    }

    /**
     * The no-country / not-sold-in-country fallback — what an anonymous first-time visitor with no
     * market selected is shown. Priced through the same {@link #buildStoreOption} as the country
     * path, so the two cannot drift apart.
     */
    private void setGlobalPrice(ProductResponse response, UUID productId, String displayCurrency, Instant now) {
        List<Object[]> globalPrice = storeProductRepository.findCheapestB2cStoreGlobally(productId);
        if (!globalPrice.isEmpty()) {
            Object[] row = globalPrice.get(0);
            BigDecimal rawPrice = (BigDecimal) row[0];
            String storeCurrency = (String) row[1];
            String target = (displayCurrency != null && !displayCurrency.isBlank()) ? displayCurrency.toUpperCase() : storeCurrency;

            Instant startsAt = (Instant) row[4];
            Instant endsAt = (Instant) row[5];

            ProductResponse.StoreOptionDto option = buildStoreOption(
                    null, rawPrice, row[2], (BigDecimal) row[3], startsAt, endsAt, now,
                    storeCurrency, target, null);
            response.setStorePrice(option.getStorePrice());
            if (option.getOriginalPrice() != null) {
                response.setOriginalPrice(option.getOriginalPrice());
                if (option.getFlashSaleEndsAt() != null) {
                    response.setOnFlashSale(Boolean.TRUE);
                    response.setFlashSaleEndsAt(option.getFlashSaleEndsAt());
                }
            }
            response.setFlashSaleStartsAt(option.getFlashSaleStartsAt());
            response.setCurrency(target);
        }
    }

    /**
     * Builds a store option with discount applied: storePrice = effective (discounted)
     * price converted to the display currency, originalPrice = pre-discount price (only
     * when an actual discount lowers the price). Single place the read-path discount math lives.
     *
     * <p>Since V60 a discount also has a WINDOW, so the two date columns and the request's single
     * {@code now} are required parameters rather than something a caller may forget: outside the
     * window there is no discount, no struck-through price and no countdown, and the shopper pays
     * {@code storePrice} — which is exactly what the cart and checkout will charge them.
     *
     * <p><b>The price always comes from the PARENT listing row, never from a variant.</b> An earlier
     * attempt quoted the cheapest active variant here so that a card would match a variant-priced
     * cart line, and it made things worse rather than better: the web storefront sends no variantId
     * (it posts {storeId, productId, quantity}), so CartService priced its lines from the parent and
     * this method advertised 900 for a listing the basket charged 1000 for — a divergence in the
     * direction that costs the customer money, on the busier of the two clients, where there had been
     * none. There is exactly ONE number a customer is shown for a product and it is this one; the
     * cart, the checkout, Buy Now and the abandoned-cart email all price through
     * {@code StoreProduct.effectivePrice} on the same row, so they cannot disagree with it. See
     * {@code CartLinePricing} for why a variantId identifies a line without pricing it.
     *
     * <p>{@code flashSaleEndsAt} is set only for a LIVE discount that has an end. A permanent
     * markdown gets its struck-through price and no countdown, because there is nothing to count
     * down to. Both the countdown and the struck-through price are decided on the CONVERTED pair,
     * not the store-currency one: two figures a cent apart can round to the same number in the
     * display currency, and a strike-through plus a countdown over an identical price is a
     * full-price card wearing a sale badge — which is exactly what the rail must not show.
     */
    private ProductResponse.StoreOptionDto buildStoreOption(
            UUID storeId, BigDecimal rawPrice, Object discountType, BigDecimal discountValue,
            Instant discountStartsAt, Instant discountEndsAt, Instant now,
            String fromCurrency, String toCurrency, Boolean express) {
        Product.DiscountType dType = (Product.DiscountType) discountType;
        BigDecimal effective = StoreProduct.effectivePrice(
                rawPrice, dType, discountValue, discountStartsAt, discountEndsAt, now);

        BigDecimal convEffective = currencyExchangeService.convert(effective, fromCurrency, toCurrency);
        BigDecimal convList = currencyExchangeService.convert(rawPrice, fromCurrency, toCurrency);
        ProductResponse.StoreOptionDto opt =
                new ProductResponse.StoreOptionDto(storeId, convEffective, toCurrency, express);
        if (convEffective != null && convList != null && convEffective.compareTo(convList) < 0) {
            opt.setOriginalPrice(convList);
            if (discountEndsAt != null) {
                opt.setFlashSaleEndsAt(discountEndsAt);
            }
        } else if (discountStartsAt != null && now.isBefore(discountStartsAt)
                && dType != null && discountValue != null) {
            // A sale that has NOT started yet. The price above is the pre-sale one and is correct right
            // now — and stops being correct the moment the window opens, which is why the instant is
            // stamped on the option.
            //
            // It is what lets CatalogueCacheFilter bound this body: without it, a response serialised a
            // minute before a sale begins carries no sale boundary at all, so it was cached for the full
            // minute and licensed to the browser for six more — the sale silently failing to start on
            // the cards while the basket already charged it. Deliberately not conditional on the
            // scheduled discount actually lowering the price: a spurious bound only shortens a cache
            // life, while a missing one shows the wrong price.
            opt.setFlashSaleStartsAt(discountStartsAt);
        }
        return opt;
    }

    /**
     * Batch version of applyCountryPricing — fetches all prices in one query and maps
     * them to the corresponding ProductResponse objects by product ID.
     */
    private void applyBatchCountryPricing(List<ProductResponse> responses, List<Product> products,
                                          String countryCode, String displayCurrency,
                                          Double lat, Double lng) {
        applyBatchCountryPricing(responses, products, countryCode, displayCurrency, lat, lng, true);
    }

    /**
     * @param includeExtras when false, only storePrice/currency/storeOptions are resolved —
     *                      ratings and delivery info are skipped (used by the price-range
     *                      computation, which reads only storePrice).
     */
    private void applyBatchCountryPricing(List<ProductResponse> responses, List<Product> products,
                                          String countryCode, String displayCurrency,
                                          Double lat, Double lng, boolean includeExtras) {
        // One instant for the whole batch — see applyCountryPricing. A 60-product list resolved
        // against 60 different clocks could show two identical products on different sides of the
        // same expiry.
        applyBatchCountryPricing(responses, products, countryCode, displayCurrency, lat, lng,
                includeExtras, Instant.now());
    }

    /**
     * @param now the caller's instant. The flash-sale rail passes its OWN, the one its query selected
     *            products with: reading the clock again here let a sale end between deciding a product
     *            was on the rail and deciding what its card said, so the rail listed it at full price.
     */
    private void applyBatchCountryPricing(List<ProductResponse> responses, List<Product> products,
                                          String countryCode, String displayCurrency,
                                          Double lat, Double lng, boolean includeExtras, Instant now) {
        if (products.isEmpty()) return;

        List<UUID> allProductIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<Object[]>> storesByProduct = new java.util.HashMap<>();
        String targetCurrency = displayCurrency != null ? displayCurrency.toUpperCase() : null;
        Set<UUID> expressStoreIds = (lat != null && lng != null)
                ? new HashSet<>(storeLocationRepository.findStoreIdsWithinRadius(lat, lng, EXPRESS_RADIUS_KM))
                : null;

        String countryStoreCurrency = null;
        if (countryCode != null && !countryCode.isBlank()) {
            String code = countryCode.toUpperCase();
            Country country = countryRepository.findByCode(code).orElse(null);
            if (country != null) {
                countryStoreCurrency = country.getCurrency();
                if (targetCurrency == null) targetCurrency = countryStoreCurrency;

                List<Object[]> rows = storeProductRepository.findAllB2cStoresPerProductBatch(allProductIds, code);
                for (Object[] row : rows) {
                    UUID pid = (UUID) row[0];
                    storesByProduct.computeIfAbsent(pid, k -> new java.util.ArrayList<>()).add(row);
                }
            }
        }

        // Identify products needing global price (either no country provided or not available in country)
        List<UUID> missingPriceIds = new java.util.ArrayList<>();
        for (Product p : products) {
            if (!storesByProduct.containsKey(p.getId())) {
                missingPriceIds.add(p.getId());
            }
        }

        Map<UUID, Object[]> globalPrices = new java.util.HashMap<>();
        if (!missingPriceIds.isEmpty()) {
            List<Object[]> gRows = storeProductRepository.findCheapestB2cPricesGloballyBatch(missingPriceIds);
            for (Object[] grow : gRows) {
                // Keep the FIRST row per product (query is ORDER BY'd) so price ties across
                // currencies resolve deterministically — search and the filter range agree.
                globalPrices.putIfAbsent((UUID) grow[0], grow);
            }
        }

        for (int i = 0; i < responses.size(); i++) {
            ProductResponse resp = responses.get(i);
            UUID pid = products.get(i).getId();
            List<Object[]> productRows = storesByProduct.get(pid);
            boolean availableInCountry = productRows != null && !productRows.isEmpty();
            resp.setAvailableInSelectedCountry(availableInCountry);

            if (availableInCountry) {
                List<ProductResponse.StoreOptionDto> options = new java.util.ArrayList<>();
                for (Object[] row : productRows) {
                    UUID sid = (UUID) row[1];
                    Boolean express = expressStoreIds != null ? expressStoreIds.contains(sid) : null;
                    options.add(buildStoreOption(sid, (BigDecimal) row[2], row[3], (BigDecimal) row[4],
                            (Instant) row[5], (Instant) row[6], now,
                            countryStoreCurrency, targetCurrency, express));
                }
                resp.setStoreOptions(options);

                int primaryIndex = 0;
                for (int o = 0; o < options.size(); o++) {
                    if (Boolean.TRUE.equals(options.get(o).getExpressDelivery())) {
                        primaryIndex = o;
                        break;
                    }
                }
                applyPrimaryOption(resp, options.get(primaryIndex), targetCurrency);
            } else {
                // Fallback to global price
                Object[] gPrice = globalPrices.get(pid);
                if (gPrice != null) {
                    BigDecimal rawPrice = (BigDecimal) gPrice[1];
                    String gCurrency = (String) gPrice[2];
                    String finalTarget = targetCurrency != null ? targetCurrency : gCurrency;
                    Instant gStartsAt = (Instant) gPrice[5];
                    Instant gEndsAt = (Instant) gPrice[6];

                    // Through buildStoreOption rather than a third inline copy of the same arithmetic:
                    // the parenthesised fallback used to re-implement the discount test, and now that
                    // the test also has to know about the window and the converted currency, a third
                    // copy is a third thing to forget.
                    ProductResponse.StoreOptionDto option = buildStoreOption(
                            null, rawPrice, gPrice[3], (BigDecimal) gPrice[4], gStartsAt, gEndsAt, now,
                            gCurrency, finalTarget, null);
                    resp.setStorePrice(option.getStorePrice());
                    if (option.getOriginalPrice() != null) {
                        resp.setOriginalPrice(option.getOriginalPrice());
                        if (option.getFlashSaleEndsAt() != null) {
                            resp.setOnFlashSale(Boolean.TRUE);
                            resp.setFlashSaleEndsAt(option.getFlashSaleEndsAt());
                        }
                    }
                    resp.setFlashSaleStartsAt(option.getFlashSaleStartsAt());
                    resp.setCurrency(finalTarget);
                }
            }
            if (includeExtras) applyDeliveryInfo(resp, countryCode);
        }
        if (includeExtras) applyRatingsBatch(responses, products);
    }

    private ProductResponse buildResponse(
            Product product,
            String title,
            String description,
            String slug,
            List<ProductResponse.MediaDto> mediaDtos,
            List<ProductResponse.SpecGroupDto> specGroupDtos,
            List<ProductResponse.ColorOptionDto> colorDtos,
            List<ProductResponse.VariantDto> variantDtos,
            List<UUID> accessoryIds,
            boolean includeStatus,
            String lang) {

        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCategoryId(product.getCategory().getId());
        if (product.getBrand() != null) {
            response.setBrandId(product.getBrand().getId());
            String brandName = brandTranslationRepository
                    .findByBrand_IdAndLanguageIgnoreCase(product.getBrand().getId(), lang)
                    .map(t -> t.getName())
                    .orElseGet(() -> brandTranslationRepository
                            .findByBrand_IdAndLanguageIgnoreCase(product.getBrand().getId(), "EN")
                            .map(t -> t.getName())
                            .orElse(null));
            response.setBrandName(brandName);
        }
        response.setProductType(product.getProductType() != null ? product.getProductType().name() : null);
        response.setIsRefurbished(product.getIsRefurbished());
        response.setRefurbGrade(product.getRefurbGrade() != null ? product.getRefurbGrade().name() : null);
        response.setSku(product.getSku());
        response.setAvailabilityStatus(product.getAvailabilityStatus() != null ? product.getAvailabilityStatus().name() : null);
        response.setStockQuantity(product.getStockQuantity());
        response.setAvailableQuantity(product.getAvailableQuantity());
        response.setIsSuperDeal(product.getIsSuperDeal());
        response.setIsLimitedStock(product.getIsLimitedStock());
        if (includeStatus) {
            response.setStatus(product.getStatus());
            response.setDeletedAt(product.getDeletedAt());
        }
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        response.setTitle(title);
        response.setDescription(description);
        response.setSlug(slug);
        response.setMedia(mediaDtos);
        response.setSpecs(specGroupDtos);
        response.setColors(colorDtos);
        response.setVariants(variantDtos);
        response.setAccessoryIds(accessoryIds);
        return response;
    }
}
