package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Brand;
import com.buyology.ecommerce.product.domain.GlobalSpecGroup;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.dto.ProductFiltersResponse;
import com.buyology.ecommerce.product.dto.ProductFiltersResponse.BrandFilterDto;
import com.buyology.ecommerce.product.dto.ProductFiltersResponse.CategoryFilterDto;
import com.buyology.ecommerce.product.dto.ProductFiltersResponse.PriceRangeDto;
import com.buyology.ecommerce.product.dto.ProductFiltersResponse.SpecFilterDto;
import com.buyology.ecommerce.product.repository.BrandRepository;
import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductFilterService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductCategoryTranslationRepository categoryTranslationRepository;
    private final BrandRepository brandRepository;
    private final BrandTranslationRepository brandTranslationRepository;
    private final GlobalSpecGroupRepository globalSpecGroupRepository;
    private final GlobalSpecGroupTranslationRepository globalSpecGroupTranslationRepository;
    private final ProductSpecOptionRepository specOptionRepository;
    private final ProductService productService;

    public ProductFilterService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductCategoryTranslationRepository categoryTranslationRepository,
            BrandRepository brandRepository,
            BrandTranslationRepository brandTranslationRepository,
            GlobalSpecGroupRepository globalSpecGroupRepository,
            GlobalSpecGroupTranslationRepository globalSpecGroupTranslationRepository,
            ProductSpecOptionRepository specOptionRepository,
            ProductService productService) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.categoryTranslationRepository = categoryTranslationRepository;
        this.brandRepository = brandRepository;
        this.brandTranslationRepository = brandTranslationRepository;
        this.globalSpecGroupRepository = globalSpecGroupRepository;
        this.globalSpecGroupTranslationRepository = globalSpecGroupTranslationRepository;
        this.specOptionRepository = specOptionRepository;
    }

    /**
     * Returns all filter options that are actually represented in the active product catalog.
     * Results are localized using the requested language; falls back to EN when a translation
     * is missing for the requested language.
     *
     * @param countryCode ISO 3166-1 alpha-3 (e.g. "UAE") — scopes price range to that country's stores
     * @param lang        language code (e.g. "EN", "AZ", "AR")
     */
    public ResponseEntity<ApiResponse<ProductFiltersResponse>> getAvailableFilters(
            String countryCode, String lang, String displayCurrency, Double lat, Double lng) {

        String langUpper = lang != null ? lang.trim().toUpperCase() : "EN";

        ProductFiltersResponse response = new ProductFiltersResponse();

        // 1. Price range — resolved DISPLAY prices (discounted + currency-converted +
        // country/global), using the same pricing as the search, so the slider bounds
        // are in the display currency and match exactly what the cards show and what
        // the search filters on.
        BigDecimal[] priceRange = productService.resolveDisplayPriceRange(countryCode, displayCurrency, lat, lng);
        if (priceRange != null) {
            response.setPriceRange(new PriceRangeDto(priceRange[0], priceRange[1]));
        } else {
            response.setPriceRange(new PriceRangeDto(BigDecimal.ZERO, BigDecimal.ZERO));
        }

        // 2. Conditions (only include values that exist in the catalog)
        List<String> conditions = new ArrayList<>();
        if (productRepository.existsByStatusAndIsRefurbished("ACTIVE", false)) conditions.add("NEW");
        if (productRepository.existsByStatusAndIsRefurbished("ACTIVE", true)) conditions.add("REFURBISHED");
        response.setConditions(conditions);

        // 3. Categories with active products (localized)
        List<ProductCategory> activeCategories = categoryRepository.findCategoriesWithActiveProducts();
        List<CategoryFilterDto> categoryDtos = activeCategories.stream()
                .map(cat -> {
                    String name = categoryTranslationRepository
                            .findByCategoryIdAndLanguage(cat.getId(), langUpper)
                            .or(() -> categoryTranslationRepository.findByCategoryIdAndLanguage(cat.getId(), "EN"))
                            .map(t -> t.getName())
                            .orElse("—");
                    String slug = categoryTranslationRepository
                            .findByCategoryIdAndLanguage(cat.getId(), langUpper)
                            .or(() -> categoryTranslationRepository.findByCategoryIdAndLanguage(cat.getId(), "EN"))
                            .map(t -> t.getSlug())
                            .orElse("");
                    return new CategoryFilterDto(
                            cat.getId(),
                            name,
                            slug,
                            cat.getParent() != null ? cat.getParent().getId() : null);
                })
                .toList();
        response.setCategories(categoryDtos);

        // 4. Brands with active products (localized)
        List<Brand> activeBrands = brandRepository.findBrandsWithActiveProducts();
        List<BrandFilterDto> brandDtos = activeBrands.stream()
                .map(brand -> {
                    String name = brandTranslationRepository
                            .findByBrand_IdAndLanguageIgnoreCase(brand.getId(), langUpper)
                            .or(() -> brandTranslationRepository.findByBrand_IdAndLanguageIgnoreCase(brand.getId(), "EN"))
                            .map(t -> t.getName())
                            .orElse("—");
                    return new BrandFilterDto(brand.getId(), name);
                })
                .toList();
        response.setBrands(brandDtos);

        // 5. Availability statuses present in the catalog
        List<String> availabilityStatuses = productRepository.findDistinctAvailabilityStatuses()
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(Enum::name)
                .toList();
        response.setAvailabilityStatuses(availabilityStatuses);

        // 6. Spec filters — one entry per GlobalSpecGroup that has values in active products
        List<GlobalSpecGroup> allGroups = globalSpecGroupRepository.findAll();
        List<SpecFilterDto> specDtos = new ArrayList<>();
        for (GlobalSpecGroup group : allGroups) {
            List<Object[]> valueRows = specOptionRepository.findDistinctValueUnitsBySpecGroupCode(group.getCode());
            if (valueRows.isEmpty()) continue;

            List<ProductFiltersResponse.SpecFilterValueDto> values = valueRows.stream()
                    .map(r -> new ProductFiltersResponse.SpecFilterValueDto(
                            (String) r[0],
                            r[1] != null ? ((com.buyology.ecommerce.common.enums.SpecUnit) r[1]).name() : null))
                    .toList();

            String label = globalSpecGroupTranslationRepository
                    .findByGroup_IdAndLanguageIgnoreCase(group.getId(), langUpper)
                    .or(() -> globalSpecGroupTranslationRepository.findByGroup_IdAndLanguageIgnoreCase(group.getId(), "EN"))
                    .map(t -> t.getName())
                    .orElse(group.getCode());

            specDtos.add(new SpecFilterDto(group.getCode(), label, values));
        }
        response.setSpecs(specDtos);

        return ApiResponse.success(response, "Filters fetched successfully");
    }
}
