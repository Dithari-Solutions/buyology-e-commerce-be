package com.buyology.ecommerce.store.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.dto.CountryResponse;
import com.buyology.ecommerce.store.dto.CreateCountryRequest;
import com.buyology.ecommerce.store.dto.UpdateCountryRequest;
import com.buyology.ecommerce.store.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/countries")
@Tag(name = "Country", description = "APIs for managing reference country data")
public class CountryController {

    private final CountryService countryService;

    public CountryController(CountryService countryService) {
        this.countryService = countryService;
    }

    @Operation(summary = "Create a country")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CountryResponse>> createCountry(
            @RequestBody @Valid CreateCountryRequest request) {
        return countryService.createCountry(request);
    }

    @Operation(summary = "Get all countries")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CountryResponse>>> getAllCountries() {
        return countryService.getAllCountries();
    }

    @Operation(summary = "Get all active countries")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<CountryResponse>>> getActiveCountries() {
        return countryService.getActiveCountries();
    }

    @Operation(summary = "Get a country by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CountryResponse>> getCountryById(@PathVariable UUID id) {
        return countryService.getCountryById(id);
    }

    @Operation(summary = "Update a country")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CountryResponse>> updateCountry(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateCountryRequest request) {
        return countryService.updateCountry(id, request);
    }

    @Operation(summary = "Deactivate a country")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCountry(@PathVariable UUID id) {
        return countryService.deleteCountry(id);
    }
}
