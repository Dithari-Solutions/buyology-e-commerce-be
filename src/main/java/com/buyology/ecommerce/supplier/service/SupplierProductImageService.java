package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.common.exception.FileValidationException;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.FileValidationUtils;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.product.repository.ProductMediaRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles supplier product image uploads. Wraps {@link FileValidationUtils#validateSupplierProductImages}
 * (PNG/WebP, ≤ 5 MB, ≤ 8 images, transparent-background heuristic for PNG).
 */
@Service
public class SupplierProductImageService {

    private static final Logger log = LoggerFactory.getLogger(SupplierProductImageService.class);

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductMediaRepository productMediaRepository;
    private final ContaboObjectService contaboObjectService;

    public SupplierProductImageService(
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            ProductMediaRepository productMediaRepository,
            ContaboObjectService contaboObjectService) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.productMediaRepository = productMediaRepository;
        this.contaboObjectService = contaboObjectService;
    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadImages(UUID productId, List<MultipartFile> files) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !supplier.getId().equals(product.getSupplierId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        if (files == null || files.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "No files provided");
        }

        // PNG/WebP only, ≤ 5 MB, ≤ 8 images, transparent-background heuristic for PNG.
        try {
            FileValidationUtils.validateSupplierProductImages(files);
        } catch (FileValidationException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        // Determine the next order index so newly uploaded images sit after any existing ones.
        int nextIndex = (int) productMediaRepository.findAll().stream()
                .filter(m -> m.getProduct() != null && productId.equals(m.getProduct().getId()))
                .count();

        java.util.List<String> uploadedUrls = new java.util.ArrayList<>();
        try {
            for (MultipartFile f : files) {
                String safeName = sanitize(f.getOriginalFilename());
                String key = "products/" + productId + "/" + UUID.randomUUID() + "-" + safeName;
                String url = contaboObjectService.uploadFile(key, f);

                ProductMedia media = new ProductMedia(
                        product, ProductMedia.MediaType.IMAGE, url, null,
                        nextIndex == 0, // first image becomes primary if there were none
                        nextIndex);
                productMediaRepository.save(media);
                uploadedUrls.add(url);
                nextIndex++;
            }
        } catch (Exception e) {
            log.error("Supplier image upload failed for product {}: {}", productId, e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Upload failed. Please try again.");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("uploaded", uploadedUrls.size());
        body.put("urls", uploadedUrls);
        return ApiResponse.success(body, "Images uploaded");
    }

    private Supplier resolveCurrentSupplier() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) return null;
            return supplierRepository.findByUserId(userId).orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve current supplier: {}", e.getMessage());
            return null;
        }
    }

    private String sanitize(String name) {
        if (name == null) return "image.png";
        String stripped = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return stripped.length() > 80 ? stripped.substring(0, 80) : stripped;
    }
}
