package com.buyology.ecommerce.store.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.dto.FlashSaleRemoveRequest;
import com.buyology.ecommerce.store.dto.FlashSaleRequest;
import com.buyology.ecommerce.store.dto.StoreProductResponse;
import com.buyology.ecommerce.store.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin flash-sale management.
 *
 * <p>Guarded by the existing {@code store:product:*} permissions rather than new ones of its own: a
 * flash sale IS a store-product price edit, and whoever may set a discount on one listing may set it
 * on forty. A new permission would also have to be seeded onto every existing admin role before the
 * screen worked for anybody.
 */
@RestController
@RequestMapping("/api/admin/flash-sale")
@Tag(name = "Flash Sale", description = "Discount a batch of store products until a date, and manage what is on sale")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @Operation(summary = "Put a batch of products on the flash sale",
            description = "Applies one discount and one end date to many store listings at once; any item may "
                    + "override either. Dates may be given as a calendar date in the shop's timezone (endsOn, "
                    + "Asia/Dubai) or as an exact instant (endsAt). All-or-nothing: the whole batch is validated "
                    + "before anything is written, so a bad row cannot leave half a campaign live. Refuses an end "
                    + "before its start, an end already past, a value of zero or less, a percentage over 100, "
                    + "and a fixed price at or above the store price. A product priced per variant is "
                    + "accepted: the discount applies to the listing price, which is the price every "
                    + "surface quotes and every cart line is charged.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:product:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping
    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> putOnFlashSale(
            @RequestBody @Valid FlashSaleRequest request) {
        return flashSaleService.putOnFlashSale(request);
    }

    @Operation(summary = "List what is on the flash sale",
            description = "Live and scheduled sales, soonest-ending first. Each row carries discountStatus "
                    + "(NONE | SCHEDULED | LIVE | ENDED), the window, and effectivePrice as of now. Ended sales "
                    + "are excluded — their prices have already reverted. Paged: the response is one page of "
                    + "rows (default 50, max 200) and the message carries the total.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:product:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> getFlashSale(
            @Parameter(description = "Limit to one store. Omit for every store.")
            @RequestParam(required = false) UUID storeId,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 200)")
            @RequestParam(defaultValue = "50") int size) {
        return flashSaleService.getFlashSale(storeId, page, size);
    }

    @Operation(summary = "Take a batch of products off the flash sale",
            description = "Clears the discount and its window, so the listing goes back to its store price "
                    + "immediately and nothing is left behind to expire later.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:product:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/remove")
    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> removeFromFlashSale(
            @RequestBody @Valid FlashSaleRemoveRequest request) {
        return flashSaleService.removeFromFlashSale(request);
    }

    @Operation(summary = "Take one product off the flash sale")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:product:update') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{storeProductId}")
    public ResponseEntity<ApiResponse<StoreProductResponse>> removeOneFromFlashSale(
            @PathVariable UUID storeProductId) {
        return flashSaleService.removeOneFromFlashSale(storeProductId);
    }
}
