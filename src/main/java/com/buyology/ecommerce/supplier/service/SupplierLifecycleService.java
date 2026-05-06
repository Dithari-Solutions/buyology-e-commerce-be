package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.service.ProductService;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.domain.Supplier.SupplierStatus;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SupplierLifecycleService.class);
    private static final int TRASH_RETENTION_DAYS = 30;

    private final SupplierRepository supplierRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public SupplierLifecycleService(
            SupplierRepository supplierRepository,
            AuthCredentialRepository authCredentialRepository,
            ProductRepository productRepository,
            ProductService productService) {
        this.supplierRepository = supplierRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.productRepository = productRepository;
        this.productService = productService;
    }

    // ── Self (current supplier) ─────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> selfFreeze() {
        Supplier s = resolveCurrentSupplier();
        if (s == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        return doFreeze(s, "self:" + s.getUserId());
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> selfUnfreeze() {
        Supplier s = resolveCurrentSupplier();
        if (s == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        return doUnfreeze(s, "self:" + s.getUserId());
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> selfSoftDelete() {
        Supplier s = resolveCurrentSupplier();
        if (s == null) return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        return doSoftDelete(s, "self:" + s.getUserId());
    }

    // ── Admin ───────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminFreeze(UUID supplierId, String performedBy) {
        Supplier s = supplierRepository.findById(supplierId).orElse(null);
        if (s == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier not found");
        return doFreeze(s, performedBy);
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminUnfreeze(UUID supplierId, String performedBy) {
        Supplier s = supplierRepository.findById(supplierId).orElse(null);
        if (s == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier not found");
        return doUnfreeze(s, performedBy);
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminSoftDelete(UUID supplierId, String performedBy) {
        Supplier s = supplierRepository.findById(supplierId).orElse(null);
        if (s == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier not found");
        return doSoftDelete(s, performedBy);
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminRestore(UUID supplierId, String performedBy) {
        Supplier s = supplierRepository.findById(supplierId).orElse(null);
        if (s == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier not found");
        if (s.getStatus() != SupplierStatus.TRASHED) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Supplier is not in trash");
        }
        s.setStatus(SupplierStatus.ACTIVE);
        s.setDeletedAt(null);
        s.setDeletedBy(null);
        s.setFrozenAt(null);
        s.setFrozenBy(null);
        supplierRepository.save(s);
        toggleAuthCredentials(s.getUserId(), true);
        log.info("Supplier {} restored from trash by {}", s.getId(), performedBy);
        return ApiResponse.success("restored", "Supplier restored");
    }

    // ── Internal lifecycle transitions ──────────────────────────────────────

    private ResponseEntity<ApiResponse<String>> doFreeze(Supplier s, String performedBy) {
        if (s.getStatus() == SupplierStatus.TRASHED) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Supplier is in trash; restore first");
        }
        if (s.getStatus() == SupplierStatus.SUSPENDED) {
            return ApiResponse.success("already_frozen", "Supplier already frozen");
        }
        s.setStatus(SupplierStatus.SUSPENDED);
        s.setFrozenAt(Instant.now());
        s.setFrozenBy(performedBy);
        supplierRepository.save(s);
        toggleAuthCredentials(s.getUserId(), false);
        log.info("Supplier {} frozen by {}", s.getId(), performedBy);
        return ApiResponse.success("frozen", "Supplier frozen");
    }

    private ResponseEntity<ApiResponse<String>> doUnfreeze(Supplier s, String performedBy) {
        if (s.getStatus() != SupplierStatus.SUSPENDED) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Supplier is not frozen");
        }
        s.setStatus(SupplierStatus.ACTIVE);
        s.setFrozenAt(null);
        s.setFrozenBy(null);
        supplierRepository.save(s);
        toggleAuthCredentials(s.getUserId(), true);
        log.info("Supplier {} unfrozen by {}", s.getId(), performedBy);
        return ApiResponse.success("unfrozen", "Supplier unfrozen");
    }

    private ResponseEntity<ApiResponse<String>> doSoftDelete(Supplier s, String performedBy) {
        if (s.getStatus() == SupplierStatus.TRASHED) {
            return ApiResponse.success("already_trashed", "Supplier already in trash");
        }
        s.setStatus(SupplierStatus.TRASHED);
        s.setDeletedAt(Instant.now());
        s.setDeletedBy(performedBy);
        supplierRepository.save(s);
        toggleAuthCredentials(s.getUserId(), false);
        log.info("Supplier {} moved to trash by {} (will purge after {} days)",
                s.getId(), performedBy, TRASH_RETENTION_DAYS);
        return ApiResponse.success("trashed", "Supplier moved to trash");
    }

    private void toggleAuthCredentials(UUID userId, boolean active) {
        List<AuthCredentials> creds = authCredentialRepository.findByUserId(userId);
        for (AuthCredentials c : creds) {
            c.setIsActive(active);
            authCredentialRepository.save(c);
        }
    }

    // ── Purge ───────────────────────────────────────────────────────────────

    @Transactional
    public int purgeExpiredTrash() {
        Instant cutoff = Instant.now().minus(TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
        List<Supplier> expired = supplierRepository.findByStatusAndDeletedAtBefore(SupplierStatus.TRASHED, cutoff);
        int purged = 0;
        for (Supplier s : expired) {
            try {
                List<Product> products = productRepository.findBySupplierId(s.getId(),
                        org.springframework.data.domain.Pageable.unpaged()).getContent();
                for (Product p : products) {
                    if (p.getDeletedAt() == null) {
                        productService.softDeleteProduct(p.getId());
                    }
                }
                supplierRepository.delete(s);
                purged++;
                log.info("Hard-deleted supplier {} after trash retention expired", s.getId());
            } catch (Exception e) {
                log.error("Failed to purge supplier {}: {}", s.getId(), e.getMessage(), e);
            }
        }
        return purged;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

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
