package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import com.buyology.ecommerce.product.dto.CategoryResponse;
import com.buyology.ecommerce.product.dto.CategoryTranslationRequest;
import com.buyology.ecommerce.product.dto.CreateCategoryRequest;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductCategoryTranslationRepository translationRepository;

    public ProductCategoryService(
            ProductCategoryRepository categoryRepository,
            ProductCategoryTranslationRepository translationRepository) {
        this.categoryRepository = categoryRepository;
        this.translationRepository = translationRepository;
    }

    /**
     * Creates a root category (parentId = null) or a subcategory (parentId set).
     * All three language translations are required: AZ, EN, AR.
     * Slugs are validated for global uniqueness per language before saving.
     *
     * @param request the category creation request
     * @return the created category wrapped in an ApiResponse
     */
    @Transactional
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(CreateCategoryRequest request) {

        // 1. Resolve the parent category when creating a subcategory
        ProductCategory parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent category not found with id: " + request.getParentId()));
        }

        // 2. Validate slug uniqueness across all three languages before any writes
        validateSlugUniqueness(request.getTranslations());

        // 3. Persist the category
        ProductCategory category = new ProductCategory(parent, request.getStatus());
        ProductCategory savedCategory = categoryRepository.save(category);

        // 4. Persist translations for AZ, EN, and AR
        List<ProductCategoryTranslation> savedTranslations =
                saveTranslations(savedCategory, request.getTranslations());

        // 5. Build and return the response
        CategoryResponse response = buildResponse(savedCategory, savedTranslations);
        String message = parent == null ? "Category created successfully" : "Subcategory created successfully";
        return ApiResponse.success(response, message);
    }

    // ========================
    // Private helpers
    // ========================

    /**
     * Validates that no slug already exists for each language.
     * Checks all three slugs up front so the caller gets all conflicts at once.
     */
    private void validateSlugUniqueness(CategoryTranslationRequest tr) {
        if (translationRepository.existsBySlugAndLanguage(tr.getSlugAz(), "AZ")) {
            throw new IllegalArgumentException(
                    "Slug '" + tr.getSlugAz() + "' is already taken for language AZ");
        }
        if (translationRepository.existsBySlugAndLanguage(tr.getSlugEn(), "EN")) {
            throw new IllegalArgumentException(
                    "Slug '" + tr.getSlugEn() + "' is already taken for language EN");
        }
        if (translationRepository.existsBySlugAndLanguage(tr.getSlugAr(), "AR")) {
            throw new IllegalArgumentException(
                    "Slug '" + tr.getSlugAr() + "' is already taken for language AR");
        }
    }

    private List<ProductCategoryTranslation> saveTranslations(
            ProductCategory category, CategoryTranslationRequest tr) {

        List<ProductCategoryTranslation> translations = List.of(
                new ProductCategoryTranslation(category, "AZ", tr.getNameAz(), tr.getDescriptionAz(), tr.getSlugAz()),
                new ProductCategoryTranslation(category, "EN", tr.getNameEn(), tr.getDescriptionEn(), tr.getSlugEn()),
                new ProductCategoryTranslation(category, "AR", tr.getNameAr(), tr.getDescriptionAr(), tr.getSlugAr()));

        return translationRepository.saveAll(translations);
    }

    private CategoryResponse buildResponse(
            ProductCategory category,
            List<ProductCategoryTranslation> translations) {

        List<CategoryResponse.TranslationDto> translationDtos = translations.stream()
                .map(t -> new CategoryResponse.TranslationDto(
                        t.getLanguage(), t.getName(), t.getDescription(), t.getSlug()))
                .toList();

        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setParentId(category.getParent() != null ? category.getParent().getId() : null);
        response.setStatus(category.getStatus());
        response.setCreatedAt(category.getCreatedAt());
        response.setUpdatedAt(category.getUpdatedAt());
        response.setTranslations(translationDtos);
        return response;
    }
}
