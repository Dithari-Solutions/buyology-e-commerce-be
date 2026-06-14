package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import com.buyology.ecommerce.product.dto.CategoryLocalizedResponse;
import com.buyology.ecommerce.product.dto.CategoryResponse;
import com.buyology.ecommerce.product.dto.CategoryTranslationRequest;
import com.buyology.ecommerce.product.dto.CreateCategoryRequest;
import com.buyology.ecommerce.product.dto.UpdateCategoryRequest;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        category.setIcon(request.getIcon());
        ProductCategory savedCategory = categoryRepository.save(category);

        // 4. Persist translations for AZ, EN, and AR
        List<ProductCategoryTranslation> savedTranslations =
                saveTranslations(savedCategory, request.getTranslations());

        // 5. Build and return the response
        CategoryResponse response = buildResponse(savedCategory, savedTranslations);
        String message = parent == null ? "Category created successfully" : "Subcategory created successfully";
        return ApiResponse.created(response, message);
    }

    public ResponseEntity<ApiResponse<List<CategoryLocalizedResponse>>> getCategories(String language) {
        Language lang;
        try {
            lang = Language.valueOf(language.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported language '" + language + "'. Accepted values: AZ, EN, AR");
        }

        List<CategoryLocalizedResponse> responses = categoryRepository.findAll().stream()
                .map(category -> {
                    // Fall back to EN so a category missing the requested-language translation
                    // (e.g. AR) still appears in the menu instead of failing the whole list.
                    ProductCategoryTranslation tr = translationRepository
                            .findByCategoryIdAndLanguage(category.getId(), lang.name())
                            .or(() -> translationRepository.findByCategoryIdAndLanguage(category.getId(), "EN"))
                            .orElse(null);

                    CategoryLocalizedResponse resp = new CategoryLocalizedResponse(
                            category.getId(),
                            category.getParent() != null ? category.getParent().getId() : null,
                            category.getStatus(),
                            tr != null ? tr.getName() : null,
                            tr != null ? tr.getDescription() : null,
                            tr != null ? tr.getSlug() : null,
                            category.getCreatedAt(),
                            category.getUpdatedAt());
                    resp.setIcon(category.getIcon());
                    return resp;
                })
                .toList();

        return ApiResponse.success(responses, "Categories fetched successfully");
    }

    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(UUID id) {
        ProductCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Category not found with id: " + id);
        }
        List<ProductCategoryTranslation> translations = translationRepository.findAllByCategoryId(id);
        return ApiResponse.success(buildResponse(category, translations), "Category fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(UUID id, UpdateCategoryRequest request) {
        ProductCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Category not found with id: " + id);
        }

        // Update parent if provided in request body
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "A category cannot be its own parent");
            }
            ProductCategory parent = categoryRepository.findById(request.getParentId()).orElse(null);
            if (parent == null) {
                return ApiResponse.failure(HttpStatus.NOT_FOUND, "Parent category not found with id: " + request.getParentId());
            }
            category.setParent(parent);
        }

        // Update status if provided
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }

        // Update icon if provided ("" clears it back to none)
        if (request.getIcon() != null) {
            category.setIcon(request.getIcon().isBlank() ? null : request.getIcon());
        }

        categoryRepository.save(category);

        // Update translations if provided
        if (request.getTranslations() != null) {
            UpdateCategoryRequest.Translations tr = request.getTranslations();
            updateTranslation(id, "AZ", tr.getNameAz(), tr.getDescriptionAz(), tr.getSlugAz());
            updateTranslation(id, "EN", tr.getNameEn(), tr.getDescriptionEn(), tr.getSlugEn());
            updateTranslation(id, "AR", tr.getNameAr(), tr.getDescriptionAr(), tr.getSlugAr());
        }

        List<ProductCategoryTranslation> updatedTranslations = translationRepository.findAllByCategoryId(id);
        return ApiResponse.success(buildResponse(category, updatedTranslations), "Category updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteCategory(UUID id) {
        ProductCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Category not found with id: " + id);
        }
        category.setStatus("INACTIVE");
        categoryRepository.save(category);
        return ApiResponse.success(null, "Category deleted successfully");
    }

    // ========================
    // Private helpers
    // ========================

    private void updateTranslation(UUID categoryId, String language, String name, String description, String slug) {
        ProductCategoryTranslation translation = translationRepository
                .findByCategoryIdAndLanguage(categoryId, language)
                .orElse(null);
        if (translation == null) return;

        if (slug != null && !slug.equals(translation.getSlug())) {
            if (translationRepository.existsBySlugAndLanguageAndIdNot(slug, language, translation.getId())) {
                throw new IllegalArgumentException("Slug '" + slug + "' is already taken for language " + language);
            }
            translation.setSlug(slug);
        }
        if (name != null) translation.setName(name);
        if (description != null) translation.setDescription(description);
        translationRepository.save(translation);
    }

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
        response.setIcon(category.getIcon());
        response.setCreatedAt(category.getCreatedAt());
        response.setUpdatedAt(category.getUpdatedAt());
        response.setTranslations(translationDtos);
        return response;
    }
}
