package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductAccessory;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.product.domain.ProductNotFoundException;
import com.buyology.ecommerce.product.domain.ProductSpecOption;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.product.domain.ProductVariantOption;
import com.buyology.ecommerce.product.dto.CreateProductRequest;
import com.buyology.ecommerce.product.dto.CreateVariantRequest;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.dto.ProductTranslationRequest;
import com.buyology.ecommerce.product.repository.ProductAccessoryRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductMediaRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductVariantOptionRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private static final String STATIC_PRODUCT_PATH = "/static/product";

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductSpecOptionRepository specOptionRepository;
    private final ProductTranslationRepository translationRepository;
    private final ProductMediaRepository mediaRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductVariantOptionRepository variantOptionRepository;
    private final ProductAccessoryRepository accessoryRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductSpecOptionRepository specOptionRepository,
            ProductTranslationRepository translationRepository,
            ProductMediaRepository mediaRepository,
            ProductVariantRepository variantRepository,
            ProductVariantOptionRepository variantOptionRepository,
            ProductAccessoryRepository accessoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.specOptionRepository = specOptionRepository;
        this.translationRepository = translationRepository;
        this.mediaRepository = mediaRepository;
        this.variantRepository = variantRepository;
        this.variantOptionRepository = variantOptionRepository;
        this.accessoryRepository = accessoryRepository;
    }

    /**
     * Creates a new product with all associated data in a single atomic transaction:
     * translations (AZ, EN, AR), media files, variants with spec options, and accessories.
     *
     * @param request    the product creation request
     * @param mediaFiles optional list of uploaded media files
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

        // 2. Business rule: refurb grade is mandatory when the product is refurbished
        if (Boolean.TRUE.equals(request.getIsRefurbished()) && request.getRefurbGrade() == null) {
            throw new IllegalArgumentException(
                    "Refurb grade (A, B, or C) is required when isRefurbished is true");
        }

        // 3. Persist the base product so that we have a UUID for all child entities
        Product product = new Product(
                category,
                request.getProductType(),
                request.getIsRefurbished(),
                request.getRefurbGrade(),
                request.getBasePrice(),
                request.getSku(),
                request.getStatus());
        Product savedProduct = productRepository.save(product);

        // 4. Save translations — all three languages are required
        List<ProductTranslation> savedTranslations = saveTranslations(savedProduct, request.getTranslations());

        // 5. Save media files to disk and persist ProductMedia records
        List<ProductResponse.MediaDto> mediaDtos = saveMediaFiles(savedProduct, mediaFiles);

        // 6. Create variants and link them to existing spec options
        List<ProductResponse.VariantDto> variantDtos = saveVariants(savedProduct, request.getVariants());

        // 7. Link accessory products
        List<UUID> resolvedAccessoryIds = saveAccessories(savedProduct, request.getAccessoryIds());

        // 8. Build and return the response
        ProductResponse response = buildResponse(
                savedProduct, savedTranslations, mediaDtos, variantDtos, resolvedAccessoryIds);

        return ApiResponse.created(response, "Product created successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        List<ProductResponse> responses = productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategory(UUID categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found with id: " + categoryId));

        List<ProductResponse> responses = productRepository.findByCategoryId(categoryId).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    // ========================
    // Private helpers
    // ========================

    private ProductResponse toResponse(Product product) {
        List<ProductTranslation> translations = translationRepository.findByProductId(product.getId());

        List<ProductResponse.MediaDto> mediaDtos = mediaRepository.findByProductId(product.getId()).stream()
                .map(m -> new ProductResponse.MediaDto(
                        m.getId(), m.getMediaType().name(), m.getUrl(),
                        m.getThumbnailUrl(), m.getIsPrimary(), m.getOrderIndex()))
                .toList();

        List<ProductResponse.VariantDto> variantDtos = variantRepository.findByProductId(product.getId()).stream()
                .map(v -> {
                    List<UUID> optionIds = variantOptionRepository.findByVariantId(v.getId()).stream()
                            .map(vo -> vo.getOption().getId())
                            .toList();
                    return new ProductResponse.VariantDto(v.getId(), v.getSku(), v.getPrice(), v.getStock(), optionIds);
                })
                .toList();

        List<UUID> accessoryIds = accessoryRepository.findByProductId(product.getId()).stream()
                .map(a -> a.getAccessory().getId())
                .toList();

        return buildResponse(product, translations, mediaDtos, variantDtos, accessoryIds);
    }

    private List<ProductTranslation> saveTranslations(Product product, ProductTranslationRequest tr) {
        List<ProductTranslation> translations = List.of(
                new ProductTranslation(product, "AZ", tr.getTitleAz(), tr.getDescriptionAz()),
                new ProductTranslation(product, "EN", tr.getTitleEn(), tr.getDescriptionEn()),
                new ProductTranslation(product, "AR", tr.getTitleAr(), tr.getDescriptionAr()));
        return translationRepository.saveAll(translations);
    }

    private List<ProductResponse.MediaDto> saveMediaFiles(Product product, List<MultipartFile> mediaFiles) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            return List.of();
        }

        Path productDir = Paths.get(STATIC_PRODUCT_PATH, product.getId().toString());
        try {
            Files.createDirectories(productDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create product media directory", e);
        }

        List<ProductMedia> mediaEntities = new ArrayList<>();
        for (int i = 0; i < mediaFiles.size(); i++) {
            MultipartFile file = mediaFiles.get(i);

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String savedFileName = i + extension;
            Path filePath = productDir.resolve(savedFileName);

            try {
                Files.write(filePath, file.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save media file: " + originalFilename, e);
            }

            ProductMedia.MediaType mediaType = resolveMediaType(file.getContentType());
            String url = "/product/" + product.getId() + "/" + savedFileName;
            // First uploaded file becomes the primary image/video
            boolean isPrimary = (i == 0);

            mediaEntities.add(new ProductMedia(product, mediaType, url, null, isPrimary, i));
        }

        List<ProductMedia> saved = mediaRepository.saveAll(mediaEntities);
        return saved.stream()
                .map(m -> new ProductResponse.MediaDto(
                        m.getId(),
                        m.getMediaType().name(),
                        m.getUrl(),
                        m.getThumbnailUrl(),
                        m.getIsPrimary(),
                        m.getOrderIndex()))
                .toList();
    }

    private List<ProductResponse.VariantDto> saveVariants(Product product, List<CreateVariantRequest> variantRequests) {
        if (variantRequests == null || variantRequests.isEmpty()) {
            return List.of();
        }

        List<ProductResponse.VariantDto> variantDtos = new ArrayList<>();

        for (CreateVariantRequest variantReq : variantRequests) {
            ProductVariant variant = new ProductVariant(
                    product,
                    variantReq.getSku(),
                    variantReq.getPrice(),
                    variantReq.getStock());
            ProductVariant savedVariant = variantRepository.save(variant);

            List<UUID> linkedOptionIds = new ArrayList<>();
            if (variantReq.getSpecOptionIds() != null) {
                for (UUID optionId : variantReq.getSpecOptionIds()) {
                    ProductSpecOption option = specOptionRepository.findById(optionId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Spec option not found with id: " + optionId));
                    variantOptionRepository.save(new ProductVariantOption(savedVariant, option));
                    linkedOptionIds.add(optionId);
                }
            }

            variantDtos.add(new ProductResponse.VariantDto(
                    savedVariant.getId(),
                    savedVariant.getSku(),
                    savedVariant.getPrice(),
                    savedVariant.getStock(),
                    linkedOptionIds));
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

    private ProductMedia.MediaType resolveMediaType(String contentType) {
        if (contentType != null && contentType.startsWith("video/")) {
            return ProductMedia.MediaType.VIDEO;
        }
        return ProductMedia.MediaType.IMAGE;
    }

    private ProductResponse buildResponse(
            Product product,
            List<ProductTranslation> translations,
            List<ProductResponse.MediaDto> mediaDtos,
            List<ProductResponse.VariantDto> variantDtos,
            List<UUID> accessoryIds) {

        List<ProductResponse.TranslationDto> translationDtos = translations.stream()
                .map(t -> new ProductResponse.TranslationDto(
                        t.getLanguage(), t.getTitle(), t.getDescription()))
                .toList();

        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCategoryId(product.getCategory().getId());
        response.setProductType(product.getProductType() != null ? product.getProductType().name() : null);
        response.setIsRefurbished(product.getIsRefurbished());
        response.setRefurbGrade(product.getRefurbGrade() != null ? product.getRefurbGrade().name() : null);
        response.setBasePrice(product.getBasePrice());
        response.setSku(product.getSku());
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        response.setTranslations(translationDtos);
        response.setMedia(mediaDtos);
        response.setVariants(variantDtos);
        response.setAccessoryIds(accessoryIds);
        return response;
    }
}
