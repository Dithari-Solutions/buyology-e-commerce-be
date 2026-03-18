package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.dto.CountryResponse;
import com.buyology.ecommerce.store.dto.CreateCountryRequest;
import com.buyology.ecommerce.store.dto.UpdateCountryRequest;
import com.buyology.ecommerce.store.repository.CountryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CountryService {

    private final CountryRepository countryRepository;

    public CountryService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @Transactional
    public ResponseEntity<ApiResponse<CountryResponse>> createCountry(CreateCountryRequest request) {
        if (countryRepository.existsByCode(request.getCode().toUpperCase())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Country with code '" + request.getCode() + "' already exists");
        }

        Country country = new Country(
                request.getCode().toUpperCase(),
                request.getName(),
                request.getCurrency().toUpperCase());

        if (request.getIsActive() != null) {
            country.setIsActive(request.getIsActive());
        }

        Country saved = countryRepository.save(country);
        return ApiResponse.created(toResponse(saved), "Country created successfully");
    }

    public ResponseEntity<ApiResponse<List<CountryResponse>>> getAllCountries() {
        List<CountryResponse> countries = countryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(countries, "Countries fetched successfully");
    }

    public ResponseEntity<ApiResponse<List<CountryResponse>>> getActiveCountries() {
        List<CountryResponse> countries = countryRepository.findAllByIsActive(true).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(countries, "Active countries fetched successfully");
    }

    public ResponseEntity<ApiResponse<CountryResponse>> getCountryById(UUID id) {
        Country country = countryRepository.findById(id).orElse(null);
        if (country == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Country not found with id: " + id);
        }
        return ApiResponse.success(toResponse(country), "Country fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<CountryResponse>> updateCountry(UUID id, UpdateCountryRequest request) {
        Country country = countryRepository.findById(id).orElse(null);
        if (country == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Country not found with id: " + id);
        }

        if (request.getCode() != null) {
            String newCode = request.getCode().toUpperCase();
            if (countryRepository.existsByCodeAndIdNot(newCode, id)) {
                return ApiResponse.failure(HttpStatus.CONFLICT,
                        "Country code '" + newCode + "' is already taken");
            }
            country.setCode(newCode);
        }
        if (request.getName() != null) country.setName(request.getName());
        if (request.getCurrency() != null) country.setCurrency(request.getCurrency().toUpperCase());
        if (request.getIsActive() != null) country.setIsActive(request.getIsActive());

        Country saved = countryRepository.save(country);
        return ApiResponse.success(toResponse(saved), "Country updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteCountry(UUID id) {
        Country country = countryRepository.findById(id).orElse(null);
        if (country == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Country not found with id: " + id);
        }
        country.setIsActive(false);
        countryRepository.save(country);
        return ApiResponse.success(null, "Country deactivated successfully");
    }

    private CountryResponse toResponse(Country c) {
        return new CountryResponse(
                c.getId(), c.getCode(), c.getName(), c.getCurrency(),
                c.getIsActive(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
