package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.CategoryLocalizedResponse;
import com.buyology.ecommerce.product.dto.CategoryResponse;
import com.buyology.ecommerce.product.dto.CreateCategoryRequest;
import com.buyology.ecommerce.product.dto.UpdateCategoryRequest;
import com.buyology.ecommerce.product.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/category")
@Tag(name = "Product Category", description = "APIs for managing product categories and subcategories")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    public ProductCategoryController(ProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Get all categories in a specific language",
            description = "Returns all categories with only the translation for the requested language. "
                    + "Accepted values for lang: AZ, EN, AR (case-insensitive).")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryLocalizedResponse>>> getCategories(
            @RequestParam String lang) {
        return categoryService.getCategories(lang);
    }

    @Operation(
            summary = "Create a category or subcategory",
            description = "Creates a root-level category when parentId is omitted, "
                    + "or a subcategory when a valid parentId is supplied. "
                    + "Translations in Azerbaijani, English, and Arabic are all required. "
                    + "Slugs must be globally unique per language.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:category:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestBody @Valid CreateCategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @Operation(summary = "Get a single category by ID",
            description = "Returns the full category with all language translations.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable UUID id) {
        return categoryService.getCategoryById(id);
    }

    @Operation(summary = "Update a category",
            description = "Partially updates a category. All fields are optional — only provided fields are applied. "
                    + "If translations are provided, only the supplied language fields are updated.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:category:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @Operation(summary = "Delete (deactivate) a category",
            description = "Soft-deletes the category by setting its status to INACTIVE.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:category:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        return categoryService.deleteCategory(id);
    }
}
