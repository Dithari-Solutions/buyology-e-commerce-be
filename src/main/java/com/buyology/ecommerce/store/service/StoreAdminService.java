package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreAdmin;
import com.buyology.ecommerce.store.dto.AssignStoreAdminRequest;
import com.buyology.ecommerce.store.dto.StoreAdminResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreAdminRequest;
import com.buyology.ecommerce.store.repository.StoreAdminRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StoreAdminService {

    private final StoreAdminRepository storeAdminRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    public StoreAdminService(StoreAdminRepository storeAdminRepository,
                             StoreRepository storeRepository,
                             UserRepository userRepository) {
        this.storeAdminRepository = storeAdminRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreAdminResponse>> assignAdmin(
            UUID storeId, AssignStoreAdminRequest request) {

        Store store = storeRepository.findByIdAndDeletedAtIsNull(storeId).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + storeId);
        }

        Users user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND,
                    "User not found with id: " + request.getUserId());
        }

        if (storeAdminRepository.existsByStoreIdAndUserId(storeId, request.getUserId())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "User is already assigned to this store");
        }

        Users assignedBy = null;
        if (request.getAssignedById() != null) {
            assignedBy = userRepository.findById(request.getAssignedById()).orElse(null);
            if (assignedBy == null) {
                return ApiResponse.failure(HttpStatus.NOT_FOUND,
                        "Assigner user not found with id: " + request.getAssignedById());
            }
        }

        StoreAdmin admin = new StoreAdmin(store, user, request.getStoreRole(), assignedBy);
        StoreAdmin saved = storeAdminRepository.save(admin);
        return ApiResponse.created(toResponse(saved), "Store admin assigned successfully");
    }

    public ResponseEntity<ApiResponse<List<StoreAdminResponse>>> getAdminsByStore(UUID storeId) {
        if (!storeRepository.findByIdAndDeletedAtIsNull(storeId).isPresent()) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + storeId);
        }
        List<StoreAdminResponse> admins = storeAdminRepository.findAllByStoreId(storeId).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(admins, "Store admins fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<StoreAdminResponse>>> getActiveAdminsByStore(UUID storeId) {
        if (!storeRepository.findByIdAndDeletedAtIsNull(storeId).isPresent()) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + storeId);
        }
        List<StoreAdminResponse> admins = storeAdminRepository.findAllByStoreIdAndIsActive(storeId, true).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(admins, "Active store admins fetched successfully");
    }

    public ResponseEntity<ApiResponse<StoreAdminResponse>> getAdminById(UUID adminId) {
        StoreAdmin admin = storeAdminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store admin not found with id: " + adminId);
        }
        return ApiResponse.success(toResponse(admin), "Store admin fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreAdminResponse>> updateAdmin(
            UUID adminId, UpdateStoreAdminRequest request) {

        StoreAdmin admin = storeAdminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store admin not found with id: " + adminId);
        }

        if (request.getStoreRole() != null) admin.setStoreRole(request.getStoreRole());
        if (request.getIsActive() != null) admin.setIsActive(request.getIsActive());

        StoreAdmin saved = storeAdminRepository.save(admin);
        return ApiResponse.success(toResponse(saved), "Store admin updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeAdmin(UUID adminId) {
        StoreAdmin admin = storeAdminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store admin not found with id: " + adminId);
        }
        admin.setIsActive(false);
        storeAdminRepository.save(admin);
        return ApiResponse.success(null, "Store admin removed successfully");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private StoreAdminResponse toResponse(StoreAdmin a) {
        StoreAdminResponse r = new StoreAdminResponse();
        r.setId(a.getId());
        r.setStoreId(a.getStore().getId());
        r.setStoreName(a.getStore().getName());
        r.setUserId(a.getUser().getId());
        r.setUserFirstName(a.getUser().getFirstName());
        r.setUserLastName(a.getUser().getLastName());
        r.setStoreRole(a.getStoreRole());
        r.setIsActive(a.getIsActive());
        r.setAssignedById(a.getAssignedBy() != null ? a.getAssignedBy().getId() : null);
        r.setAssignedAt(a.getAssignedAt());
        r.setCreatedAt(a.getCreatedAt());
        r.setUpdatedAt(a.getUpdatedAt());
        return r;
    }
}
