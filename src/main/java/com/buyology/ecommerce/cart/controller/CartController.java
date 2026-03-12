package com.buyology.ecommerce.cart.controller;

import com.buyology.ecommerce.cart.dto.AddToCartRequest;
import com.buyology.ecommerce.cart.dto.CartResponse;
import com.buyology.ecommerce.cart.dto.UpdateCartItemRequest;
import com.buyology.ecommerce.cart.service.CartService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Get the active cart for a user")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@PathVariable UUID userId) {
        return cartService.getCart(userId);
    }

    @Operation(summary = "Add a product (with optional variant and spec selections) to the cart")
    @PostMapping("/{userId}/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @PathVariable UUID userId,
            @RequestBody AddToCartRequest request) {
        return cartService.addItem(userId, request);
    }

    @Operation(summary = "Update the quantity of a cart item")
    @PatchMapping("/{userId}/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId,
            @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItemQuantity(userId, cartItemId, request);
    }

    @Operation(summary = "Remove a specific item from the cart")
    @DeleteMapping("/{userId}/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId) {
        return cartService.removeItem(userId, cartItemId);
    }

    @Operation(summary = "Clear all items from the active cart")
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> clearCart(@PathVariable UUID userId) {
        return cartService.clearCart(userId);
    }

    @Operation(summary = "Checkout the active cart")
    @PostMapping("/{userId}/checkout")
    public ResponseEntity<ApiResponse<CartResponse>> checkout(@PathVariable UUID userId) {
        return cartService.checkout(userId);
    }
}
