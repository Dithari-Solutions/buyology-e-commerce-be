package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.domain.CartItemSpecSelection;
import com.buyology.ecommerce.cart.dto.*;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductSpecOption;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemSpecSelectionRepository specSelectionRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductSpecOptionRepository specOptionRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            CartItemSpecSelectionRepository specSelectionRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductSpecOptionRepository specOptionRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.specSelectionRepository = specSelectionRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.specOptionRepository = specOptionRepository;
    }

    // ─── Get or create active cart ────────────────────────────────────────────

    public ResponseEntity<ApiResponse<CartResponse>> getCart(UUID userId) {
        if (!userRepository.existsById(userId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }
        Cart cart = findOrCreateActiveCart(userId);
        return ApiResponse.success(buildCartResponse(cart), "Cart retrieved successfully");
    }

    // ─── Add item to cart ─────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> addItem(UUID userId, AddToCartRequest request) {
        if (request.getProductId() == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "productId is required");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        Users user = userRepository.findById(userId)
                .orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElse(null);
        if (product == null || "DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        // Resolve variant if provided
        ProductVariant variant = null;
        if (request.getVariantId() != null) {
            variant = variantRepository.findById(request.getVariantId())
                    .orElse(null);
            if (variant == null || !variant.getProduct().getId().equals(product.getId())) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Variant does not belong to the given product");
            }
        }

        // Resolve selected spec options
        List<ProductSpecOption> selectedSpecOptions = new ArrayList<>();
        if (request.getSpecOptionIds() != null && !request.getSpecOptionIds().isEmpty()) {
            for (UUID specOptionId : request.getSpecOptionIds()) {
                ProductSpecOption option = specOptionRepository.findById(specOptionId)
                        .orElse(null);
                if (option == null) {
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Spec option not found: " + specOptionId);
                }
                selectedSpecOptions.add(option);
            }
        }

        // Calculate unit price: variant price (or product base price) + sum of spec additional prices
        BigDecimal basePrice = variant != null ? variant.getPrice() : resolveEffectiveProductPrice(product);
        BigDecimal specAdditional = selectedSpecOptions.stream()
                .map(ProductSpecOption::getAdditionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unitPrice = basePrice.add(specAdditional);

        Cart cart = findOrCreateActiveCart(user);

        // If same product+variant already in cart and no custom specs, increment quantity
        if (selectedSpecOptions.isEmpty()) {
            Optional<CartItem> existing = variant != null
                    ? cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), product.getId(), variant.getId())
                    : cartItemRepository.findByCartIdAndProductIdAndVariantIdIsNull(cart.getId(), product.getId());

            if (existing.isPresent()) {
                CartItem item = existing.get();
                item.setQuantity(item.getQuantity() + request.getQuantity());
                item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                cartItemRepository.save(item);
                recalculateCartTotal(cart);
                return ApiResponse.success(buildCartResponse(cart), "Cart updated");
            }
        }

        // Create new cart item
        CartItem cartItem = new CartItem(cart, product, variant, request.getQuantity(), unitPrice);
        cartItemRepository.save(cartItem);

        // Save spec selections
        for (ProductSpecOption option : selectedSpecOptions) {
            specSelectionRepository.save(new CartItemSpecSelection(cartItem, option));
        }

        recalculateCartTotal(cart);
        return ApiResponse.created(buildCartResponse(cart), "Item added to cart");
    }

    // ─── Update item quantity ─────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(UUID userId, UUID cartItemId, UpdateCartItemRequest request) {
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        Cart cart = cartRepository.findByUserIdAndStatus(userId, Cart.CartStatus.ACTIVE)
                .orElse(null);
        if (cart == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        CartItem item = cartItemRepository.findById(cartItemId).orElse(null);
        if (item == null || !item.getCart().getId().equals(cart.getId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Cart item not found");
        }

        item.setQuantity(request.getQuantity());
        item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        cartItemRepository.save(item);

        recalculateCartTotal(cart);
        return ApiResponse.success(buildCartResponse(cart), "Cart item updated");
    }

    // ─── Remove item ──────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(UUID userId, UUID cartItemId) {
        Cart cart = cartRepository.findByUserIdAndStatus(userId, Cart.CartStatus.ACTIVE)
                .orElse(null);
        if (cart == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        CartItem item = cartItemRepository.findById(cartItemId).orElse(null);
        if (item == null || !item.getCart().getId().equals(cart.getId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Cart item not found");
        }

        specSelectionRepository.deleteByCartItemId(cartItemId);
        cartItemRepository.delete(item);

        recalculateCartTotal(cart);
        return ApiResponse.success(buildCartResponse(cart), "Item removed from cart");
    }

    // ─── Clear cart ───────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> clearCart(UUID userId) {
        Cart cart = cartRepository.findByUserIdAndStatus(userId, Cart.CartStatus.ACTIVE)
                .orElse(null);
        if (cart == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        for (CartItem item : items) {
            specSelectionRepository.deleteByCartItemId(item.getId());
        }
        cartItemRepository.deleteByCartId(cart.getId());

        cart.setTotalPrice(BigDecimal.ZERO);
        cartRepository.save(cart);

        return ApiResponse.success(null, "Cart cleared");
    }

    // ─── Checkout ─────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> checkout(UUID userId) {
        Cart cart = cartRepository.findByUserIdAndStatus(userId, Cart.CartStatus.ACTIVE)
                .orElse(null);
        if (cart == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        cart.setStatus(Cart.CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return ApiResponse.success(buildCartResponse(cart), "Cart checked out successfully");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Cart findOrCreateActiveCart(UUID userId) {
        return cartRepository.findByUserIdAndStatus(userId, Cart.CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Users user = userRepository.findById(userId).orElseThrow();
                    return cartRepository.save(new Cart(user));
                });
    }

    private Cart findOrCreateActiveCart(Users user) {
        return cartRepository.findByUserIdAndStatus(user.getId(), Cart.CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(new Cart(user)));
    }

    private void recalculateCartTotal(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        BigDecimal total = items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalPrice(total);
        cartRepository.save(cart);
    }

    private BigDecimal resolveEffectiveProductPrice(Product product) {
        if (product.getDiscountType() == null || product.getDiscountValue() == null) {
            return product.getBasePrice();
        }
        return switch (product.getDiscountType()) {
            case FIXED -> product.getBasePrice().subtract(product.getDiscountValue()).max(BigDecimal.ZERO);
            case PERCENTAGE -> {
                BigDecimal discount = product.getBasePrice()
                        .multiply(product.getDiscountValue())
                        .divide(BigDecimal.valueOf(100));
                yield product.getBasePrice().subtract(discount).max(BigDecimal.ZERO);
            }
        };
    }

    private CartResponse buildCartResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setId(cart.getId());
        response.setUserId(cart.getUser().getId());
        response.setStatus(cart.getStatus().name());
        response.setTotalPrice(cart.getTotalPrice());
        response.setCreatedAt(cart.getCreatedAt());
        response.setUpdatedAt(cart.getUpdatedAt());

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemResponse> itemResponses = new ArrayList<>();
        for (CartItem item : items) {
            itemResponses.add(buildCartItemResponse(item));
        }
        response.setItems(itemResponses);
        return response;
    }

    private CartItemResponse buildCartItemResponse(CartItem item) {
        CartItemResponse response = new CartItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProduct().getId());
        response.setProductSku(item.getProduct().getSku());
        if (item.getVariant() != null) {
            response.setVariantId(item.getVariant().getId());
            response.setVariantSku(item.getVariant().getSku());
        }
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setTotalPrice(item.getTotalPrice());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());

        List<CartItemSpecSelection> selections = specSelectionRepository.findByCartItemId(item.getId());
        List<CartItemSpecSelectionResponse> specResponses = new ArrayList<>();
        for (CartItemSpecSelection sel : selections) {
            CartItemSpecSelectionResponse specResp = new CartItemSpecSelectionResponse();
            specResp.setSpecOptionId(sel.getSpecOption().getId());
            specResp.setGroupCode(sel.getSpecOption().getGroup().getCode());
            specResp.setValue(sel.getSpecOption().getValue());
            if (sel.getSpecOption().getUnit() != null) {
                specResp.setUnit(sel.getSpecOption().getUnit().name());
            }
            specResp.setAdditionalPrice(sel.getSpecOption().getAdditionalPrice());
            specResp.setColorCode(sel.getSpecOption().getColorCode());
            specResponses.add(specResp);
        }
        response.setSelectedSpecs(specResponses);
        return response;
    }
}
