package com.buyology.ecommerce.cart.controller;

import com.buyology.ecommerce.cart.dto.AddToCartRequest;
import com.buyology.ecommerce.cart.dto.CartResponse;
import com.buyology.ecommerce.cart.dto.UpdateCartItemRequest;
import com.buyology.ecommerce.cart.service.CartService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "Shopping cart management")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Get the active cart for a user. Pass lat/lng to enable the under-30-min delivery badge.")
    @GetMapping("/{authCredentialId}")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @PathVariable UUID authCredentialId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return cartService.getCart(authCredentialId, lat, lng);
    }

    @Operation(summary = "Add a product (with optional variant and spec selections) to the cart")
    @PostMapping("/{authCredentialId}/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @PathVariable UUID authCredentialId,
            @Valid @RequestBody AddToCartRequest request) {
        return cartService.addItem(authCredentialId, request);
    }

    @Operation(summary = "Update the quantity of a cart item")
    @PatchMapping("/{authCredentialId}/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @PathVariable UUID authCredentialId,
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItemQuantity(authCredentialId, cartItemId, request);
    }

    @Operation(summary = "Remove a specific item from the cart")
    @DeleteMapping("/{authCredentialId}/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable UUID authCredentialId,
            @PathVariable UUID cartItemId) {
        return cartService.removeItem(authCredentialId, cartItemId);
    }

    @Operation(summary = "Clear all items from the active cart")
    @DeleteMapping("/{authCredentialId}")
    public ResponseEntity<ApiResponse<Void>> clearCart(@PathVariable UUID authCredentialId) {
        return cartService.clearCart(authCredentialId);
    }

    @Operation(summary = "Tick or untick one cart line. Unticked lines stay in the cart but are not ordered.")
    @PatchMapping("/{authCredentialId}/items/{cartItemId}/selection")
    public ResponseEntity<ApiResponse<CartResponse>> setItemSelection(
            @PathVariable UUID authCredentialId,
            @PathVariable UUID cartItemId,
            @RequestParam boolean selected) {
        return cartService.setItemSelection(authCredentialId, cartItemId, selected);
    }

    @Operation(summary = "Tick or untick every cart line at once.")
    @PatchMapping("/{authCredentialId}/selection")
    public ResponseEntity<ApiResponse<CartResponse>> setAllSelection(
            @PathVariable UUID authCredentialId,
            @RequestParam boolean selected) {
        return cartService.setAllSelection(authCredentialId, selected);
    }

    @Operation(summary = "Checkout the active cart")
    @PostMapping("/{authCredentialId}/checkout")
    public ResponseEntity<ApiResponse<CartResponse>> checkout(@PathVariable UUID authCredentialId) {
        return cartService.checkout(authCredentialId);
    }

    @Operation(summary = "Get the item/quantity count of the active cart (for badge display)")
    @GetMapping("/{authCredentialId}/count")
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.cart.dto.CartCountResponse>> getCartCount(
            @PathVariable UUID authCredentialId) {
        return cartService.getCartCount(authCredentialId);
    }
}
