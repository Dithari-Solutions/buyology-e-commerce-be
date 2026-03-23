package com.buyology.ecommerce.admin.controller;

import com.buyology.ecommerce.admin.dto.AdminUserDetailResponse;
import com.buyology.ecommerce.admin.dto.AdminUserListResponse;
import com.buyology.ecommerce.admin.service.AdminUserService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin – Users", description = "Admin access to user management")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "List all users (paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserListResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.listUsers(page, size);
    }

    @Operation(summary = "Get full user detail by authCredentialId — includes profile, favorites, and active cart")
    @GetMapping("/{authCredentialId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable UUID authCredentialId) {
        return adminUserService.getUserDetail(authCredentialId);
    }
}
