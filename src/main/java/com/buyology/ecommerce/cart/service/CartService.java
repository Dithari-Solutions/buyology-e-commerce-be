package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.domain.CartItemSpecSelection;
import com.buyology.ecommerce.cart.dto.*;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductSpecOption;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CartService {

    private static final double THIRTY_MIN_RADIUS_KM = 12.5;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemSpecSelectionRepository specSelectionRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductSpecOptionRepository specOptionRepository;
    private final StoreProductRepository storeProductRepository;
    private final StoreProductVariantRepository storeProductVariantRepository;
    private final StoreLocationRepository storeLocationRepository;
    private final CurrencyExchangeService currencyExchangeService;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            CartItemSpecSelectionRepository specSelectionRepository,
            AuthCredentialRepository authCredentialRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductSpecOptionRepository specOptionRepository,
            StoreProductRepository storeProductRepository,
            StoreProductVariantRepository storeProductVariantRepository,
            StoreLocationRepository storeLocationRepository,
            CurrencyExchangeService currencyExchangeService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.specSelectionRepository = specSelectionRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.specOptionRepository = specOptionRepository;
        this.storeProductRepository = storeProductRepository;
        this.storeProductVariantRepository = storeProductVariantRepository;
        this.storeLocationRepository = storeLocationRepository;
        this.currencyExchangeService = currencyExchangeService;
    }

    // ─── Get or create active cart ────────────────────────────────────────────

    /**
     * @param userLat optional — when provided together with userLng, enables the quick-delivery badge
     * @param userLng optional — when provided together with userLat, enables the quick-delivery badge
     */
    public ResponseEntity<ApiResponse<CartResponse>> getCart(UUID authCredentialId, Double userLat, Double userLng) {
        if (!authCredentialRepository.existsById(authCredentialId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }
        Cart cart = findOrCreateActiveCart(authCredentialId);

        Set<UUID> nearbyStoreIds = resolveNearbyStoreIds(userLat, userLng);
        return ApiResponse.success(buildCartResponse(cart, nearbyStoreIds), "Cart retrieved successfully");
    }

    // ─── Add item to cart ─────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> addItem(UUID authCredentialId, AddToCartRequest request) {
        if (request.getProductId() == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "productId is required");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        AuthCredentials authCredential = authCredentialRepository.findById(authCredentialId).orElse(null);
        if (authCredential == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }

        Product product = productRepository.findById(request.getProductId()).orElse(null);
        if (product == null || "DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        // Resolve variant if provided
        ProductVariant variant = null;
        if (request.getVariantId() != null) {
            variant = variantRepository.findById(request.getVariantId()).orElse(null);
            if (variant == null || !variant.getProduct().getId().equals(product.getId())) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Variant does not belong to the given product");
            }
        }

        // Resolve selected spec options
        List<ProductSpecOption> selectedSpecOptions = new ArrayList<>();
        if (request.getSpecOptionIds() != null && !request.getSpecOptionIds().isEmpty()) {
            for (UUID specOptionId : request.getSpecOptionIds()) {
                ProductSpecOption option = specOptionRepository.findById(specOptionId).orElse(null);
                if (option == null) {
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Spec option not found: " + specOptionId);
                }
                selectedSpecOptions.add(option);
            }
        }

        // Resolve store product — storeId is required for pricing
        if (request.getStoreId() == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "storeId is required");
        }
        StoreProduct storeProduct = storeProductRepository
                .findByStore_IdAndProduct_IdAndIsActiveTrue(request.getStoreId(), product.getId())
                .orElse(null);
        if (storeProduct == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product is not available in the selected store");
        }

        // Extract country + currency from this store
        String itemCountryCode = storeProduct.getStore().getCountry().getCode();
        String itemCurrency = storeProduct.getStore().getCountry().getCurrency();

        Cart cart = findOrCreateActiveCart(authCredential);

        // Enforce single-country carts: once a country is set, all items must match
        if (cart.getCountryCode() != null && !cart.getCountryCode().equals(itemCountryCode)) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "All items in a cart must belong to the same country. " +
                    "Current cart country: " + cart.getCountryCode() + ", item country: " + itemCountryCode);
        }

        // Determine base price
        BigDecimal basePrice;
        if (variant != null) {
            StoreProductVariant storeVariant = storeProductVariantRepository
                    .findByStoreProduct_IdAndVariant_Id(storeProduct.getId(), variant.getId())
                    .orElse(null);
            if (storeVariant == null || !storeVariant.getIsActive()) {
                return ApiResponse.failure(HttpStatus.NOT_FOUND, "Variant is not available in the selected store");
            }
            basePrice = storeVariant.getStorePrice();
        } else {
            basePrice = storeProduct.getStorePrice();
        }

        BigDecimal specAdditional = selectedSpecOptions.stream()
                .map(ProductSpecOption::getAdditionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unitPrice = basePrice.add(specAdditional);

        // Stamp the cart with country + currency on first item
        if (cart.getCountryCode() == null) {
            cart.setCountryCode(itemCountryCode);
            cart.setCurrency(itemCurrency);
        }

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
                return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart updated");
            }
        }

        // Create new cart item
        CartItem cartItem = new CartItem(cart, product, variant, request.getQuantity(), unitPrice, request.getStoreId());
        cartItemRepository.save(cartItem);

        // Save spec selections
        for (ProductSpecOption option : selectedSpecOptions) {
            specSelectionRepository.save(new CartItemSpecSelection(cartItem, option));
        }

        recalculateCartTotal(cart);
        return ApiResponse.created(buildCartResponse(cart, Collections.emptySet()), "Item added to cart");
    }

    // ─── Update item quantity ─────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(UUID authCredentialId, UUID cartItemId, UpdateCartItemRequest request) {
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
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
        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart item updated");
    }

    // ─── Remove item ──────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(UUID authCredentialId, UUID cartItemId) {
        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
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
        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Item removed from cart");
    }

    // ─── Clear cart ───────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> clearCart(UUID authCredentialId) {
        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        for (CartItem item : items) {
            specSelectionRepository.deleteByCartItemId(item.getId());
        }
        cartItemRepository.deleteByCartId(cart.getId());

        cart.setTotalPrice(BigDecimal.ZERO);
        cart.setCountryCode(null);
        cart.setCurrency(null);
        cartRepository.save(cart);

        return ApiResponse.success(null, "Cart cleared");
    }

    // ─── Checkout ─────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> checkout(UUID authCredentialId) {
        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        cart.setStatus(Cart.CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart checked out successfully");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Cart findOrCreateActiveCart(UUID authCredentialId) {
        return cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE)
                .orElseGet(() -> {
                    AuthCredentials cred = authCredentialRepository.findById(authCredentialId).orElseThrow();
                    return cartRepository.save(new Cart(cred));
                });
    }

    private Cart findOrCreateActiveCart(AuthCredentials authCredential) {
        return cartRepository.findByAuthCredentialIdAndStatus(authCredential.getId(), Cart.CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(new Cart(authCredential)));
    }

    private void recalculateCartTotal(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        BigDecimal total = items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalPrice(total);
        cartRepository.save(cart);
    }

    /**
     * Returns the set of store IDs within the 30-minute delivery radius.
     * Returns an empty set when coordinates are not provided.
     */
    private Set<UUID> resolveNearbyStoreIds(Double lat, Double lng) {
        if (lat == null || lng == null) return Collections.emptySet();
        List<UUID> ids = storeLocationRepository.findStoreIdsWithinRadius(lat, lng, THIRTY_MIN_RADIUS_KM);
        return new HashSet<>(ids);
    }

    private CartResponse buildCartResponse(Cart cart, Set<UUID> nearbyStoreIds) {
        CartResponse response = new CartResponse();
        response.setId(cart.getId());
        response.setAuthCredentialId(cart.getAuthCredential().getId());
        response.setStatus(cart.getStatus().name());
        response.setTotalPrice(cart.getTotalPrice());
        response.setCountryCode(cart.getCountryCode());
        response.setCurrency(cart.getCurrency());
        response.setCreatedAt(cart.getCreatedAt());
        response.setUpdatedAt(cart.getUpdatedAt());

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemResponse> itemResponses = new ArrayList<>();
        for (CartItem item : items) {
            itemResponses.add(buildCartItemResponse(item, nearbyStoreIds));
        }
        response.setItems(itemResponses);
        return response;
    }

    private CartItemResponse buildCartItemResponse(CartItem item, Set<UUID> nearbyStoreIds) {
        CartItemResponse response = new CartItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProduct().getId());
        response.setProductSku(item.getProduct().getSku());
        if (item.getVariant() != null) {
            response.setVariantId(item.getVariant().getId());
            response.setVariantSku(item.getVariant().getSku());
        }
        response.setStoreId(item.getStoreId());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setTotalPrice(item.getTotalPrice());
        response.setQuickDelivery(item.getStoreId() != null && nearbyStoreIds.contains(item.getStoreId()));
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
