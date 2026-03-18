package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Brand;
import com.buyology.ecommerce.product.domain.BrandTranslation;
import com.buyology.ecommerce.product.dto.BrandResponse;
import com.buyology.ecommerce.product.dto.CreateBrandRequest;
import com.buyology.ecommerce.product.repository.BrandRepository;
import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandTranslationRepository translationRepository;

    public BrandService(BrandRepository brandRepository,
            BrandTranslationRepository translationRepository) {
        this.brandRepository = brandRepository;
        this.translationRepository = translationRepository;
    }

    @Transactional
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(CreateBrandRequest request) {
        Brand brand = new Brand();
        Brand saved = brandRepository.save(brand);

        List<BrandTranslation> translations = translationRepository.saveAll(List.of(
                new BrandTranslation(saved, "AZ", request.getNameAz()),
                new BrandTranslation(saved, "EN", request.getNameEn()),
                new BrandTranslation(saved, "AR", request.getNameAr())));

        return ApiResponse.created(toResponse(saved, translations), "Brand created successfully");
    }

    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {
        List<BrandResponse> brands = brandRepository.findByStatus("ACTIVE").stream()
                .map(b -> toResponse(b, translationRepository.findAllByBrand_Id(b.getId())))
                .toList();
        return ApiResponse.success(brands, "Brands fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteBrand(UUID id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Brand not found with id: " + id));
        brand.setStatus("INACTIVE");
        brandRepository.save(brand);
        return ApiResponse.success(null, "Brand deleted successfully");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private BrandResponse toResponse(Brand brand, List<BrandTranslation> translations) {
        List<BrandResponse.TranslationDto> dtos = translations.stream()
                .map(t -> new BrandResponse.TranslationDto(t.getLanguage(), t.getName()))
                .toList();
        return new BrandResponse(brand.getId(), brand.getStatus(), dtos);
    }
}
