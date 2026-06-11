package com.buyology.ecommerce.user.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.user.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/account")
public class UserAccountController {

    private final UserAccountService service;

    public UserAccountController(UserAccountService service) {
        this.service = service;
    }

    @Operation(summary = "Request account deletion (soft-delete; permanent after a 30-day grace period)")
    @PostMapping("/delete-request")
    public ResponseEntity<ApiResponse<Void>> requestDeletion(@PathVariable UUID userId) {
        service.requestDeletion(userId);
        return ApiResponse.success(null, "Account scheduled for deletion in " + UserAccountService.GRACE_DAYS + " days");
    }

    @Operation(summary = "Recover an account that was scheduled for deletion (within the grace period)")
    @PostMapping("/recover")
    public ResponseEntity<ApiResponse<Void>> recover(@PathVariable UUID userId) {
        service.recover(userId);
        return ApiResponse.success(null, "Account recovered");
    }
}
