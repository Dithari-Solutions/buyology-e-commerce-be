package com.buyology.ecommerce.courier.profile.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.courier.profile.domain.CourierProfile;
import com.buyology.ecommerce.courier.profile.dto.CourierProfileResponse;
import com.buyology.ecommerce.courier.profile.dto.CreateCourierProfileRequest;
import com.buyology.ecommerce.courier.profile.dto.UpdateCourierProfileRequest;
import com.buyology.ecommerce.courier.profile.repository.CourierProfileRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CourierProfileService {

    private final CourierProfileRepository repo;
    private final StoreRepository storeRepository;

    public CourierProfileService(CourierProfileRepository repo, StoreRepository storeRepository) {
        this.repo = repo;
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<CourierProfileResponse>>> listByStore(UUID storeId, boolean activeOnly) {
        List<CourierProfile> couriers = activeOnly
                ? repo.findByStoreIdAndActiveTrueOrderByFirstNameAsc(storeId)
                : repo.findByStoreIdOrderByCreatedAtDesc(storeId);
        return ApiResponse.success(couriers.stream().map(CourierProfileResponse::from).toList(), "Couriers fetched");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<CourierProfileResponse>> getById(UUID id) {
        return repo.findById(id)
                .map(c -> ApiResponse.success(CourierProfileResponse.from(c), "Courier fetched"))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Courier not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<CourierProfileResponse>> create(CreateCourierProfileRequest req) {
        if (!storeRepository.existsById(req.getStoreId())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Store not found");
        }
        CourierProfile c = new CourierProfile();
        c.setStoreId(req.getStoreId());
        c.setFirstName(req.getFirstName());
        c.setLastName(req.getLastName());
        c.setPhone(req.getPhone());
        c.setEmail(req.getEmail());
        c.setVehicleType(req.getVehicleType());
        c.setActive(req.getActive() == null || req.getActive());
        return ApiResponse.created(CourierProfileResponse.from(repo.save(c)), "Courier created");
    }

    @Transactional
    public ResponseEntity<ApiResponse<CourierProfileResponse>> update(UUID id, UpdateCourierProfileRequest req) {
        CourierProfile c = repo.findById(id).orElse(null);
        if (c == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Courier not found");
        if (req.getFirstName() != null) c.setFirstName(req.getFirstName());
        if (req.getLastName() != null) c.setLastName(req.getLastName());
        if (req.getPhone() != null) c.setPhone(req.getPhone());
        if (req.getEmail() != null) c.setEmail(req.getEmail());
        if (req.getVehicleType() != null) c.setVehicleType(req.getVehicleType());
        if (req.getActive() != null) c.setActive(req.getActive());
        return ApiResponse.success(CourierProfileResponse.from(repo.save(c)), "Courier updated");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(UUID id) {
        if (!repo.existsById(id)) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Courier not found");
        repo.deleteById(id);
        return ApiResponse.success(null, "Courier deleted");
    }
}
