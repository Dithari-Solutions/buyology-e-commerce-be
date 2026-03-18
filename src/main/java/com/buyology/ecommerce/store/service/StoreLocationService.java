package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.dto.CreateStoreLocationRequest;
import com.buyology.ecommerce.store.dto.StoreLocationResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreLocationRequest;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StoreLocationService {

    private final StoreLocationRepository locationRepository;
    private final StoreRepository storeRepository;

    public StoreLocationService(StoreLocationRepository locationRepository,
                                StoreRepository storeRepository) {
        this.locationRepository = locationRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreLocationResponse>> createLocation(
            UUID storeId, CreateStoreLocationRequest request) {

        Store store = storeRepository.findByIdAndDeletedAtIsNull(storeId).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + storeId);
        }

        // Enforce single-primary constraint: clear existing primary if the new one is primary
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            locationRepository.findByStoreIdAndIsPrimary(storeId, true).ifPresent(existing -> {
                existing.setIsPrimary(false);
                locationRepository.save(existing);
            });
        }

        StoreLocation location = new StoreLocation(
                store,
                request.getBranchName(),
                request.getAddress(),
                request.getCity(),
                request.getCountry(),
                request.getLatitude(),
                request.getLongitude());

        location.setState(request.getState());
        location.setPostalCode(request.getPostalCode());
        location.setIsPrimary(Boolean.TRUE.equals(request.getIsPrimary()));

        StoreLocation saved = locationRepository.save(location);
        return ApiResponse.created(toResponse(saved), "Store location created successfully");
    }

    public ResponseEntity<ApiResponse<List<StoreLocationResponse>>> getLocationsByStore(UUID storeId) {
        if (!storeRepository.findByIdAndDeletedAtIsNull(storeId).isPresent()) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + storeId);
        }
        List<StoreLocationResponse> locations = locationRepository.findAllByStoreId(storeId).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(locations, "Store locations fetched successfully");
    }

    public ResponseEntity<ApiResponse<StoreLocationResponse>> getLocationById(UUID locationId) {
        StoreLocation location = locationRepository.findById(locationId).orElse(null);
        if (location == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Location not found with id: " + locationId);
        }
        return ApiResponse.success(toResponse(location), "Location fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreLocationResponse>> updateLocation(
            UUID locationId, UpdateStoreLocationRequest request) {

        StoreLocation location = locationRepository.findById(locationId).orElse(null);
        if (location == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Location not found with id: " + locationId);
        }

        // If promoting to primary, demote the existing primary
        if (Boolean.TRUE.equals(request.getIsPrimary()) && !Boolean.TRUE.equals(location.getIsPrimary())) {
            UUID storeId = location.getStore().getId();
            locationRepository.findByStoreIdAndIsPrimary(storeId, true).ifPresent(existing -> {
                if (!existing.getId().equals(locationId)) {
                    existing.setIsPrimary(false);
                    locationRepository.save(existing);
                }
            });
        }

        if (request.getBranchName() != null) location.setBranchName(request.getBranchName());
        if (request.getAddress() != null) location.setAddress(request.getAddress());
        if (request.getCity() != null) location.setCity(request.getCity());
        if (request.getState() != null) location.setState(request.getState());
        if (request.getCountry() != null) location.setCountry(request.getCountry());
        if (request.getPostalCode() != null) location.setPostalCode(request.getPostalCode());
        if (request.getLatitude() != null) location.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) location.setLongitude(request.getLongitude());
        if (request.getIsPrimary() != null) location.setIsPrimary(request.getIsPrimary());
        if (request.getIsActive() != null) location.setIsActive(request.getIsActive());

        StoreLocation saved = locationRepository.save(location);
        return ApiResponse.success(toResponse(saved), "Location updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteLocation(UUID locationId) {
        StoreLocation location = locationRepository.findById(locationId).orElse(null);
        if (location == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Location not found with id: " + locationId);
        }
        location.setIsActive(false);
        locationRepository.save(location);
        return ApiResponse.success(null, "Location deactivated successfully");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private StoreLocationResponse toResponse(StoreLocation l) {
        StoreLocationResponse r = new StoreLocationResponse();
        r.setId(l.getId());
        r.setStoreId(l.getStore().getId());
        r.setBranchName(l.getBranchName());
        r.setAddress(l.getAddress());
        r.setCity(l.getCity());
        r.setState(l.getState());
        r.setCountry(l.getCountry());
        r.setPostalCode(l.getPostalCode());
        r.setLatitude(l.getLatitude());
        r.setLongitude(l.getLongitude());
        r.setIsPrimary(l.getIsPrimary());
        r.setIsActive(l.getIsActive());
        r.setCreatedAt(l.getCreatedAt());
        r.setUpdatedAt(l.getUpdatedAt());
        return r;
    }
}
