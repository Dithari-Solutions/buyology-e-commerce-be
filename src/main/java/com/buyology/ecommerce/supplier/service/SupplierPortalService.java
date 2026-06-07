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
import com.buyology.ecommerce.supplier.dto.AssignedStoreResponse;
import com.buyology.ecommerce.supplier.dto.SupplierProductSummary;
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

    // ── Current supplier (self) ─────────────────────────────────────────────

    public ResponseEntity<ApiResponse<Supplier>> getCurrentSupplier() {
        Supplier s = resolveCurrentSupplier();
        if (s == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier account not found");
        return ApiResponse.success(s, "Current supplier");
    }

    // ── Assigned stores ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<AssignedStoreResponse>>> getAssignedStores() {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        List<UUID> storeIds = storeAssignmentRepository.findStoreIdsBySupplierId(supplier.getId());
        List<AssignedStoreResponse> stores = storeRepository.findAllById(storeIds).stream()
                .map(s -> new AssignedStoreResponse(
                        s.getId(),
                        s.getName(),
                        s.getSlug(),
                        s.getStatus() != null ? s.getStatus().name() : null))
                .toList();
        return ApiResponse.success(stores, "Assigned stores");
    }

    // ── Supplier products ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<SupplierProductSummary>>> getMyProducts(
            SupplierStatus supplierStatus, Pageable pageable) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        Page<Product> page = (supplierStatus != null)
                ? productRepository.findBySupplierIdAndSupplierStatus(supplier.getId(), supplierStatus, pageable)
                : productRepository.findBySupplierId(supplier.getId(), pageable);
        return ApiResponse.success(page.map(SupplierProductSummary::from), "Products");
    }

    /**
     * Full detail of one of the supplier's OWN products — same {@link ProductResponse}
     * shape the admin sees, but ownership-guarded so a supplier can only view products
     * tagged with their own supplierId.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.product.dto.ProductResponse>> getMyProductDetail(
            UUID productId, String lang) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !supplier.getId().equals(product.getSupplierId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }
        return productService.getProductByIdAdmin(productId, lang);
    }

    // ── Submit product (FULL — mirrors admin /api/admin/product/create) ──────

    /**
     * Run the full admin product-creation pipeline (translations, specs, colors,
     * variants, accessories, media) but tag the resulting product as supplier-
     * submitted (pending review, draft, hidden) and link it to the chosen store.
     */
    @Transactional
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.product.dto.ProductResponse>> submitProductFull(
            com.buyology.ecommerce.product.dto.CreateProductRequest request,
            java.util.List<org.springframework.web.multipart.MultipartFile> mediaFiles,
            UUID storeId,
            BigDecimal storePrice) {

        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        if (storeId == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "storeId is required");
        }
        if (!storeAssignmentRepository.existsBySupplierIdAndStoreId(supplier.getId(), storeId)) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "You are not assigned to this store");
        }

        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Store not found");
        }

        // Validate uploaded media against the supplier image rules (PNG/WebP, ≤5 MB,
        // ≤8 images, transparent background heuristic for PNG).
        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            try {
                com.buyology.ecommerce.common.utils.FileValidationUtils.validateSupplierProductImages(mediaFiles);
            } catch (com.buyology.ecommerce.common.exception.FileValidationException e) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }

        // Force supplier moderation flags regardless of what the client posted.
        request.setStatus("INACTIVE");

        ResponseEntity<ApiResponse<com.buyology.ecommerce.product.dto.ProductResponse>> resp =
                productService.createProductForSupplier(request, mediaFiles, supplier.getId());
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null
                || resp.getBody().getData() == null) {
            return resp;
        }
        UUID productId = resp.getBody().getData().getId();

        // Link to the chosen store at the supplier-provided price (hidden until publish).
        productRepository.findById(productId).ifPresent(p -> {
            StoreProduct sp = new StoreProduct(store, p, storePrice);
            sp.setIsActive(false);
            storeProductRepository.save(sp);
        });

        emailService.sendSupplierProductUnderReviewEmail(
                supplier.getContactEmail(),
                supplier.getBusinessName(),
                resp.getBody().getData().getSku(),
                resp.getBody().getData().getSku());

        return resp;
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
        product.setIsActive(true);
        product.setSupplierRejectionReason(null);
        productRepository.save(product);

        // Approval auto-publishes: flip the linked StoreProduct visible too.
        storeProductRepository.findByProduct_Id(productId).ifPresent(sp -> {
            sp.setIsActive(true);
            storeProductRepository.save(sp);
        });

        Supplier supplier = supplierRepository.findById(product.getSupplierId()).orElse(null);
        if (supplier != null) {
            emailService.sendSupplierProductApprovedEmail(
                    supplier.getContactEmail(),
                    supplier.getBusinessName(),
                    product.getSku(),
                    product.getSku());
        }

        return ApiResponse.success("approved", "Product approved and published");
    }

    // ── Admin: reject supplier product ───────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> rejectProduct(UUID productId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || product.getSupplierId() == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier product not found");
        }
        product.setSupplierStatus(SupplierStatus.REJECTED);
        product.setSupplierRejectionReason(reason.trim());
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

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<SupplierProductSummary>>> listSupplierProductsForAdmin(
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
        return ApiResponse.success(page.map(SupplierProductSummary::from), "Supplier products");
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

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<SupplierProductSummary>>> listOwnTrash(Pageable pageable) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");

        Page<Product> all = productRepository.findBySupplierId(supplier.getId(), pageable);
        List<SupplierProductSummary> trashed = all.getContent().stream()
                .filter(p -> "DELETED".equals(p.getStatus()))
                .map(SupplierProductSummary::from)
                .toList();
        Page<SupplierProductSummary> page = new org.springframework.data.domain.PageImpl<>(
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
