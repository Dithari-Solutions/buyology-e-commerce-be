package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.domain.StoreOperatingHours;
import com.buyology.ecommerce.store.dto.CreateOperatingHoursRequest;
import com.buyology.ecommerce.store.dto.OperatingHoursResponse;
import com.buyology.ecommerce.store.dto.UpdateOperatingHoursRequest;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreOperatingHoursRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StoreOperatingHoursService {

    private final StoreOperatingHoursRepository hoursRepository;
    private final StoreLocationRepository locationRepository;

    public StoreOperatingHoursService(StoreOperatingHoursRepository hoursRepository,
                                      StoreLocationRepository locationRepository) {
        this.hoursRepository = hoursRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public ResponseEntity<ApiResponse<OperatingHoursResponse>> createHours(
            UUID locationId, CreateOperatingHoursRequest request) {

        StoreLocation location = locationRepository.findById(locationId).orElse(null);
        if (location == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Location not found with id: " + locationId);
        }

        if (hoursRepository.existsByLocationIdAndDayOfWeek(locationId, request.getDayOfWeek())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Operating hours for " + request.getDayOfWeek() + " already exist for this location");
        }

        boolean isClosed = Boolean.TRUE.equals(request.getIsClosed());

        StoreOperatingHours hours = new StoreOperatingHours(
                location,
                request.getDayOfWeek(),
                isClosed ? null : request.getOpenTime(),
                isClosed ? null : request.getCloseTime(),
                isClosed);

        StoreOperatingHours saved = hoursRepository.save(hours);
        return ApiResponse.created(toResponse(saved), "Operating hours created successfully");
    }

    public ResponseEntity<ApiResponse<List<OperatingHoursResponse>>> getHoursByLocation(UUID locationId) {
        if (!locationRepository.findById(locationId).isPresent()) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Location not found with id: " + locationId);
        }
        List<OperatingHoursResponse> hours = hoursRepository.findAllByLocationId(locationId).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(hours, "Operating hours fetched successfully");
    }

    public ResponseEntity<ApiResponse<OperatingHoursResponse>> getHoursById(UUID hoursId) {
        StoreOperatingHours hours = hoursRepository.findById(hoursId).orElse(null);
        if (hours == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Operating hours not found with id: " + hoursId);
        }
        return ApiResponse.success(toResponse(hours), "Operating hours fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<OperatingHoursResponse>> updateHours(
            UUID hoursId, UpdateOperatingHoursRequest request) {

        StoreOperatingHours hours = hoursRepository.findById(hoursId).orElse(null);
        if (hours == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Operating hours not found with id: " + hoursId);
        }

        if (request.getIsClosed() != null) {
            hours.setIsClosed(request.getIsClosed());
            if (Boolean.TRUE.equals(request.getIsClosed())) {
                hours.setOpenTime(null);
                hours.setCloseTime(null);
            }
        }

        if (!Boolean.TRUE.equals(hours.getIsClosed())) {
            if (request.getOpenTime() != null) hours.setOpenTime(request.getOpenTime());
            if (request.getCloseTime() != null) hours.setCloseTime(request.getCloseTime());
        }

        StoreOperatingHours saved = hoursRepository.save(hours);
        return ApiResponse.success(toResponse(saved), "Operating hours updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteHours(UUID hoursId) {
        StoreOperatingHours hours = hoursRepository.findById(hoursId).orElse(null);
        if (hours == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Operating hours not found with id: " + hoursId);
        }
        hoursRepository.delete(hours);
        return ApiResponse.success(null, "Operating hours deleted successfully");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private OperatingHoursResponse toResponse(StoreOperatingHours h) {
        OperatingHoursResponse r = new OperatingHoursResponse();
        r.setId(h.getId());
        r.setLocationId(h.getLocation().getId());
        r.setDayOfWeek(h.getDayOfWeek());
        r.setOpenTime(h.getOpenTime());
        r.setCloseTime(h.getCloseTime());
        r.setIsClosed(h.getIsClosed());
        r.setCreatedAt(h.getCreatedAt());
        r.setUpdatedAt(h.getUpdatedAt());
        return r;
    }
}
