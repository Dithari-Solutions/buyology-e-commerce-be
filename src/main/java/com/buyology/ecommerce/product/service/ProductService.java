package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.response.ApiResponse;
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
import com.buyology.ecommerce.common.utils.SlugUtils;
import com.buyology.ecommerce.product.dto.CreateSpecGroupRequest;
import com.buyology.ecommerce.product.dto.CreateSpecOptionRequest;
import com.buyology.ecommerce.product.dto.CreateVariantRequest;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.dto.ProductTranslationRequest;
import com.buyology.ecommerce.product.repository.ProductAccessoryRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final String PRODUCT_UPLOAD_PATH = "/opt/uploads/product";

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductSpecGroupRepository specGroupRepository;
    private final ProductSpecGroupTranslationRepository specGroupTranslationRepository;
    private final ProductSpecOptionRepository specOptionRepository;
    private final ProductSpecOptionTranslationRepository specOptionTranslationRepository;
    private final ProductTranslationRepository translationRepository;
    private final ProductMediaRepository mediaRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductVariantOptionRepository variantOptionRepository;
    private final ProductAccessoryRepository accessoryRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductSpecGroupRepository specGroupRepository,
            ProductSpecGroupTranslationRepository specGroupTranslationRepository,
            ProductSpecOptionRepository specOptionRepository,
            ProductSpecOptionTranslationRepository specOptionTranslationRepository,
            ProductTranslationRepository translationRepository,
            ProductMediaRepository mediaRepository,
            ProductVariantRepository variantRepository,
            ProductVariantOptionRepository variantOptionRepository,
            ProductAccessoryRepository accessoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.specGroupRepository = specGroupRepository;
        this.specGroupTranslationRepository = specGroupTranslationRepository;
        this.specOptionRepository = specOptionRepository;
        this.specOptionTranslationRepository = specOptionTranslationRepository;
        this.translationRepository = translationRepository;
        this.mediaRepository = mediaRepository;
        this.variantRepository = variantRepository;
        this.variantOptionRepository = variantOptionRepository;
        this.accessoryRepository = accessoryRepository;
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

        // 2. Refurb grade is mandatory when the product is refurbished
        if (Boolean.TRUE.equals(request.getIsRefurbished()) && request.getRefurbGrade() == null) {
            throw new IllegalArgumentException(
                    "Refurb grade (A, B, or C) is required when isRefurbished is true");
        }

        // 3. Discount validation
        if (request.getDiscountType() != null) {
            if (request.getDiscountValue() == null) {
                throw new IllegalArgumentException("discountValue is required when discountType is set");
            }
            if (request.getDiscountType() == Product.DiscountType.FIXED
                    && request.getDiscountValue().compareTo(request.getBasePrice()) >= 0) {
                throw new IllegalArgumentException("Fixed discount value must be lower than base price");
            }
            if (request.getDiscountType() == Product.DiscountType.PERCENTAGE
                    && (request.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0
                            || request.getDiscountValue().compareTo(new BigDecimal("100")) > 0)) {
                throw new IllegalArgumentException("Percentage discount must be between 0 and 100");
            }
        } else if (request.getDiscountValue() != null) {
            throw new IllegalArgumentException("discountType is required when discountValue is set");
        }

        // 4. Persist base product
        Product product = new Product(
                category,
                request.getProductType(),
                request.getIsRefurbished(),
                request.getRefurbGrade(),
                request.getBasePrice(),
                request.getDiscountType(),
                request.getDiscountValue(),
                request.getSku(),
                request.getStatus());
        Product savedProduct = productRepository.save(product);

        // 5. Save translations
        List<ProductTranslation> savedTranslations = saveTranslations(savedProduct, request.getTranslations());

        // 6. Create inline spec groups/options — builds localKey → option map for variant resolution
        Map<String, ProductSpecOption> localKeyToOption = new HashMap<>();
        List<ProductResponse.SpecGroupDto> specGroupDtos = saveSpecs(request.getSpecs(), localKeyToOption);

        // 7. Create color spec options with their own media; track which file indices are claimed
        Set<Integer> claimedMediaIndices = new HashSet<>();
        List<ProductResponse.ColorOptionDto> colorDtos = saveColors(
                savedProduct, request.getColors(), mediaFiles, localKeyToOption, claimedMediaIndices);

        // 8. Save remaining (product-level) media files — those not claimed by any color
        List<ProductResponse.MediaDto> mediaDtos = saveProductMedia(savedProduct, mediaFiles, claimedMediaIndices);

        // 9. Create variants — spec options resolved by existing UUID or inline localKey
        List<ProductResponse.VariantDto> variantDtos = saveVariants(savedProduct, request.getVariants(), localKeyToOption);

        // 10. Link accessories
        List<UUID> resolvedAccessoryIds = saveAccessories(savedProduct, request.getAccessoryIds());

        // 11. Build response
        ProductTranslation first = savedTranslations.get(0);
        ProductResponse response = buildResponse(
                savedProduct, first.getTitle(), first.getDescription(), first.getSlug(),
                mediaDtos, specGroupDtos, colorDtos, variantDtos, resolvedAccessoryIds, true);

        return ApiResponse.created(response, "Product created successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProductsAdmin(String lang) {
        List<ProductResponse> responses = productRepository.findAll().stream()
                .map(p -> toResponse(p, lang, true))
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategoryAdmin(UUID categoryId, String lang) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));

        List<ProductResponse> responses = productRepository.findByCategoryId(categoryId).stream()
                .map(p -> toResponse(p, lang, true))
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<ProductResponse>> getProductByIdAdmin(UUID id, String lang) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ApiResponse.success(toResponse(product, lang, true), "Product fetched successfully");
    }

    public ResponseEntity<ApiResponse<ProductResponse>> getProductByIdPublic(UUID id, String lang) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new ProductNotFoundException(id);
        }
        return ApiResponse.success(toResponse(product, lang, false), "Product fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProductsPublic(String lang) {
        List<ProductResponse> responses = productRepository.findByStatus("ACTIVE").stream()
                .map(p -> toResponse(p, lang, false))
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategoryPublic(UUID categoryId, String lang) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));

        List<ProductResponse> responses = productRepository.findByStatusAndCategoryId("ACTIVE", categoryId).stream()
                .map(p -> toResponse(p, lang, false))
                .toList();
        return ApiResponse.success(responses, "Products fetched successfully");
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
                    return new ProductResponse.VariantDto(v.getId(), v.getSku(), v.getPrice(), v.getStock(), optionIds);
                })
                .toList();

        List<UUID> accessoryIds = accessoryRepository.findByProductId(product.getId()).stream()
                .map(a -> a.getAccessory().getId())
                .toList();

        ProductTranslation translation = translations.get(0);
        return buildResponse(product, translation.getTitle(), translation.getDescription(), translation.getSlug(),
                mediaDtos, List.of(), colorDtos, variantDtos, accessoryIds, includeStatus);
    }

    private List<ProductTranslation> saveTranslations(Product product, ProductTranslationRequest tr) {
        List<ProductTranslation> translations = new ArrayList<>();
        translations.add(new ProductTranslation(product, "AZ", tr.getTitleAz(), tr.getDescriptionAz(), SlugUtils.toSlug(tr.getTitleAz())));
        translations.add(new ProductTranslation(product, "EN", tr.getTitleEn(), tr.getDescriptionEn(), SlugUtils.toSlug(tr.getTitleEn())));
        translations.add(new ProductTranslation(product, "AR", tr.getTitleAr(), tr.getDescriptionAr(), SlugUtils.toSlug(tr.getTitleAr())));
        return translationRepository.saveAll(translations);
    }

    /**
     * Creates spec groups and their options inline.
     * additionalPrice = 0 means the spec is included in the base product price.
     * additionalPrice > 0 means it is an upgrade option that costs extra.
     */
    private List<ProductResponse.SpecGroupDto> saveSpecs(
            List<CreateSpecGroupRequest> specRequests,
            Map<String, ProductSpecOption> localKeyToOption) {

        if (specRequests == null || specRequests.isEmpty()) {
            return List.of();
        }

        List<ProductResponse.SpecGroupDto> groupDtos = new ArrayList<>();

        for (CreateSpecGroupRequest groupReq : specRequests) {
            ProductSpecGroup group = specGroupRepository.save(new ProductSpecGroup(groupReq.getCode()));

            specGroupTranslationRepository.saveAll(List.of(
                    new ProductSpecGroupTranslation(group, "AZ", groupReq.getNameAz()),
                    new ProductSpecGroupTranslation(group, "EN", groupReq.getNameEn()),
                    new ProductSpecGroupTranslation(group, "AR", groupReq.getNameAr())
            ));

            List<ProductResponse.SpecOptionDto> optionDtos = new ArrayList<>();
            for (CreateSpecOptionRequest optReq : groupReq.getOptions()) {
                if (optReq.getLocalKey() != null && localKeyToOption.containsKey(optReq.getLocalKey())) {
                    throw new IllegalArgumentException("Duplicate localKey in specs: " + optReq.getLocalKey());
                }

                ProductSpecOption option = specOptionRepository.save(
                        new ProductSpecOption(group, optReq.getValueEn(), optReq.getAdditionalPrice()));

                specOptionTranslationRepository.saveAll(List.of(
                        new ProductSpecOptionTranslation(option, Language.AZ, optReq.getValueAz()),
                        new ProductSpecOptionTranslation(option, Language.EN, optReq.getValueEn()),
                        new ProductSpecOptionTranslation(option, Language.AR, optReq.getValueAr())
                ));

                if (optReq.getLocalKey() != null) {
                    localKeyToOption.put(optReq.getLocalKey(), option);
                }

                optionDtos.add(new ProductResponse.SpecOptionDto(
                        option.getId(), option.getValue(), option.getAdditionalPrice()));
            }

            groupDtos.add(new ProductResponse.SpecGroupDto(
                    group.getId(), group.getCode(), groupReq.getNameEn(), optionDtos));
        }

        return groupDtos;
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
        ProductSpecGroup colorGroup = specGroupRepository.save(new ProductSpecGroup("color_" + product.getId()));
        specGroupTranslationRepository.saveAll(List.of(
                new ProductSpecGroupTranslation(colorGroup, "AZ", "Rəng"),
                new ProductSpecGroupTranslation(colorGroup, "EN", "Color"),
                new ProductSpecGroupTranslation(colorGroup, "AR", "اللون")
        ));

        Path productDir = Paths.get(PRODUCT_UPLOAD_PATH, product.getId().toString());
        ensureDirectory(productDir);

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
                    String url = saveFile(productDir, file, "color_" + colorOption.getId() + "_" + i);
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

        Path productDir = Paths.get(PRODUCT_UPLOAD_PATH, product.getId().toString());
        ensureDirectory(productDir);

        List<ProductMedia> mediaEntities = new ArrayList<>();
        int orderIndex = 0;
        for (int i = 0; i < mediaFiles.size(); i++) {
            if (claimedMediaIndices.contains(i)) {
                continue; // skip files already owned by a color
            }
            MultipartFile file = mediaFiles.get(i);
            String url = saveFile(productDir, file, "product_" + orderIndex);
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
            ProductVariant variant = new ProductVariant(product, variantReq.getSku(), variantReq.getPrice(), variantReq.getStock());
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
                    savedVariant.getId(), savedVariant.getSku(), savedVariant.getPrice(), savedVariant.getStock(), linkedOptionIds));
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

    private String saveFile(Path dir, MultipartFile file, String baseName) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = baseName + extension;
        try {
            Files.write(dir.resolve(fileName), file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save media file: " + originalFilename, e);
        }
        return "/product/" + dir.getFileName() + "/" + fileName;
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create product media directory", e);
        }
    }

    private ProductMedia.MediaType resolveMediaType(String contentType) {
        if (contentType != null && contentType.startsWith("video/")) {
            return ProductMedia.MediaType.VIDEO;
        }
        return ProductMedia.MediaType.IMAGE;
    }

    private ProductResponse.MediaDto toMediaDto(ProductMedia m) {
        return new ProductResponse.MediaDto(
                m.getId(), m.getMediaType().name(), m.getUrl(),
                m.getThumbnailUrl(), m.getIsPrimary(), m.getOrderIndex());
    }

    private BigDecimal computeEffectivePrice(Product product) {
        if (product.getDiscountType() == null || product.getDiscountValue() == null) {
            return product.getBasePrice();
        }
        return switch (product.getDiscountType()) {
            case FIXED -> product.getDiscountValue();
            case PERCENTAGE -> product.getBasePrice()
                    .multiply(BigDecimal.ONE.subtract(
                            product.getDiscountValue().divide(new BigDecimal("100"))))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        };
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
            boolean includeStatus) {

        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCategoryId(product.getCategory().getId());
        response.setProductType(product.getProductType() != null ? product.getProductType().name() : null);
        response.setIsRefurbished(product.getIsRefurbished());
        response.setRefurbGrade(product.getRefurbGrade() != null ? product.getRefurbGrade().name() : null);
        response.setBasePrice(product.getBasePrice());
        response.setDiscountType(product.getDiscountType() != null ? product.getDiscountType().name() : null);
        response.setDiscountValue(product.getDiscountValue());
        response.setEffectivePrice(computeEffectivePrice(product));
        response.setSku(product.getSku());
        if (includeStatus) {
            response.setStatus(product.getStatus());
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
