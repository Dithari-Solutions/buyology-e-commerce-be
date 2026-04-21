package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.enums.SpecUnit;
import com.buyology.ecommerce.common.response.ApiResponse;
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
import com.buyology.ecommerce.product.repository.ProductVariantOptionRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.repository.CountryRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.product.search.service.ProductSearchService;
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

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
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
    private final StoreLocationRepository storeLocationRepository;
    private final ContaboObjectService contaboObjectService;
    private final ProductSearchService productSearchService;

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
            StoreLocationRepository storeLocationRepository,
            ContaboObjectService contaboObjectService,
            ProductSearchService productSearchService) {
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
        this.storeLocationRepository = storeLocationRepository;
        this.contaboObjectService = contaboObjectService;
        this.productSearchService = productSearchService;
    }

    /**
     * Creates a new product with all associated data in a single atomic transaction:
     * translations (AZ, EN, AR), spec groups/options (with additionalPrice for upgrades),
     * colors (each with their own media), remaining media, variants, and accessories.
     *
     * @param request    the product creation request
     * @param mediaFiles flat list of uploaded media files; colors claim files by index
     * @return the created product wrapped in an ApiResponse
     */
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

        // 3. Persist base product
        String sku = generateSku(request.getProductType());
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
        List<ProductResponse> responses = productRepository.findByStatusNot("DELETED").stream()
                .map(p -> toResponse(p, lang, true))
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategoryAdmin(UUID categoryId, String lang) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));

        List<ProductResponse> responses = productRepository.findByStatusNotAndCategoryId("DELETED", categoryId).stream()
                .map(p -> toResponse(p, lang, true))
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
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
        product.setStatus("ACTIVE");
        product.setDeletedAt(null);
        productRepository.save(product);
        return ApiResponse.success(toResponse(product, lang, true), "Product restored successfully");
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
        List<ProductSpecGroup> groups = specGroupRepository.findByProduct_Id(productId);
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
        ProductResponse response = toResponse(product, lang, false);
        applyCountryPricing(response, product.getId(), countryCode, currency, lat, lng);
        return ApiResponse.success(response, "Product fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProductsPublic(
            String lang, String countryCode, String currency, Double lat, Double lng) {
        List<Product> products = (countryCode != null && !countryCode.isBlank())
                ? storeProductRepository.findActiveProductsByCountryCode(countryCode.toUpperCase())
                : productRepository.findByStatus("ACTIVE");

        List<ProductResponse> responses = products.stream()
                .map(p -> toResponse(p, lang, false))
                .toList();
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProducts(
            ProductFilterRequest filter, String lang, String countryCode, String currency, Double lat, Double lng) {
        List<Product> products = productRepository.findAll(ProductSpecification.from(filter)).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList();

        // If country filter is active, intersect with country-available products
        if (countryCode != null && !countryCode.isBlank()) {
            List<UUID> countryProductIds = storeProductRepository
                    .findActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = products.stream()
                    .filter(p -> countryProductIds.contains(p.getId()))
                    .toList();
        }

        List<ProductResponse> responses = products.stream()
                .map(p -> toResponse(p, lang, false))
                .toList();
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProductsElastic(
            String query, String lang, String countryCode, String currency, Double lat, Double lng) {
        List<com.buyology.ecommerce.product.search.domain.ProductDocument> searchResults = productSearchService.search(query);
        
        List<UUID> productIds = searchResults.stream()
                .map(com.buyology.ecommerce.product.search.domain.ProductDocument::getId)
                .collect(Collectors.toList());
        
        if (productIds.isEmpty()) {
            return ApiResponse.success(List.of(), "No products found matching the query");
        }

        List<Product> products = productRepository.findAllById(productIds).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList();

        // Maintain order from search results
        Map<UUID, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        List<Product> orderedProducts = productIds.stream()
                .map(productMap::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        List<ProductResponse> responses = orderedProducts.stream()
                .map(p -> toResponse(p, lang, false))
                .toList();
        
        applyBatchCountryPricing(responses, orderedProducts, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Search results fetched successfully");
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
                    .findActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = productRepository.findByStatusAndCategoryId("ACTIVE", categoryId).stream()
                    .filter(p -> countryProductIds.contains(p.getId()))
                    .toList();
        } else {
            products = productRepository.findByStatusAndCategoryId("ACTIVE", categoryId);
        }

        List<ProductResponse> responses = products.stream()
                .map(p -> toResponse(p, lang, false))
                .toList();
        applyBatchCountryPricing(responses, products, countryCode, currency, lat, lng);
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getSuperDeals(
            String lang, String countryCode, String currency) {
        List<Product> products = productRepository.findByStatusAndIsSuperDeal("ACTIVE", true);
        if (countryCode != null && !countryCode.isBlank()) {
            List<UUID> countryProductIds = storeProductRepository
                    .findActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = products.stream().filter(p -> countryProductIds.contains(p.getId())).toList();
        }
        List<ProductResponse> responses = products.stream().map(p -> toResponse(p, lang, false)).toList();
        applyBatchCountryPricing(responses, products, countryCode, currency, null, null);
        return ApiResponse.success(responses, "Super deal products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getLimitedStockProducts(
            String lang, String countryCode, String currency) {
        List<Product> products = productRepository.findByStatusAndIsLimitedStock("ACTIVE", true);
        if (countryCode != null && !countryCode.isBlank()) {
            List<UUID> countryProductIds = storeProductRepository
                    .findActiveProductsByCountryCode(countryCode.toUpperCase())
                    .stream().map(Product::getId).toList();
            products = products.stream().filter(p -> countryProductIds.contains(p.getId())).toList();
        }
        List<ProductResponse> responses = products.stream().map(p -> toResponse(p, lang, false)).toList();
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
        List<Product> products = productRepository.findAllById(productIds).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList();
        List<ProductResponse> responses = products.stream().map(p -> toResponse(p, lang, false)).toList();
        applyBatchCountryPricing(responses, products, countryCode, currency, null, null);
        return ApiResponse.success(responses, "Quick delivery products fetched successfully");
    }

    // ========================
    // Private helpers
    // ========================

    private ProductResponse toResponse(Product product, String lang, boolean includeStatus) {
        List<ProductTranslation> translations = translationRepository.findByProductId(product.getId())
                .stream()
                .filter(t -> t.getLanguage().equalsIgnoreCase(lang))
                .toList();
        if (translations.isEmpty()) {
            throw new IllegalArgumentException("No translation found for language: " + lang);
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
                        SpecUnit unit = opt.getGlobalSpecOption().getUnit();
                        return new ProductResponse.SpecOptionDto(opt.getId(), optValue, unit, opt.getAdditionalPrice());
                    })
                    .toList();

            groupDtos.add(new ProductResponse.SpecGroupDto(group.getId(), group.getCode(), groupName, optionDtos));
        }
        return groupDtos;
    }

    private List<ProductTranslation> saveTranslations(Product product, ProductTranslationRequest tr) {
        String slugAz = SlugUtils.toSlug(tr.getTitleAz());
        String slugEn = SlugUtils.toSlug(tr.getTitleEn());
        String slugAr = SlugUtils.toSlug(tr.getTitleAr());

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

        List<ProductTranslation> translations = new ArrayList<>();
        translations.add(new ProductTranslation(product, "AZ", tr.getTitleAz(), tr.getDescriptionAz(), slugAz));
        translations.add(new ProductTranslation(product, "EN", tr.getTitleEn(), tr.getDescriptionEn(), slugEn));
        translations.add(new ProductTranslation(product, "AR", tr.getTitleAr(), tr.getDescriptionAr(), slugAr));
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
     * additionalPrice = 0 means the spec is included in the base product price.
     * additionalPrice > 0 means it is an upgrade option that costs extra.
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
                        new ProductSpecOption(group, globalOption, valueEn, globalOption.getUnit(), optReq.getAdditionalPrice()));

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

            // Save the color as a spec option (colors have no additional price — price comes from variants)
            ProductSpecOption colorOption = specOptionRepository.save(
                    new ProductSpecOption(colorGroup, colorReq.getValueEn(), BigDecimal.ZERO, colorReq.getColorCode()));

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

    private void applyCountryPricing(ProductResponse response, UUID productId,
                                     String countryCode, String displayCurrency,
                                     Double lat, Double lng) {
        if (countryCode == null || countryCode.isBlank()) return;

        String code = countryCode.toUpperCase();
        Country country = countryRepository.findByCode(code).orElse(null);
        if (country == null) return;

        List<Object[]> allStores = storeProductRepository.findCheapestStoreByProductAndCountry(productId, code);
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
                BigDecimal converted = currencyExchangeService.convert((java.math.BigDecimal) row[1], storeCurrency, target);
                Boolean express = expressStoreIds != null ? expressStoreIds.contains(sid) : null;
                options.add(new ProductResponse.StoreOptionDto(sid, converted, target, express));
            }
            response.setStoreOptions(options);

            // Primary store: first express store, or cheapest if none are express
            ProductResponse.StoreOptionDto primary = options.stream()
                    .filter(o -> Boolean.TRUE.equals(o.getExpressDelivery()))
                    .findFirst()
                    .orElse(options.get(0));
            response.setStoreId(primary.getStoreId());
            response.setStorePrice(primary.getStorePrice());
            response.setCurrency(target);
            response.setExpressDelivery(primary.getExpressDelivery());
        }
    }

    /**
     * Batch version of applyCountryPricing — fetches all prices in one query and maps
     * them to the corresponding ProductResponse objects by product ID.
     */
    private void applyBatchCountryPricing(List<ProductResponse> responses, List<Product> products,
                                          String countryCode, String displayCurrency,
                                          Double lat, Double lng) {
        if (countryCode == null || countryCode.isBlank() || products.isEmpty()) return;

        String code = countryCode.toUpperCase();
        Country country = countryRepository.findByCode(code).orElse(null);
        if (country == null) return;

        String storeCurrency = country.getCurrency();
        String target = (displayCurrency != null && !displayCurrency.isBlank())
                ? displayCurrency.toUpperCase()
                : storeCurrency;

        List<UUID> ids = products.stream().map(Product::getId).toList();
        List<Object[]> rows = storeProductRepository.findAllStoresPerProductBatch(ids, code);

        // Group rows by productId → ordered list of [storeId, rawPrice]
        Map<UUID, List<Object[]>> storesByProduct = new java.util.LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID pid = (UUID) row[0];
            storesByProduct.computeIfAbsent(pid, k -> new java.util.ArrayList<>()).add(row);
        }

        Set<UUID> expressStoreIds = (lat != null && lng != null)
                ? new HashSet<>(storeLocationRepository.findStoreIdsWithinRadius(lat, lng, EXPRESS_RADIUS_KM))
                : null;

        for (int i = 0; i < responses.size(); i++) {
            ProductResponse resp = responses.get(i);
            UUID pid = products.get(i).getId();
            List<Object[]> productRows = storesByProduct.get(pid);
            resp.setAvailableInSelectedCountry(productRows != null && !productRows.isEmpty());
            if (productRows != null && !productRows.isEmpty()) {
                List<ProductResponse.StoreOptionDto> options = new java.util.ArrayList<>();
                for (Object[] row : productRows) {
                    UUID sid = (UUID) row[1];
                    BigDecimal converted = currencyExchangeService.convert((java.math.BigDecimal) row[2], storeCurrency, target);
                    Boolean express = expressStoreIds != null ? expressStoreIds.contains(sid) : null;
                    options.add(new ProductResponse.StoreOptionDto(sid, converted, target, express));
                }
                resp.setStoreOptions(options);

                // Primary store: first express store, or cheapest if none are express
                ProductResponse.StoreOptionDto primary = options.stream()
                        .filter(o -> Boolean.TRUE.equals(o.getExpressDelivery()))
                        .findFirst()
                        .orElse(options.get(0));
                resp.setStoreId(primary.getStoreId());
                resp.setStorePrice(primary.getStorePrice());
                resp.setCurrency(target);
                resp.setExpressDelivery(primary.getExpressDelivery());
            }
        }
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
