package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.CategoryResponse;
import com.buyology.ecommerce.product.dto.CreateCategoryRequest;
import com.buyology.ecommerce.product.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/category")
@Tag(name = "Product Category", description = "APIs for managing product categories and subcategories")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    public ProductCategoryController(ProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(
            summary = "Create a category or subcategory",
            description = "Creates a root-level category when parentId is omitted, "
                    + "or a subcategory when a valid parentId is supplied. "
                    + "Translations in Azerbaijani, English, and Arabic are all required. "
                    + "Slugs must be globally unique per language.")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestBody @Valid CreateCategoryRequest request) {
        return categoryService.createCategory(request);
    }
}
