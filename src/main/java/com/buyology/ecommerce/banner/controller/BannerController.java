package com.buyology.ecommerce.banner.controller;

import com.buyology.ecommerce.banner.dto.BannerResponse;
import com.buyology.ecommerce.banner.service.BannerService;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/banner")
@Tag(name = "Banner", description = "Public APIs for promo banners")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @Operation(summary = "Get active banners ordered by sortOrder")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getBanners(@RequestParam Language language) {
        List<BannerResponse> data = bannerService.listActiveForPublic(language);
        return ApiResponse.success(data, data.isEmpty() ? "No banners found." : "Banners fetched successfully");
    }
}
