package com.buyology.ecommerce.cart.admin;

import com.buyology.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/admin/cart-activity")
public class AdminCartActivityController {
    private final AdminCartActivityService service;
    public AdminCartActivityController(AdminCartActivityService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('user:read') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(60) int size,
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(defaultValue = "false") boolean withItems) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .body(new ApiResponse<>(200, "Customer cart activity", service.list(page, size, search, withItems)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('user:read') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable UUID userId) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .body(new ApiResponse<>(200, "Customer cart", service.detail(userId)));
    }

    public record MessageRequest(@NotNull UUID requestId, @NotBlank @Size(max = 160) @Pattern(regexp = "[^\\r\\n]+") String subject,
                                 @NotBlank @Size(max = 5000) String body) {}
    @PostMapping("/{userId}/messages")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> send(@PathVariable UUID userId,
            @AuthenticationPrincipal UUID adminId, @Valid @RequestBody MessageRequest request) {
        return ApiResponse.success(service.send(userId, adminId, request.requestId(), request.subject().trim(), request.body().trim()), "Message processed");
    }
}
