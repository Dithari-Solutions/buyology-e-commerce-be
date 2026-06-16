package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.domain.StoreOperatingHours;
import com.buyology.ecommerce.store.domain.StoreTranslation;
import com.buyology.ecommerce.store.dto.CreateOperatingHoursRequest;
import com.buyology.ecommerce.store.dto.CreateStoreLocationRequest;
import com.buyology.ecommerce.store.dto.CreateStoreRequest;
import com.buyology.ecommerce.store.dto.StoreLocationResponse;
import com.buyology.ecommerce.store.dto.StoreResponse;
import com.buyology.ecommerce.store.dto.StoreTranslationRequest;
import com.buyology.ecommerce.store.dto.StoreTranslationResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreRequest;
import com.buyology.ecommerce.store.repository.CountryRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreOperatingHoursRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.buyology.ecommerce.store.repository.StoreTranslationRepository;
import com.buyology.ecommerce.common.utils.FileValidationUtils;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final CountryRepository countryRepository;
    private final StoreTranslationRepository translationRepository;
    private final StoreLocationRepository locationRepository;
    private final StoreOperatingHoursRepository hoursRepository;
    private final ContaboObjectService contaboObjectService;

    public StoreService(StoreRepository storeRepository,
                        CountryRepository countryRepository,
                        StoreTranslationRepository translationRepository,
                        StoreLocationRepository locationRepository,
                        StoreOperatingHoursRepository hoursRepository,
                        ContaboObjectService contaboObjectService) {
        this.storeRepository = storeRepository;
        this.countryRepository = countryRepository;
        this.translationRepository = translationRepository;
        this.locationRepository = locationRepository;
        this.hoursRepository = hoursRepository;
        this.contaboObjectService = contaboObjectService;
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(CreateStoreRequest request, MultipartFile banner) {
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
        store.setContactEmail(request.getContactEmail());
        store.setContactPhone(request.getContactPhone());

        Store saved = storeRepository.save(store);

        if (banner != null && !banner.isEmpty()) {
            saved.setBannerUrl(saveBannerFile(saved.getId(), banner));
            saved = storeRepository.save(saved);
        }

        List<StoreTranslation> translations = List.of();
        if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
            translations = saveTranslations(saved, request.getTranslations());
        }

        StoreLocation location = createLocationWithHours(saved, request.getLocation());

        return ApiResponse.created(buildResponse(saved, translations, List.of(location)), "Store created successfully");
    }

    public ResponseEntity<ApiResponse<List<StoreResponse>>> getAllStores() {
        List<StoreResponse> stores = storeRepository.findAllByDeletedAtIsNull().stream()
                .map(s -> buildResponse(s,
                        translationRepository.findAllByStoreId(s.getId()),
                        locationRepository.findAllByStoreId(s.getId())))
                .toList();
        return ApiResponse.success(stores, "Stores fetched successfully");
    }

    /**
     * Public storefront view: ACTIVE stores with their locations AND each location's
     * weekly operating hours nested — for the contact page (no auth required).
     */
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<StoreResponse>>> getPublicStores() {
        List<StoreResponse> stores = storeRepository
                .findAllByStatusAndDeletedAtIsNull(com.buyology.ecommerce.store.enums.StoreStatus.ACTIVE)
                .stream()
                .map(s -> {
                    // Lightweight public payload: only contact-relevant fields (no banner
                    // presign, no status/timestamps/translations) so this unauthenticated
                    // endpoint stays cheap and doesn't leak admin/internal fields.
                    StoreResponse resp = new StoreResponse();
                    resp.setId(s.getId());
                    resp.setName(s.getName());
                    resp.setSlug(s.getSlug());
                    resp.setCountryName(s.getCountry().getName());
                    resp.setContactEmail(s.getContactEmail());
                    resp.setContactPhone(s.getContactPhone());
                    resp.setLocations(locationRepository.findAllByStoreIdAndIsActive(s.getId(), true).stream()
                            .map(l -> {
                                StoreLocationResponse lr = toLocationResponse(l);
                                lr.setOperatingHours(hoursRepository.findAllByLocationId(l.getId()).stream()
                                        .map(this::toHoursResponse).toList());
                                return lr;
                            }).toList());
                    return resp;
                })
                .toList();
        return ApiResponse.success(stores, "Stores fetched successfully");
    }

    private com.buyology.ecommerce.store.dto.OperatingHoursResponse toHoursResponse(StoreOperatingHours h) {
        com.buyology.ecommerce.store.dto.OperatingHoursResponse r =
                new com.buyology.ecommerce.store.dto.OperatingHoursResponse();
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

    public ResponseEntity<ApiResponse<StoreResponse>> getStoreById(UUID id) {
        Store store = storeRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with id: " + id);
        }
        return ApiResponse.success(buildResponse(store,
                translationRepository.findAllByStoreId(id),
                locationRepository.findAllByStoreId(id)), "Store fetched successfully");
    }

    public ResponseEntity<ApiResponse<StoreResponse>> getStoreBySlug(String slug) {
        Store store = storeRepository.findBySlugAndDeletedAtIsNull(slug).orElse(null);
        if (store == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Store not found with slug: " + slug);
        }
        return ApiResponse.success(buildResponse(store,
                translationRepository.findAllByStoreId(store.getId()),
                locationRepository.findAllByStoreId(store.getId())), "Store fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(UUID id, UpdateStoreRequest request, MultipartFile banner) {
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
        if (banner != null && !banner.isEmpty()) store.setBannerUrl(saveBannerFile(id, banner));
        if (request.getContactEmail() != null) store.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) store.setContactPhone(request.getContactPhone());

        Store saved = storeRepository.save(store);
        return ApiResponse.success(buildResponse(saved,
                translationRepository.findAllByStoreId(id),
                locationRepository.findAllByStoreId(id)), "Store updated successfully");
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

    private String saveBannerFile(UUID storeId, MultipartFile file) {
        FileValidationUtils.validateImage(file);
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf("."))
                : "";
        // Store under the watermarked "banners/" prefix and persist the returned S3
        // key (which may have an updated extension if the image was re-encoded).
        String key = "banners/" + storeId + "/banner" + ext;
        return contaboObjectService.uploadFile(key, file);
    }

    private StoreLocation createLocationWithHours(Store store, CreateStoreLocationRequest req) {
        StoreLocation location = new StoreLocation(
                store,
                req.getBranchName(),
                req.getAddress(),
                req.getCity(),
                req.getCountry(),
                req.getLatitude(),
                req.getLongitude());
        location.setState(req.getState());
        location.setPostalCode(req.getPostalCode());
        location.setIsPrimary(true);
        StoreLocation saved = locationRepository.save(location);

        for (CreateOperatingHoursRequest h : req.getOperatingHours()) {
            boolean isClosed = Boolean.TRUE.equals(h.getIsClosed());
            hoursRepository.save(new StoreOperatingHours(
                    saved,
                    h.getDayOfWeek(),
                    isClosed ? null : h.getOpenTime(),
                    isClosed ? null : h.getCloseTime(),
                    isClosed));
        }
        return saved;
    }

    private List<StoreTranslation> saveTranslations(Store store, List<StoreTranslationRequest> requests) {
        List<StoreTranslation> entities = requests.stream()
                .map(r -> new StoreTranslation(store, r.getLanguage().toLowerCase(),
                        r.getName(), r.getDescription()))
                .toList();
        return translationRepository.saveAll(entities);
    }

    private StoreResponse buildResponse(Store store, List<StoreTranslation> translations, List<StoreLocation> locations) {
        StoreResponse response = new StoreResponse();
        response.setId(store.getId());
        response.setCountryId(store.getCountry().getId());
        response.setCountryName(store.getCountry().getName());
        response.setName(store.getName());
        response.setSlug(store.getSlug());
        response.setStatus(store.getStatus());
        response.setBannerUrl(contaboObjectService.getPresignedUrl(store.getBannerUrl()));
        response.setContactEmail(store.getContactEmail());
        response.setContactPhone(store.getContactPhone());
        response.setCreatedAt(store.getCreatedAt());
        response.setUpdatedAt(store.getUpdatedAt());
        response.setTranslations(translations.stream().map(this::toTranslationResponse).toList());
        response.setLocations(locations.stream().map(this::toLocationResponse).toList());
        return response;
    }

    private StoreTranslationResponse toTranslationResponse(StoreTranslation t) {
        return new StoreTranslationResponse(
                t.getId(), t.getLanguage(), t.getName(),
                t.getDescription(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private StoreLocationResponse toLocationResponse(StoreLocation l) {
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
