package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.dto.UpdateProductRequest;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.service.ProductService;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.domain.SupplierProductChangeRequest;
import com.buyology.ecommerce.supplier.domain.SupplierProductChangeRequest.Action;
import com.buyology.ecommerce.supplier.domain.SupplierProductChangeRequest.Status;
import com.buyology.ecommerce.supplier.dto.SupplierProductChangeResponse;
import com.buyology.ecommerce.supplier.repository.SupplierProductChangeRequestRepository;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Supplier-initiated product change workflow. Suppliers file EDIT/DELETE/RESTORE
 * requests; a superadmin approves (action applied) or rejects. Suppliers never
 * mutate their products directly.
 */
@Service
public class SupplierProductChangeService {

    private final SupplierProductChangeRequestRepository changeRepo;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public SupplierProductChangeService(SupplierProductChangeRequestRepository changeRepo,
                                        SupplierRepository supplierRepository,
                                        ProductRepository productRepository,
                                        ProductService productService,
                                        ObjectMapper objectMapper) {
        this.changeRepo = changeRepo;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.objectMapper = objectMapper;
    }

    // ── Supplier: file requests ───────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<SupplierProductChangeResponse>> requestEdit(
            UUID productId, UpdateProductRequest request) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        Product product = ownedProductOr404(productId, supplier);
        if (product == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        if ("DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Restore the product from trash before editing it");
        }
        if (changeRepo.existsByProductIdAndStatus(productId, Status.PENDING)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "There is already a pending request for this product");
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Invalid edit payload");
        }
        return ApiResponse.created(create(supplier.getId(), productId, Action.EDIT, payload),
                "Edit request submitted for admin approval");
    }

    @Transactional
    public ResponseEntity<ApiResponse<SupplierProductChangeResponse>> requestDelete(UUID productId) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        Product product = ownedProductOr404(productId, supplier);
        if (product == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        if ("DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Product is already in trash");
        }
        if (changeRepo.existsByProductIdAndStatus(productId, Status.PENDING)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "There is already a pending request for this product");
        }
        return ApiResponse.created(create(supplier.getId(), productId, Action.DELETE, null),
                "Delete request submitted for admin approval");
    }

    @Transactional
    public ResponseEntity<ApiResponse<SupplierProductChangeResponse>> requestRestore(UUID productId) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        Product product = ownedProductOr404(productId, supplier);
        if (product == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        if (!"DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Product is not in trash");
        }
        if (changeRepo.existsByProductIdAndStatus(productId, Status.PENDING)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "There is already a pending request for this product");
        }
        return ApiResponse.created(create(supplier.getId(), productId, Action.RESTORE, null),
                "Restore request submitted for admin approval");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<SupplierProductChangeResponse>>> listForSupplier(Pageable pageable) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        return ApiResponse.success(
                changeRepo.findAllBySupplierId(supplier.getId(), pageable).map(SupplierProductChangeResponse::from),
                "Change requests");
    }

    // ── Admin (superadmin): review ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<SupplierProductChangeResponse>>> listForAdmin(
            Status status, Pageable pageable) {
        Page<SupplierProductChangeRequest> page = (status == null)
                ? changeRepo.findAll(pageable)
                : changeRepo.findAllByStatus(status, pageable);
        return ApiResponse.success(page.map(SupplierProductChangeResponse::from), "Change requests");
    }

    @Transactional
    public ResponseEntity<ApiResponse<SupplierProductChangeResponse>> approve(UUID id, UUID adminId) {
        SupplierProductChangeRequest req = changeRepo.findById(id).orElse(null);
        if (req == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Change request not found");
        if (req.getStatus() != Status.PENDING) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Request is already " + req.getStatus());
        }

        // Apply the requested action.
        switch (req.getAction()) {
            case DELETE -> productService.softDeleteProduct(req.getProductId());
            case RESTORE -> applyRestore(req.getProductId());
            case EDIT -> {
                try {
                    UpdateProductRequest payload =
                            objectMapper.readValue(req.getPayload(), UpdateProductRequest.class);
                    productService.updateProduct(req.getProductId(), payload, null);
                } catch (Exception e) {
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                            "Could not apply edit: " + e.getMessage());
                }
            }
        }

        req.setStatus(Status.APPROVED);
        req.setReviewedAt(Instant.now());
        req.setReviewedBy(adminId);
        changeRepo.save(req);
        return ApiResponse.success(SupplierProductChangeResponse.from(req), "Change request approved");
    }

    @Transactional
    public ResponseEntity<ApiResponse<SupplierProductChangeResponse>> reject(
            UUID id, UUID adminId, String reason) {
        SupplierProductChangeRequest req = changeRepo.findById(id).orElse(null);
        if (req == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Change request not found");
        if (req.getStatus() != Status.PENDING) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Request is already " + req.getStatus());
        }
        req.setStatus(Status.REJECTED);
        req.setRejectionReason(reason);
        req.setReviewedAt(Instant.now());
        req.setReviewedBy(adminId);
        changeRepo.save(req);
        return ApiResponse.success(SupplierProductChangeResponse.from(req), "Change request rejected");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyRestore(UUID productId) {
        productRepository.findById(productId).ifPresent(product -> {
            if ("DELETED".equals(product.getStatus())) {
                product.setStatus("INACTIVE");
                product.setDeletedAt(null);
                product.setIsActive(false);
                productRepository.save(product);
            }
        });
    }

    private SupplierProductChangeResponse create(UUID supplierId, UUID productId, Action action, String payload) {
        SupplierProductChangeRequest req = new SupplierProductChangeRequest();
        req.setSupplierId(supplierId);
        req.setProductId(productId);
        req.setAction(action);
        req.setPayload(payload);
        req.setStatus(Status.PENDING);
        return SupplierProductChangeResponse.from(changeRepo.save(req));
    }

    private Product ownedProductOr404(UUID productId, Supplier supplier) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !supplier.getId().equals(product.getSupplierId())) return null;
        return product;
    }

    private Supplier resolveCurrentSupplier() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) return null;
            return supplierRepository.findByUserId(userId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
