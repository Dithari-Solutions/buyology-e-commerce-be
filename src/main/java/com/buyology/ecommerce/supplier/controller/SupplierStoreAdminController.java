package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.buyology.ecommerce.supplier.domain.SupplierStoreAssignment;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import com.buyology.ecommerce.supplier.repository.SupplierStoreAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin endpoints for managing which stores a supplier is allowed to add products to.
 * Initial assignment happens at supplier-application approval time; this controller lets
 * admins add or remove stores after approval.
 */
@RestController
@RequestMapping("/api/admin/suppliers/{supplierId}/stores")
public class SupplierStoreAdminController {

    private final SupplierRepository supplierRepo;
    private final StoreRepository storeRepo;
    private final SupplierStoreAssignmentRepository assignmentRepo;

    public SupplierStoreAdminController(SupplierRepository supplierRepo,
                                        StoreRepository storeRepo,
                                        SupplierStoreAssignmentRepository assignmentRepo) {
        this.supplierRepo = supplierRepo;
        this.storeRepo = storeRepo;
        this.assignmentRepo = assignmentRepo;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('supplier:application:read')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@PathVariable UUID supplierId) {
        if (supplierRepo.findById(supplierId).isEmpty()) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier not found");
        }
        List<UUID> ids = assignmentRepo.findStoreIdsBySupplierId(supplierId);
        List<Store> stores = ids.isEmpty() ? List.of() : storeRepo.findAllById(ids);
        // Project to a small DTO — Store has a LAZY @ManyToOne to Country which
        // would trip Jackson with a ByteBuddy proxy if we returned the entity.
        List<Map<String, Object>> body = stores.stream()
                .map(s -> Map.<String, Object>of("id", s.getId(), "name", s.getName()))
                .toList();
        return ApiResponse.success(body, "Assigned stores");
    }

    @PostMapping("/{storeId}")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    @Transactional
    public ResponseEntity<ApiResponse<String>> assign(
            @PathVariable UUID supplierId,
            @PathVariable UUID storeId) {
        if (supplierRepo.findById(supplierId).isEmpty()) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier not found");
        }
        if (storeRepo.findById(storeId).isEmpty()) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found");
        }
        if (assignmentRepo.existsBySupplierIdAndStoreId(supplierId, storeId)) {
            return ApiResponse.success("already_assigned", "Supplier already assigned to this store");
        }
        assignmentRepo.save(new SupplierStoreAssignment(supplierId, storeId));
        return ApiResponse.success("assigned", "Store assigned to supplier");
    }

    @DeleteMapping("/{storeId}")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    @Transactional
    public ResponseEntity<ApiResponse<String>> unassign(
            @PathVariable UUID supplierId,
            @PathVariable UUID storeId) {
        int removed = assignmentRepo.deleteBySupplierIdAndStoreId(supplierId, storeId);
        if (removed == 0) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        return ApiResponse.success("unassigned", "Store unassigned from supplier");
    }
}
