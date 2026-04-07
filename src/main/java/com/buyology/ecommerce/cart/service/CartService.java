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
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

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
    private final UserProfilesRepository userProfileRepo;
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
            UserProfilesRepository userProfileRepo,
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
        this.userProfileRepo = userProfileRepo;
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
        log.debug("addItem called — authCredentialId={} productId={} variantId={} storeId={} qty={}",
                authCredentialId, request.getProductId(), request.getVariantId(),
                request.getStoreId(), request.getQuantity());

        if (request.getProductId() == null) {
            log.warn("addItem rejected — productId is null [authCredentialId={}]", authCredentialId);
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "productId is required");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            log.warn("addItem rejected — invalid quantity={} [authCredentialId={} productId={}]",
                    request.getQuantity(), authCredentialId, request.getProductId());
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        AuthCredentials authCredential = authCredentialRepository.findById(authCredentialId).orElse(null);
        if (authCredential == null) {
            log.warn("addItem rejected — authCredentialId={} not found", authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }

        Product product = productRepository.findById(request.getProductId()).orElse(null);
        if (product == null || "DELETED".equals(product.getStatus())) {
            log.warn("addItem rejected — productId={} not found or deleted [authCredentialId={}]",
                    request.getProductId(), authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        // Resolve variant if provided
        ProductVariant variant = null;
        if (request.getVariantId() != null) {
            variant = variantRepository.findById(request.getVariantId()).orElse(null);
            if (variant == null || !variant.getProduct().getId().equals(product.getId())) {
                log.warn("addItem rejected — variantId={} not found or does not belong to productId={} [authCredentialId={}]",
                        request.getVariantId(), request.getProductId(), authCredentialId);
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Variant does not belong to the given product");
            }
        }

        // Resolve selected spec options
        List<ProductSpecOption> selectedSpecOptions = new ArrayList<>();
        if (request.getSpecOptionIds() != null && !request.getSpecOptionIds().isEmpty()) {
            for (UUID specOptionId : request.getSpecOptionIds()) {
                ProductSpecOption option = specOptionRepository.findById(specOptionId).orElse(null);
                if (option == null) {
                    log.warn("addItem rejected — specOptionId={} not found [authCredentialId={} productId={}]",
                            specOptionId, authCredentialId, request.getProductId());
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Spec option not found: " + specOptionId);
                }
                selectedSpecOptions.add(option);
            }
        }

        // Resolve store product — storeId is required for pricing
        if (request.getStoreId() == null) {
            log.warn("addItem rejected — storeId is null [authCredentialId={} productId={}]",
                    authCredentialId, request.getProductId());
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "storeId is required");
        }
        StoreProduct storeProduct = storeProductRepository
                .findByStore_IdAndProduct_IdAndIsActiveTrue(request.getStoreId(), product.getId())
                .orElse(null);
        if (storeProduct == null) {
            log.warn("addItem rejected — no active StoreProduct for storeId={} productId={} [authCredentialId={}]",
                    request.getStoreId(), request.getProductId(), authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product is not available in the selected store");
        }

        // Extract country + currency from this store
        String itemCountryCode = storeProduct.getStore().getCountry().getCode();
        String itemCurrency = storeProduct.getStore().getCountry().getCurrency();
        log.debug("addItem — resolved storeId={} countryCode={} currency={}",
                request.getStoreId(), itemCountryCode, itemCurrency);

        // Fetch user profile to check home country
        UserProfiles userProfile = userProfileRepo.findByUserId(authCredential.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User profile not found"));

        if (!itemCountryCode.equalsIgnoreCase(userProfile.getSelectedCountryCode())) {
            log.warn("addItem rejected — country mismatch storeCountry={} homeCountry={} [authCredentialId={}]",
                    itemCountryCode, userProfile.getSelectedCountryCode(), authCredentialId);
            return ApiResponse.failure(HttpStatus.FORBIDDEN,
                    "You can only purchase products from stores in your current country (" + 
                    userProfile.getSelectedCountryCode() + "). Browsing other countries is allowed, but purchase is restricted.");
        }

        Cart cart = findOrCreateActiveCart(authCredential);

        // Enforce single-country carts: once a country is set, all items must match
        if (cart.getCountryCode() != null && !cart.getCountryCode().equals(itemCountryCode)) {
            log.warn("addItem rejected — country mismatch cartCountry={} itemCountry={} [authCredentialId={} cartId={}]",
                    cart.getCountryCode(), itemCountryCode, authCredentialId, cart.getId());
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
                log.warn("addItem rejected — variantId={} not active in storeId={} [authCredentialId={} productId={}]",
                        variant.getId(), request.getStoreId(), authCredentialId, request.getProductId());
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
                int newQty = item.getQuantity() + request.getQuantity();
                log.debug("addItem — incrementing existing cartItemId={} qty {} -> {} [cartId={}]",
                        item.getId(), item.getQuantity(), newQty, cart.getId());
                item.setQuantity(newQty);
                item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                cartItemRepository.save(item);
                recalculateCartTotal(cart);
                return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart updated");
            }
        }

        // Create new cart item
        log.debug("addItem — creating new CartItem productId={} variantId={} qty={} unitPrice={} storeId={} [cartId={}]",
                product.getId(), variant != null ? variant.getId() : null,
                request.getQuantity(), unitPrice, request.getStoreId(), cart.getId());
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
        log.debug("updateItemQuantity called — authCredentialId={} cartItemId={} qty={}",
                authCredentialId, cartItemId, request.getQuantity());

        if (request.getQuantity() == null || request.getQuantity() < 1) {
            log.warn("updateItemQuantity rejected — invalid quantity={} [authCredentialId={} cartItemId={}]",
                    request.getQuantity(), authCredentialId, cartItemId);
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            log.warn("updateItemQuantity rejected — no active cart [authCredentialId={}]", authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        CartItem item = cartItemRepository.findById(cartItemId).orElse(null);
        if (item == null || !item.getCart().getId().equals(cart.getId())) {
            log.warn("updateItemQuantity rejected — cartItemId={} not found in cartId={} [authCredentialId={}]",
                    cartItemId, cart.getId(), authCredentialId);
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
        log.debug("removeItem called — authCredentialId={} cartItemId={}", authCredentialId, cartItemId);

        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            log.warn("removeItem rejected — no active cart [authCredentialId={}]", authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        CartItem item = cartItemRepository.findById(cartItemId).orElse(null);
        if (item == null || !item.getCart().getId().equals(cart.getId())) {
            log.warn("removeItem rejected — cartItemId={} not found in cartId={} [authCredentialId={}]",
                    cartItemId, cart.getId(), authCredentialId);
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
        // Accept both ACTIVE and CHECKED_OUT carts so this endpoint works after checkout
        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE)
                .or(() -> cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.CHECKED_OUT))
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
        cart.setCountryCode(null);
        cart.setCurrency(null);
        cartRepository.save(cart);

        return ApiResponse.success(null, "Cart cleared");
    }

    // ─── Checkout ─────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> checkout(UUID authCredentialId) {
        log.debug("checkout called — authCredentialId={}", authCredentialId);

        Cart cart = cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            log.warn("checkout rejected — no active cart [authCredentialId={}]", authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            log.warn("checkout rejected — cart is empty [authCredentialId={} cartId={}]", authCredentialId, cart.getId());
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        log.info("checkout — cartId={} authCredentialId={} itemCount={} total={} currency={}",
                cart.getId(), authCredentialId, items.size(), cart.getTotalPrice(), cart.getCurrency());
        cart.setStatus(Cart.CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart checked out successfully");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Cart findOrCreateActiveCart(UUID authCredentialId) {
        return cartRepository.findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.ACTIVE)
                .orElseGet(() -> {
                    // If a stale CHECKED_OUT cart exists, clear and reuse it rather than
                    // risking a unique-constraint violation by inserting a second cart row.
                    Cart stale = cartRepository
                            .findByAuthCredentialIdAndStatus(authCredentialId, Cart.CartStatus.CHECKED_OUT)
                            .orElse(null);
                    if (stale != null) {
                        log.debug("findOrCreateActiveCart — resetting stale CHECKED_OUT cart {} for authCredentialId={}",
                                stale.getId(), authCredentialId);
                        List<CartItem> items = cartItemRepository.findByCartId(stale.getId());
                        for (CartItem item : items) {
                            specSelectionRepository.deleteByCartItemId(item.getId());
                        }
                        cartItemRepository.deleteByCartId(stale.getId());
                        stale.setStatus(Cart.CartStatus.ACTIVE);
                        stale.setTotalPrice(BigDecimal.ZERO);
                        stale.setCountryCode(null);
                        stale.setCurrency(null);
                        return cartRepository.save(stale);
                    }
                    AuthCredentials cred = authCredentialRepository.findById(authCredentialId).orElseThrow();
                    return cartRepository.save(new Cart(cred));
                });
    }

    private Cart findOrCreateActiveCart(AuthCredentials authCredential) {
        return cartRepository.findByAuthCredentialIdAndStatus(authCredential.getId(), Cart.CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart stale = cartRepository
                            .findByAuthCredentialIdAndStatus(authCredential.getId(), Cart.CartStatus.CHECKED_OUT)
                            .orElse(null);
                    if (stale != null) {
                        log.debug("findOrCreateActiveCart — resetting stale CHECKED_OUT cart {} for authCredentialId={}",
                                stale.getId(), authCredential.getId());
                        List<CartItem> items = cartItemRepository.findByCartId(stale.getId());
                        for (CartItem item : items) {
                            specSelectionRepository.deleteByCartItemId(item.getId());
                        }
                        cartItemRepository.deleteByCartId(stale.getId());
                        stale.setStatus(Cart.CartStatus.ACTIVE);
                        stale.setTotalPrice(BigDecimal.ZERO);
                        stale.setCountryCode(null);
                        stale.setCurrency(null);
                        return cartRepository.save(stale);
                    }
                    return cartRepository.save(new Cart(authCredential));
                });
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
