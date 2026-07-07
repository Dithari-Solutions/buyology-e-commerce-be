package com.buyology.ecommerce.b2b.productrequest.controller;

import com.buyology.ecommerce.b2b.productrequest.dto.B2bProductRequestResponse;
import com.buyology.ecommerce.b2b.productrequest.service.B2bProductRequestService;
import com.buyology.ecommerce.common.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Member-facing B2B product-sourcing request endpoints. All require authentication; the service
 * enforces that the caller holds an ACTIVE B2bMembership and keys ownership on their credential
 * (sub). The JWT filter sets the principal to users.id (uid); the service resolves the credential.
 */
@RestController
@RequestMapping("/api/b2b/product-requests")
@PreAuthorize("isAuthenticated()")
public class B2bProductRequestController {

    private final B2bProductRequestService requestService;

    public B2bProductRequestController(B2bProductRequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * Submit a product request. Multipart so an optional image can be attached; scalar fields are
     * plain {@code @RequestParam} (NOT a JSON @RequestPart) to avoid browser FormData
     * content-type / 415 issues.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<B2bProductRequestResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("productName") String productName,
            @RequestParam("quantity") Integer quantity,
            @RequestParam(value = "message", required = false) String message,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ApiResponse.created(
                requestService.create(userId, productName, quantity, message, image),
                "Product request submitted");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<B2bProductRequestResponse>>> listMine(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(requestService.listOwn(userId), "Product requests fetched");
    }
}
