package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.Product.SupplierStatus;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import com.buyology.ecommerce.supplier.repository.SupplierStoreAssignmentRepository;
import com.buyology.ecommerce.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierPortalService {

    private static final Logger log = LoggerFactory.getLogger(SupplierPortalService.class);

    private final SupplierRepository supplierRepository;
    private final SupplierStoreAssignmentRepository storeAssignmentRepository;
    private final StoreRepository storeRepository;
    private final StoreProductRepository storeProductRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final EmailService emailService;
    private final ProductService productService;

    public SupplierPortalService(
            SupplierRepository supplierRepository,
            SupplierStoreAssignmentRepository storeAssignmentRepository,
            StoreRepository storeRepository,
            StoreProductRepository storeProductRepository,
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            EmailService emailService,
            ProductService productService) {
        this.supplierRepository = supplierRepository;
        this.storeAssignmentRepository = storeAssignmentRepository;
        this.storeRepository = storeRepository;
        this.storeProductRepository = storeProductRepository;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.emailService = emailService;
        this.productService = productService;
    }

    // ── Assigned stores ──────────────────────────────────────────────────────

    public ResponseEntity<ApiResponse<List<Store>>> getAssignedStores() {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        List<UUID> storeIds = storeAssignmentRepository.findStoreIdsBySupplierId(supplier.getId());
        List<Store> stores = storeRepository.findAllById(storeIds);
        return ApiResponse.success(stores, "Assigned stores");
    }

    // ── Supplier products ────────────────────────────────────────────────────

    public ResponseEntity<ApiResponse<Page<Product>>> getMyProducts(
            SupplierStatus supplierStatus, Pageable pageable) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        Page<Product> page = (supplierStatus != null)
                ? productRepository.findBySupplierIdAndSupplierStatus(supplier.getId(), supplierStatus, pageable)
                : productRepository.findBySupplierId(supplier.getId(), pageable);
        return ApiResponse.success(page, "Products");
    }

    // ── Submit product ───────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<UUID>> submitProduct(
            UUID categoryId,
            UUID storeId,
            String sku,
            BigDecimal storePrice,
            String productJson) {

        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }

        if (!storeAssignmentRepository.existsBySupplierIdAndStoreId(supplier.getId(), storeId)) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "You are not assigned to this store");
        }

        if (productRepository.existsBySku(sku)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "A product with this SKU already exists");
        }

        ProductCategory category = productCategoryRepository.findById(categoryId).orElse(null);
        if (category == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Invalid category");
        }

        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Store not found");
        }

        Product product = new Product();
        product.setCategory(category);
        product.setSku(sku);
        product.setStatus("INACTIVE");
        product.setIsActive(false);
        product.setSupplierId(supplier.getId());
        product.setSupplierStatus(SupplierStatus.PENDING_REVIEW);
        product.setAvailabilityStatus(Product.AvailabilityStatus.PRE_ORDER);
        product.setIsRefurbished(false);
        product.setIsSuperDeal(false);
        product.setIsLimitedStock(false);
        productRepository.save(product);

        StoreProduct storeProduct = new StoreProduct(store, product, storePrice);
        storeProduct.setIsActive(false);
        storeProductRepository.save(storeProduct);

        emailService.sendSupplierProductUnderReviewEmail(
                supplier.getContactEmail(),
                supplier.getBusinessName(),
                sku,
                sku);

        return ApiResponse.created(product.getId(), "Product submitted for review");
    }

    // ── Admin: approve supplier product ─────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> approveProduct(UUID productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || product.getSupplierId() == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier product not found");
        }
        product.setSupplierStatus(SupplierStatus.APPROVED);
        product.setStatus("ACTIVE");
        product.setSupplierRejectionReason(null);
        productRepository.save(product);

        // Approval no longer auto-publishes; the supplier must call /publish
        // (toggles Product.isActive + StoreProduct.isActive) to make the product visible.

        Supplier supplier = supplierRepository.findById(product.getSupplierId()).orElse(null);
        if (supplier != null) {
            emailService.sendSupplierProductApprovedEmail(
                    supplier.getContactEmail(),
                    supplier.getBusinessName(),
                    product.getSku(),
                    product.getSku());
        }

        return ApiResponse.success("approved", "Product approved; ready for supplier to publish");
    }

    // ── Admin: reject supplier product ───────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> rejectProduct(UUID productId, String reason) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || product.getSupplierId() == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier product not found");
        }
        product.setSupplierStatus(SupplierStatus.REJECTED);
        product.setSupplierRejectionReason(reason);
        productRepository.save(product);

        Supplier supplier = supplierRepository.findById(product.getSupplierId()).orElse(null);
        if (supplier != null) {
            emailService.sendSupplierProductRejectedEmail(
                    supplier.getContactEmail(),
                    supplier.getBusinessName(),
                    product.getSku(),
                    reason);
        }

        return ApiResponse.success("rejected", "Product rejected");
    }

    // ── Admin: list supplier products for review ─────────────────────────────

    public ResponseEntity<ApiResponse<Page<Product>>> listSupplierProductsForAdmin(
            SupplierStatus supplierStatus, UUID supplierId, Pageable pageable) {
        Page<Product> page;
        if (supplierId != null && supplierStatus != null) {
            page = productRepository.findBySupplierIdAndSupplierStatus(supplierId, supplierStatus, pageable);
        } else if (supplierId != null) {
            page = productRepository.findBySupplierId(supplierId, pageable);
        } else if (supplierStatus != null) {
            page = productRepository.findBySupplierStatus(supplierStatus, pageable);
        } else {
            page = productRepository.findBySupplierIdIsNotNull(pageable);
        }
        return ApiResponse.success(page, "Supplier products");
    }

    // ── Supplier draft/publish toggle ────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> publishProduct(UUID productId) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !supplier.getId().equals(product.getSupplierId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }
        if ("DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Product is in trash; restore first");
        }
        if (product.getSupplierStatus() != SupplierStatus.APPROVED) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Product must be approved by admin before publishing");
        }
        product.setIsActive(true);
        productRepository.save(product);

        storeProductRepository.findByProduct_Id(productId).ifPresent(sp -> {
            sp.setIsActive(true);
            storeProductRepository.save(sp);
        });
        return ApiResponse.success("published", "Product published");
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> draftProduct(UUID productId) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !supplier.getId().equals(product.getSupplierId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }
        product.setIsActive(false);
        productRepository.save(product);

        storeProductRepository.findByProduct_Id(productId).ifPresent(sp -> {
            sp.setIsActive(false);
            storeProductRepository.save(sp);
        });
        return ApiResponse.success("drafted", "Product moved to draft");
    }

    // ── Supplier soft-delete / trash / restore ───────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> softDeleteOwnProduct(UUID productId) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !supplier.getId().equals(product.getSupplierId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }
        return productService.softDeleteProduct(productId);
    }

    public ResponseEntity<ApiResponse<Page<Product>>> listOwnTrash(Pageable pageable) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        Page<Product> all = productRepository.findBySupplierId(supplier.getId(), pageable);
        List<Product> trashed = all.getContent().stream()
                .filter(p -> "DELETED".equals(p.getStatus()))
                .toList();
        Page<Product> page = new org.springframework.data.domain.PageImpl<>(
                trashed, pageable, trashed.size());
        return ApiResponse.success(page, "Trashed products");
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> restoreOwnProduct(UUID productId) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !supplier.getId().equals(product.getSupplierId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (!"DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Product is not in trash");
        }
        product.setStatus("INACTIVE");
        product.setDeletedAt(null);
        product.setIsActive(false);
        productRepository.save(product);
        return ApiResponse.success("restored", "Product restored to draft");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
}
