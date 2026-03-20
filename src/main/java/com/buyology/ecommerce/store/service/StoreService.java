package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreTranslation;
import com.buyology.ecommerce.store.dto.CreateStoreRequest;
import com.buyology.ecommerce.store.dto.StoreResponse;
import com.buyology.ecommerce.store.dto.StoreTranslationRequest;
import com.buyology.ecommerce.store.dto.StoreTranslationResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreRequest;
import com.buyology.ecommerce.store.repository.CountryRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.buyology.ecommerce.store.repository.StoreTranslationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final CountryRepository countryRepository;
    private final StoreTranslationRepository translationRepository;

    public StoreService(StoreRepository storeRepository,
                        CountryRepository countryRepository,
                        StoreTranslationRepository translationRepository) {
        this.storeRepository = storeRepository;
        this.countryRepository = countryRepository;
        this.translationRepository = translationRepository;
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(CreateStoreRequest request) {
        Country country = countryRepository.findById(request.getCountryId()).orElse(null);
        if (country == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND,
                    "Country not found with id: " + request.getCountryId());
        }

        if (storeRepository.existsBySlug(request.getSlug())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Slug '" + request.getSlug() + "' is already taken");
        }

        Store store = new Store(country, request.getName(), request.getSlug());
        if (request.getStatus() != null) store.setStatus(request.getStatus());
        store.setBannerUrl(request.getBannerUrl());
        store.setContactEmail(request.getContactEmail());
        store.setContactPhone(request.getContactPhone());

        Store saved = storeRepository.save(store);

        List<StoreTranslation> translations = List.of();
        if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
            translations = saveTranslations(saved, request.getTranslations());
        }

        return ApiResponse.created(buildResponse(saved, translations), "Store created successfully");
    }

    public ResponseEntity<ApiResponse<List<StoreResponse>>> getAllStores() {
        List<StoreResponse> stores = storeRepository.findAllByDeletedAtIsNull().stream()
                .map(s -> buildResponse(s, translationRepository.findAllByStoreId(s.getId())))
                .toList();
        return ApiResponse.success(stores, "Stores fetched successfully");
    }

    public ResponseEntity<ApiResponse<StoreResponse>> getStoreById(UUID id) {
        Store store = storeRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + id);
        }
        List<StoreTranslation> translations = translationRepository.findAllByStoreId(id);
        return ApiResponse.success(buildResponse(store, translations), "Store fetched successfully");
    }

    public ResponseEntity<ApiResponse<StoreResponse>> getStoreBySlug(String slug) {
        Store store = storeRepository.findBySlugAndDeletedAtIsNull(slug).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with slug: " + slug);
        }
        List<StoreTranslation> translations = translationRepository.findAllByStoreId(store.getId());
        return ApiResponse.success(buildResponse(store, translations), "Store fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(UUID id, UpdateStoreRequest request) {
        Store store = storeRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + id);
        }

        if (request.getCountryId() != null) {
            Country country = countryRepository.findById(request.getCountryId()).orElse(null);
            if (country == null) {
                return ApiResponse.failure(HttpStatus.NOT_FOUND,
                        "Country not found with id: " + request.getCountryId());
            }
            store.setCountry(country);
        }

        if (request.getSlug() != null) {
            if (storeRepository.existsBySlugAndIdNot(request.getSlug(), id)) {
                return ApiResponse.failure(HttpStatus.CONFLICT,
                        "Slug '" + request.getSlug() + "' is already taken");
            }
            store.setSlug(request.getSlug());
        }

        if (request.getName() != null) store.setName(request.getName());
        if (request.getStatus() != null) store.setStatus(request.getStatus());
        if (request.getBannerUrl() != null) store.setBannerUrl(request.getBannerUrl());
        if (request.getContactEmail() != null) store.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) store.setContactPhone(request.getContactPhone());

        Store saved = storeRepository.save(store);
        List<StoreTranslation> translations = translationRepository.findAllByStoreId(id);
        return ApiResponse.success(buildResponse(saved, translations), "Store updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteStore(UUID id) {
        Store store = storeRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + id);
        }
        store.setDeletedAt(Instant.now());
        storeRepository.save(store);
        return ApiResponse.success(null, "Store deleted successfully");
    }

    // ── Translation endpoints ────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<StoreTranslationResponse>> addTranslation(
            UUID storeId, StoreTranslationRequest request) {

        Store store = storeRepository.findByIdAndDeletedAtIsNull(storeId).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + storeId);
        }

        String lang = request.getLanguage().toLowerCase();
        if (translationRepository.existsByStoreIdAndLanguage(storeId, lang)) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Translation for language '" + lang + "' already exists on this store");
        }

        StoreTranslation translation = new StoreTranslation(store, lang, request.getName(), request.getDescription());
        StoreTranslation saved = translationRepository.save(translation);
        return ApiResponse.created(toTranslationResponse(saved), "Translation added successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreTranslationResponse>> updateTranslation(
            UUID storeId, String language, StoreTranslationRequest request) {

        StoreTranslation translation = translationRepository
                .findByStoreIdAndLanguage(storeId, language.toLowerCase()).orElse(null);
        if (translation == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND,
                    "Translation for language '" + language + "' not found on store " + storeId);
        }

        if (request.getName() != null) translation.setName(request.getName());
        if (request.getDescription() != null) translation.setDescription(request.getDescription());

        StoreTranslation saved = translationRepository.save(translation);
        return ApiResponse.success(toTranslationResponse(saved), "Translation updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteTranslation(UUID storeId, String language) {
        StoreTranslation translation = translationRepository
                .findByStoreIdAndLanguage(storeId, language.toLowerCase()).orElse(null);
        if (translation == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND,
                    "Translation for language '" + language + "' not found on store " + storeId);
        }
        translationRepository.delete(translation);
        return ApiResponse.success(null, "Translation deleted successfully");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<StoreTranslation> saveTranslations(Store store, List<StoreTranslationRequest> requests) {
        List<StoreTranslation> entities = requests.stream()
                .map(r -> new StoreTranslation(store, r.getLanguage().toLowerCase(),
                        r.getName(), r.getDescription()))
                .toList();
        return translationRepository.saveAll(entities);
    }

    private StoreResponse buildResponse(Store store, List<StoreTranslation> translations) {
        StoreResponse response = new StoreResponse();
        response.setId(store.getId());
        response.setCountryId(store.getCountry().getId());
        response.setCountryName(store.getCountry().getName());
        response.setName(store.getName());
        response.setSlug(store.getSlug());
        response.setStatus(store.getStatus());
        response.setBannerUrl(store.getBannerUrl());
        response.setContactEmail(store.getContactEmail());
        response.setContactPhone(store.getContactPhone());
        response.setCreatedAt(store.getCreatedAt());
        response.setUpdatedAt(store.getUpdatedAt());
        response.setTranslations(translations.stream().map(this::toTranslationResponse).toList());
        return response;
    }

    private StoreTranslationResponse toTranslationResponse(StoreTranslation t) {
        return new StoreTranslationResponse(
                t.getId(), t.getLanguage(), t.getName(),
                t.getDescription(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
