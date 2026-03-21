package com.buyology.ecommerce.user.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.user.dto.AddressResponse;
import com.buyology.ecommerce.user.dto.SaveAddressRequest;
import com.buyology.ecommerce.user.service.UserAddressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/addresses")
public class UserAddressController {

    private final UserAddressService addressService;

    public UserAddressController(UserAddressService addressService) {
        this.addressService = addressService;
    }

    /**
     * Save a new delivery address.
     * Phone number is stored but not yet verified via SMS OTP (re-enable when Twilio is configured).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> saveAddress(
            @PathVariable UUID userId,
            @Valid @RequestBody SaveAddressRequest request) {
        return ApiResponse.created(addressService.saveAddress(userId, request), "Address saved successfully");
    }

    /**
     * Get all saved addresses for a user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
            @PathVariable UUID userId) {
        return ApiResponse.success(addressService.getAddresses(userId), "Addresses fetched successfully");
    }

    /**
     * Get a single address by ID.
     */
    @GetMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @PathVariable UUID userId,
            @PathVariable UUID addressId) {
        return ApiResponse.success(addressService.getAddress(userId, addressId), "Address fetched successfully");
    }

    /**
     * Mark an address as the default delivery address.
     * Clears the default flag from all other addresses for this user.
     */
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefault(
            @PathVariable UUID userId,
            @PathVariable UUID addressId) {
        return ApiResponse.success(addressService.setDefault(userId, addressId), "Default address updated");
    }

    /**
     * Delete an address. Hard delete — payment history is unaffected
     * since transactions snapshot the address at time of payment.
     */
    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable UUID userId,
            @PathVariable UUID addressId) {
        addressService.deleteAddress(userId, addressId);
        return ApiResponse.success(null, "Address deleted successfully");
    }
}
