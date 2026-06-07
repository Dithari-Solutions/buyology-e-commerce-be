package com.buyology.ecommerce.store.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.dto.CreateStoreRequest;
import com.buyology.ecommerce.store.dto.StoreResponse;
import com.buyology.ecommerce.store.dto.StoreTranslationRequest;
import com.buyology.ecommerce.store.dto.StoreTranslationResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreRequest;
import com.buyology.ecommerce.store.service.StoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stores")
@Tag(name = "Store", description = "APIs for managing stores and their translations")
public class StoreController {

    private final StoreService storeService;
    private final ObjectMapper objectMapper;

    public StoreController(StoreService storeService, ObjectMapper objectMapper) {
        this.storeService = storeService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create a store",
            description = "Multipart form: 'request' part is JSON, 'banner' part is an optional image file.")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'STORE_ADMIN')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(
            @RequestPart("request") String requestJson,
            @RequestPart(value = "banner", required = false) MultipartFile banner) throws Exception {
        CreateStoreRequest request = objectMapper.readValue(requestJson, CreateStoreRequest.class);
        return storeService.createStore(request, banner);
    }

    @Operation(summary = "Get all stores")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreResponse>>> getAllStores() {
        return storeService.getAllStores();
    }

    @Operation(summary = "Get a store by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStoreById(@PathVariable UUID id) {
        return storeService.getStoreById(id);
    }

    @Operation(summary = "Get a store by slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStoreBySlug(@PathVariable String slug) {
        return storeService.getStoreBySlug(slug);
    }

    @Operation(summary = "Update a store",
            description = "Multipart form: 'request' part is JSON (all fields optional), 'banner' part is an optional new image file.")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'STORE_ADMIN')")
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @PathVariable UUID id,
            @RequestPart("request") String requestJson,
            @RequestPart(value = "banner", required = false) MultipartFile banner) throws Exception {
        UpdateStoreRequest request = objectMapper.readValue(requestJson, UpdateStoreRequest.class);
        return storeService.updateStore(id, request, banner);
    }

    @Operation(summary = "Soft-delete a store")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'STORE_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStore(@PathVariable UUID id) {
        return storeService.deleteStore(id);
    }

    // ── Translation sub-resource ─────────────────────────────────────────────

    @Operation(summary = "Add a translation to a store")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'STORE_ADMIN')")
    @PostMapping("/{storeId}/translations")
    public ResponseEntity<ApiResponse<StoreTranslationResponse>> addTranslation(
            @PathVariable UUID storeId,
            @RequestBody @Valid StoreTranslationRequest request) {
        return storeService.addTranslation(storeId, request);
    }

    @Operation(summary = "Update a store translation by language code")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'STORE_ADMIN')")
    @PatchMapping("/{storeId}/translations/{language}")
    public ResponseEntity<ApiResponse<StoreTranslationResponse>> updateTranslation(
            @PathVariable UUID storeId,
            @PathVariable String language,
            @RequestBody @Valid StoreTranslationRequest request) {
        return storeService.updateTranslation(storeId, language, request);
    }

    @Operation(summary = "Delete a store translation by language code")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'STORE_ADMIN')")
    @DeleteMapping("/{storeId}/translations/{language}")
    public ResponseEntity<ApiResponse<Void>> deleteTranslation(
            @PathVariable UUID storeId,
            @PathVariable String language) {
        return storeService.deleteTranslation(storeId, language);
    }
}
