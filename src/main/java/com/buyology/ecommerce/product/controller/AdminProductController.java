package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.CreateProductRequest;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.dto.UpdateProductRequest;
import com.buyology.ecommerce.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/product")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Product", description = "Admin APIs for managing all products")
public class AdminProductController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public AdminProductController(ProductService productService, ObjectMapper objectMapper) {
        this.productService = productService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create a new product with translations (AZ/EN/AR), media files, variants, and accessories")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = AdminProductController.CreateProductForm.class),
                    encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)
            )
    )
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Parameter(hidden = true) @RequestPart("request") String requestJson,
            @Parameter(hidden = true) @RequestPart(value = "files", required = false) List<MultipartFile> mediaFiles)
            throws Exception {

        CreateProductRequest request = objectMapper.readValue(requestJson, CreateProductRequest.class);
        return productService.createProduct(request, mediaFiles);
    }

    @Operation(summary = "Partially update a product (all fields optional) and edit its media")
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = AdminProductController.UpdateProductForm.class),
                    encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)
            )
    )
    @PatchMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID productId,
            @Parameter(hidden = true) @RequestPart("request") String requestJson,
            @Parameter(hidden = true) @RequestPart(value = "files", required = false) List<MultipartFile> newFiles)
            throws Exception {

        UpdateProductRequest request = objectMapper.readValue(requestJson, UpdateProductRequest.class);
        return productService.updateProduct(productId, request, newFiles);
    }

    @Operation(summary = "Get all products (all statuses) — unpaged")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam String lang) {
        return productService.getAllProductsAdmin(lang);
    }

    @Operation(summary = "Paginated + searchable product list (all statuses)")
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.common.response.PageResponse<ProductResponse>>> getAllProductsPaged(
            @RequestParam String lang,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.getAllProductsAdminPaged(lang, search, status, page, size);
    }

    @Operation(summary = "Product status counts for the admin dashboard")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getProductStats() {
        return productService.getAdminProductStats();
    }

    @Operation(summary = "Get product by ID with all details including media (all statuses)")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable UUID productId,
            @RequestParam String lang) {
        return productService.getProductByIdAdmin(productId, lang);
    }

    @Operation(summary = "Get all products by category (excludes trash)")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam String lang) {
        return productService.getProductsByCategoryAdmin(categoryId, lang);
    }

    @Operation(summary = "Activate or deactivate a product (status ACTIVE / INACTIVE)")
    @PatchMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<Void>> setProductStatus(
            @PathVariable UUID productId,
            @RequestParam String status) {
        return productService.setProductStatus(productId, status);
    }

    @Operation(summary = "Soft-delete a product — moves it to trash (auto-purged after 30 days)")
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID productId) {
        return productService.softDeleteProduct(productId);
    }

    @Operation(summary = "Get all products in trash")
    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getTrash(@RequestParam String lang) {
        return productService.getTrash(lang);
    }

    @Operation(summary = "Restore a trashed product back to active")
    @PutMapping("/{productId}/restore")
    public ResponseEntity<ApiResponse<ProductResponse>> restoreProduct(
            @PathVariable UUID productId,
            @RequestParam String lang) {
        return productService.restoreFromTrash(productId, lang);
    }

    @Operation(summary = "Manually trigger Elasticsearch reindexing for all products",
            description = "Deletes the existing index and rebuilds it from the database. Use this if search results are out of sync.")
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Void>> reindex() {
        return productService.reindexElasticsearch();
    }

    private static class CreateProductForm {

        @Schema(
                description = "Product creation request as JSON",
                implementation = CreateProductRequest.class)
        public Object request;

        @ArraySchema(schema = @Schema(
                type = "string",
                format = "binary",
                description = "Optional media files (images or videos)"))
        public List<MultipartFile> files;
    }

    private static class UpdateProductForm {

        @Schema(
                description = "Partial product update as JSON (all fields optional)",
                implementation = UpdateProductRequest.class)
        public Object request;

        @ArraySchema(schema = @Schema(
                type = "string",
                format = "binary",
                description = "Optional new media files to append"))
        public List<MultipartFile> files;
    }
}
