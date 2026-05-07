package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.membership.domain.B2bCountry;
import com.buyology.ecommerce.membership.repository.B2bCountryRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/b2b/countries")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public class AdminB2bCountryController {

    private final B2bCountryRepository repo;

    public AdminB2bCountryController(B2bCountryRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<B2bCountry>>> list() {
        return ApiResponse.success(repo.findAll(), "B2B countries");
    }

    public record CountryRequest(
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @NotBlank String countryName,
            @NotBlank String currencyCode,
            Boolean enabled,
            java.math.BigDecimal minOrderAmount) {}

    @PostMapping
    public ResponseEntity<ApiResponse<B2bCountry>> upsert(@org.springframework.web.bind.annotation.RequestBody CountryRequest req) {
        String code = req.countryCode().toUpperCase();
        B2bCountry country = repo.findByCountryCode(code).orElseGet(B2bCountry::new);
        country.setCountryCode(code);
        country.setCountryName(req.countryName());
        country.setCurrencyCode(req.currencyCode().toUpperCase());
        country.setEnabled(req.enabled() == null || req.enabled());
        country.setMinOrderAmount(req.minOrderAmount());
        return ApiResponse.success(repo.save(country), "Saved");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID id) {
        if (!repo.existsById(id)) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Not found");
        repo.deleteById(id);
        return ApiResponse.success("deleted", "Country removed");
    }

    @GetMapping("/public")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<B2bCountry>>> listEnabled() {
        return ApiResponse.success(repo.findByEnabledTrueOrderByCountryNameAsc(), "Enabled B2B countries");
    }
}
