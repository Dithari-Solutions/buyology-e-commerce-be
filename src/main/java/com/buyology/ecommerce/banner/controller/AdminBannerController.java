package com.buyology.ecommerce.banner.controller;

import com.buyology.ecommerce.banner.domain.Banner;
import com.buyology.ecommerce.banner.domain.BannerPlatform;
import com.buyology.ecommerce.banner.domain.BannerStatus;
import com.buyology.ecommerce.banner.dto.BannerAdminResponse;
import com.buyology.ecommerce.banner.dto.CreateBannerFormData;
import com.buyology.ecommerce.banner.dto.CreateBannerRequest;
import com.buyology.ecommerce.banner.dto.UpdateBannerRequest;
import com.buyology.ecommerce.banner.service.BannerService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/banner")
@Tag(name = "Admin Banner", description = "Admin APIs for promo banner management")
public class AdminBannerController {

    private final BannerService bannerService;
    private final ObjectMapper objectMapper;

    public AdminBannerController(BannerService bannerService, ObjectMapper objectMapper) {
        this.bannerService = bannerService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "List all banners (admin), optionally filtered by platform")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('banner:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerAdminResponse>>> list(
            @RequestParam(required = false) BannerPlatform platform) {
        return ApiResponse.success(bannerService.listForAdmin(platform), "Banners fetched successfully");
    }

    @Operation(summary = "Get one banner by id")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('banner:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerAdminResponse>> getOne(@PathVariable UUID id) {
        return ApiResponse.success(bannerService.getAdminById(id), "Banner fetched successfully");
    }

    @Operation(summary = "Create a banner")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = CreateBannerFormData.class),
                    encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)
            )
    )
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('banner:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BannerAdminResponse>> create(
            @RequestPart("request") String requestJson,
            @RequestPart("background") MultipartFile background) throws Exception {
        CreateBannerRequest request = objectMapper.readValue(requestJson, CreateBannerRequest.class);
        UUID createdBy = SecurityUtils.currentUserIdOrNull();
        Banner created = bannerService.createBanner(request, background, createdBy);
        return ApiResponse.created(bannerService.getAdminById(created.getId()), "Banner created successfully");
    }

    @Operation(summary = "Update a banner")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('banner:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BannerAdminResponse>> update(
            @PathVariable UUID id,
            @RequestPart("request") String requestJson,
            @RequestPart(value = "background", required = false) MultipartFile background) throws Exception {
        UpdateBannerRequest request = objectMapper.readValue(requestJson, UpdateBannerRequest.class);
        Banner updated = bannerService.updateBanner(id, request, background);
        return ApiResponse.success(bannerService.getAdminById(updated.getId()), "Banner updated successfully");
    }

    @Operation(summary = "Set banner status")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('banner:moderate') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable UUID id,
            @RequestParam BannerStatus status) {
        bannerService.setStatus(id, status);
        return ApiResponse.success(null, "Banner status updated");
    }

    @Operation(summary = "Set banner sort order")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('banner:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{id}/sort-order")
    public ResponseEntity<ApiResponse<Void>> setSortOrder(
            @PathVariable UUID id,
            @RequestParam int sortOrder) {
        bannerService.setSortOrder(id, sortOrder);
        return ApiResponse.success(null, "Banner sort order updated");
    }

    @Operation(summary = "Delete a banner")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('banner:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        bannerService.deleteBanner(id);
        return ApiResponse.success(null, "Banner deleted successfully");
    }
}
