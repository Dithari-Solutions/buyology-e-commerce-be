package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.order.service.DeliveryFeePolicy;
import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.domain.CartItemSpecSelection;
import com.buyology.ecommerce.cart.dto.*;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.CountryCodeUtil;
import com.buyology.ecommerce.common.utils.SecurityUtils;
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
import com.buyology.ecommerce.user.service.AccountStatusValidator;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.domain.StoreOperatingHours;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreOperatingHoursRepository;
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

    private static final double THIRTY_MIN_RADIUS_KM = com.buyology.ecommerce.store.service.ExpressDeliveryRadius.KM;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemSpecSelectionRepository specSelectionRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductSpecOptionRepository specOptionRepository;
    private final StoreProductRepository storeProductRepository;
    /** See OrderService's product-stock guard. Defaults to off; the two must agree. */
    private final boolean enforceProductStock;
    private final StoreProductVariantRepository storeProductVariantRepository;
    private final StoreLocationRepository storeLocationRepository;
    private final StoreOperatingHoursRepository operatingHoursRepository;
    private final UserProfilesRepository userProfileRepo;
    private final AccountStatusValidator accountStatusValidator;
    private final CurrencyExchangeService currencyExchangeService;
    private final DeliveryFeePolicy deliveryFeePolicy;
    /** The same tax the order pipeline applies — shared so the basket and the charge agree. */
    private final com.buyology.ecommerce.order.service.VatPolicy vatPolicy;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            CartItemSpecSelectionRepository specSelectionRepository,
            AuthCredentialRepository authCredentialRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductSpecOptionRepository specOptionRepository,
            StoreProductRepository storeProductRepository,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.stock.enforce-product-quantity:false}") boolean enforceProductStock,
            StoreProductVariantRepository storeProductVariantRepository,
            StoreLocationRepository storeLocationRepository,
            StoreOperatingHoursRepository operatingHoursRepository,
            UserProfilesRepository userProfileRepo,
            AccountStatusValidator accountStatusValidator,
            CurrencyExchangeService currencyExchangeService,
            DeliveryFeePolicy deliveryFeePolicy,
            com.buyology.ecommerce.order.service.VatPolicy vatPolicy) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.specSelectionRepository = specSelectionRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.specOptionRepository = specOptionRepository;
        this.storeProductRepository = storeProductRepository;
        this.enforceProductStock = enforceProductStock;
        this.storeProductVariantRepository = storeProductVariantRepository;
        this.storeLocationRepository = storeLocationRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.userProfileRepo = userProfileRepo;
        this.accountStatusValidator = accountStatusValidator;
        this.currencyExchangeService = currencyExchangeService;
        this.deliveryFeePolicy = deliveryFeePolicy;
        this.vatPolicy = vatPolicy;
    }

    // ─── Get or create active cart ────────────────────────────────────────────

    /**
     * @param userLat optional — when provided together with userLng, enables the quick-delivery badge
     * @param userLng optional — when provided together with userLat, enables the quick-delivery badge
     */
    // Transactional because it can CREATE the cart: the credential lock that makes find-or-create
    // atomic is only a lock for as long as a transaction holds it. Without one, each repository
    // call auto-commits and releases it immediately, and two parallel cart loads race again.
    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> getCart(UUID authCredentialId, Double userLat, Double userLng) {
        requireOwnedCredential(authCredentialId);
        Cart cart = findOrCreateActiveCart(authCredentialId);

        Set<UUID> nearbyStoreIds = resolveNearbyStoreIds(userLat, userLng);
        return ApiResponse.success(buildCartResponse(cart, nearbyStoreIds), "Cart retrieved successfully");
    }

    /**
     * Lightweight count of the active cart for badge display — does NOT create a cart.
     *
     * <p>A CHECKED_OUT cart counts. Starting a checkout flips the cart to CHECKED_OUT, and it stays
     * there until a payment actually succeeds (success marks it ABANDONED). So every shopper who
     * reached the checkout page and did not pay — abandoned at the gateway, was logged out, closed
     * the tab — is left holding one. {@link #getCart} resumes exactly that cart and shows the items
     * again, but this counter looked only at ACTIVE and answered zero, so the header badge read
     * "empty" over a cart that still had everything in it. That is the cart people reported losing
     * after signing back in: nothing was ever deleted, the badge was reporting on the wrong row.
     *
     * <p>Read-only, so it reports what {@link #getCart} WOULD resume without resuming it — a badge
     * poll must not change the cart's state.
     */
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.cart.dto.CartCountResponse>> getCartCount(UUID authCredentialId) {
        requireOwnedCredential(authCredentialId);
        Cart cart = cartRepository
                .findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.ACTIVE)
                .or(() -> cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                        authCredentialId, Cart.CartStatus.CHECKED_OUT))
                .orElse(null);
        if (cart == null) {
            return ApiResponse.success(new com.buyology.ecommerce.cart.dto.CartCountResponse(0, 0), "Cart count retrieved");
        }
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        int itemCount = items.size();
        int totalQuantity = items.stream().mapToInt(CartItem::getQuantity).sum();
        return ApiResponse.success(
                new com.buyology.ecommerce.cart.dto.CartCountResponse(itemCount, totalQuantity),
                "Cart count retrieved");
    }

    // ─── Add item to cart ─────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> addItem(UUID authCredentialId, AddToCartRequest request) {
        log.debug("addItem called — authCredentialId={} productId={} variantId={} storeId={} qty={}",
                authCredentialId, request.getProductId(), request.getVariantId(),
                request.getStoreId(), request.getQuantity());

        AuthCredentials authCredential = requireOwnedCredential(authCredentialId);
        // Block carting for accounts pending deletion — they must recover their account first.
        accountStatusValidator.requireActiveAccount(authCredential.getUserId());

        if (request.getProductId() == null) {
            log.warn("addItem rejected — productId is null [authCredentialId={}]", authCredentialId);
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "productId is required");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            log.warn("addItem rejected — invalid quantity={} [authCredentialId={} productId={}]",
                    request.getQuantity(), authCredentialId, request.getProductId());
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        Product product = productRepository.findById(request.getProductId()).orElse(null);
        if (product == null || "DELETED".equals(product.getStatus())) {
            log.warn("addItem rejected — productId={} not found or deleted [authCredentialId={}]",
                    request.getProductId(), authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        // A product an admin has marked out of stock cannot be added. The disabled button on the
        // product page used to be the only thing enforcing this, so any direct call to this
        // endpoint — or the published mobile app, which cannot be updated as fast as the web —
        // added it to the cart regardless.
        if (product.getAvailabilityStatus() == Product.AvailabilityStatus.OUT_OF_STOCK) {
            log.warn("addItem rejected — productId={} is OUT_OF_STOCK [authCredentialId={}]",
                    request.getProductId(), authCredentialId);
            return ApiResponse.failure(HttpStatus.CONFLICT, "This product is out of stock.");
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

        // Fetch user profile to check home country. A brand-new user may not have a
        // profile row yet (it's created lazily) — that just means no market country is
        // selected, so there is no country restriction to enforce. Never 400 here.
        UserProfiles userProfile = userProfileRepo.findByUserId(authCredential.getUserId()).orElse(null);

        String userCountry = userProfile != null ? userProfile.getSelectedCountryCode() : null;
        if (userCountry != null && !userCountry.isBlank()
                && !CountryCodeUtil.isSameCountry(itemCountryCode, userCountry)) {
            log.warn("addItem rejected — country mismatch storeCountry={} homeCountry={} [authCredentialId={}]",
                    itemCountryCode, userCountry, authCredentialId);
            return ApiResponse.failure(HttpStatus.FORBIDDEN,
                    "You can only purchase products from stores in your current country (" +
                    userCountry + "). Browsing other countries is allowed, but purchase is restricted.");
        }

        Cart cart = findOrCreateActiveCart(authCredential);

        // Enforce single-country carts: once a country is set, all items must match
        if (cart.getCountryCode() != null && !CountryCodeUtil.isSameCountry(cart.getCountryCode(), itemCountryCode)) {
            log.warn("addItem rejected — country mismatch cartCountry={} itemCountry={} [authCredentialId={} cartId={}]",
                    cart.getCountryCode(), itemCountryCode, authCredentialId, cart.getId());
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "All items in a cart must belong to the same country. " +
                    "Current cart country: " + cart.getCountryCode() + ", item country: " + itemCountryCode);
        }

        // Determine base price. Apply the store-level discount to the product price so
        // the cart charges (and later checks out at) the discounted price — not the raw
        // price. Variants carry their own price and have no discount in the data model.
        BigDecimal unitPrice;
        BigDecimal originalUnitPrice = null; // pre-discount price, only set when discounted
        // How many units this line can actually have. Null means the product does not track
        // stock, which is a real state here and must stay sellable.
        Integer availableUnits;
        if (variant != null) {
            StoreProductVariant storeVariant = storeProductVariantRepository
                    .findByStoreProduct_IdAndVariant_Id(storeProduct.getId(), variant.getId())
                    .orElse(null);
            if (storeVariant == null || !storeVariant.getIsActive()) {
                log.warn("addItem rejected — variantId={} not active in storeId={} [authCredentialId={} productId={}]",
                        variant.getId(), request.getStoreId(), authCredentialId, request.getProductId());
                return ApiResponse.failure(HttpStatus.NOT_FOUND, "Variant is not available in the selected store");
            }
            unitPrice = storeVariant.getStorePrice();
            availableUnits = storeVariant.getStock();
        } else {
            unitPrice = storeProduct.effectivePrice();
            if (storeProduct.hasDiscount()) {
                originalUnitPrice = storeProduct.getStorePrice();
            }
            // Product-level stock only caps the line when the switch is on, exactly as in
            // OrderService.createOrder — the two must agree, or the cart refuses something
            // checkout would have allowed, or worse allows something checkout will refuse.
            //
            // products.stock_quantity is a display-urgency hint that has been counted down past
            // whatever an admin typed for every order since V12 (the old decrement floored at zero
            // and never blocked), so a well-selling product sits at 0 while still on sale. Capping
            // against it refused add-to-cart for those products. PRE_ORDER has no ceiling either
            // way: it means "accept orders we cannot fill yet".
            //
            // available_quantity is the count that DOES cap, and needs no switch: it is null on
            // every product until an admin states a figure, so there is no drifted history to
            // protect anyone from. It caps PRE_ORDER products too — an explicit number is an
            // explicit number, and PRE_ORDER is the default for a new product, so exempting it
            // would mean the figure did nothing on most of the catalogue.
            if (product.tracksAvailableQuantity()) {
                availableUnits = product.getAvailableQuantity();
            } else {
                availableUnits = (!enforceProductStock
                        || product.getAvailabilityStatus() == Product.AvailabilityStatus.PRE_ORDER)
                        ? null
                        : product.getStockQuantity();
            }
        }

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
                if (availableUnits != null && newQty > availableUnits) {
                    log.warn("addItem rejected — {} unit(s) requested, {} in stock [cartId={} productId={}]",
                            newQty, availableUnits, cart.getId(), product.getId());
                    return ApiResponse.failure(HttpStatus.CONFLICT, outOfStockMessage(availableUnits));
                }
                log.debug("addItem — incrementing existing cartItemId={} qty {} -> {} [cartId={}]",
                        item.getId(), item.getQuantity(), newQty, cart.getId());
                item.setQuantity(newQty);
                item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                cartItemRepository.save(item);
                recalculateCartTotal(cart);
                return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart updated");
            }
        }

        if (availableUnits != null && request.getQuantity() > availableUnits) {
            log.warn("addItem rejected — {} unit(s) requested, {} in stock [cartId={} productId={}]",
                    request.getQuantity(), availableUnits, cart.getId(), product.getId());
            return ApiResponse.failure(HttpStatus.CONFLICT, outOfStockMessage(availableUnits));
        }

        // Create new cart item
        log.debug("addItem — creating new CartItem productId={} variantId={} qty={} unitPrice={} storeId={} [cartId={}]",
                product.getId(), variant != null ? variant.getId() : null,
                request.getQuantity(), unitPrice, request.getStoreId(), cart.getId());
        CartItem cartItem = new CartItem(cart, product, variant, request.getQuantity(), unitPrice, request.getStoreId());
        cartItem.setOriginalUnitPrice(originalUnitPrice);
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

        requireOwnedCredential(authCredentialId);

        if (request.getQuantity() == null || request.getQuantity() < 1) {
            log.warn("updateItemQuantity rejected — invalid quantity={} [authCredentialId={} cartItemId={}]",
                    request.getQuantity(), authCredentialId, cartItemId);
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        Cart cart = findEditableCart(authCredentialId);
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

        // The same ceiling as addItem. Without it the merge path was guarded and the PATCH path
        // was not, so typing the quantity straight into the box bypassed the check entirely.
        Integer availableUnits = availableUnitsFor(item);
        if (availableUnits != null && request.getQuantity() > availableUnits) {
            log.warn("updateItemQuantity rejected — {} unit(s) requested, {} in stock [cartId={} cartItemId={}]",
                    request.getQuantity(), availableUnits, cart.getId(), cartItemId);
            return ApiResponse.failure(HttpStatus.CONFLICT, outOfStockMessage(availableUnits));
        }

        item.setQuantity(request.getQuantity());
        item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        cartItemRepository.save(item);

        recalculateCartTotal(cart);
        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart item updated");
    }

    // ─── Stock ceilings ───────────────────────────────────────────────────────

    /**
     * How many units of an existing cart line are actually available, or null when the product
     * does not track stock.
     *
     * <p>Mirrors the resolution in {@code addItem}: a variant line is limited by the store
     * listing's own count, a variant-less line by the product's count.
     */
    private Integer availableUnitsFor(CartItem item) {
        if (item.getVariant() != null && item.getStoreId() != null) {
            return storeProductRepository
                    .findByStore_IdAndProduct_IdAndIsActiveTrue(item.getStoreId(), item.getProduct().getId())
                    .flatMap(sp -> storeProductVariantRepository
                            .findByStoreProduct_IdAndVariant_Id(sp.getId(), item.getVariant().getId()))
                    .map(StoreProductVariant::getStock)
                    .orElse(null);
        }
        Product p = item.getProduct();

        // A stated count wins, and needs no switch: available_quantity is null until an admin
        // vouches for a figure, so there is no untrustworthy history to guard against here — which
        // is the whole reason it is a separate column from stockQuantity (see V55).
        if (p.tracksAvailableQuantity()) {
            return p.getAvailableQuantity();
        }

        return (!enforceProductStock
                || p.getAvailabilityStatus() == Product.AvailabilityStatus.PRE_ORDER)
                ? null
                : p.getStockQuantity();
    }

    /**
     * What the customer is told when they ask for more than exists.
     *
     * <p>Names the number, because "out of stock" on a line they can see in their cart reads as a
     * bug. Note this is a courtesy check, not the guarantee: the cart reserves nothing, so the
     * binding refusal is the conditional decrement in {@code OrderService.createOrder}. This just
     * means they find out before they reach the payment page rather than after.
     */
    private static String outOfStockMessage(int available) {
        if (available <= 0) {
            return "This item is now out of stock.";
        }
        return available == 1
                ? "Only 1 left in stock."
                : "Only " + available + " left in stock.";
    }

    // ─── Remove item ──────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(UUID authCredentialId, UUID cartItemId) {
        log.debug("removeItem called — authCredentialId={} cartItemId={}", authCredentialId, cartItemId);

        requireOwnedCredential(authCredentialId);

        Cart cart = findEditableCart(authCredentialId);
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
        requireOwnedCredential(authCredentialId);
        // Accept both ACTIVE and CHECKED_OUT carts so this endpoint works after checkout
        Cart cart = cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.ACTIVE)
                .or(() -> cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.CHECKED_OUT))
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

        requireOwnedCredential(authCredentialId);

        Cart cart = cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            // No ACTIVE cart — a previous attempt may have left it CHECKED_OUT without a
            // completed payment (a SUCCESS would have marked it ABANDONED). Re-use that
            // cart so the user can retry instead of getting a dead-end 404.
            cart = cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.CHECKED_OUT).orElse(null);
        }
        if (cart == null) {
            log.warn("checkout rejected — no active cart [authCredentialId={}]", authCredentialId);
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }

        List<CartItem> items = cartItemRepository.findByCartIdAndSelectedTrue(cart.getId());
        if (items.isEmpty()) {
            // Distinguish "nothing here" from "nothing ticked" — the second has a self-service fix
            // the message must name.
            boolean cartHasRows = !cartItemRepository.findByCartId(cart.getId()).isEmpty();
            log.warn("checkout rejected — {} [authCredentialId={} cartId={}]",
                    cartHasRows ? "no items selected" : "cart is empty", authCredentialId, cart.getId());
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, cartHasRows
                    ? "No items are selected for checkout. Tick at least one item in your cart."
                    : "Cart is empty");
        }

        log.info("checkout — cartId={} authCredentialId={} itemCount={} total={} currency={}",
                cart.getId(), authCredentialId, items.size(), cart.getTotalPrice(), cart.getCurrency());
        cart.setStatus(Cart.CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Cart checked out successfully");
    }

    /**
     * Ticks or unticks one cart line. The row stays; only whether it will be ordered changes —
     * and with it the cart total, which is always the selected subtotal.
     */
    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> setItemSelection(
            UUID authCredentialId, UUID cartItemId, boolean selected) {
        requireOwnedCredential(authCredentialId);
        CartItem item = cartItemRepository.findById(cartItemId).orElse(null);
        if (item == null || item.getCart() == null
                || item.getCart().getAuthCredential() == null
                || !authCredentialId.equals(item.getCart().getAuthCredential().getId())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        item.setSelected(selected);
        cartItemRepository.save(item);
        Cart cart = item.getCart();
        recalculateCartTotal(cart);
        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Selection updated");
    }

    /** Ticks or unticks every line at once — the "select all" checkbox. */
    @Transactional
    public ResponseEntity<ApiResponse<CartResponse>> setAllSelection(UUID authCredentialId, boolean selected) {
        requireOwnedCredential(authCredentialId);
        Cart cart = findEditableCart(authCredentialId);
        if (cart == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "No active cart found");
        }
        for (CartItem item : cartItemRepository.findByCartId(cart.getId())) {
            item.setSelected(selected);
            cartItemRepository.save(item);
        }
        recalculateCartTotal(cart);
        return ApiResponse.success(buildCartResponse(cart, Collections.emptySet()), "Selection updated");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Loads the AuthCredentials and asserts the authenticated principal (Users.id) owns it.
     * The path-supplied authCredentialId is NOT trusted as identity — ownership is verified
     * against the JWT principal. Throws IllegalArgumentException (→400/404) if absent,
     * AccessDeniedException (→403) if the caller is not the owner.
     */
    private AuthCredentials requireOwnedCredential(UUID authCredentialId) {
        AuthCredentials credential = authCredentialRepository.findById(authCredentialId)
                .orElseThrow(() -> new IllegalArgumentException("Auth credential not found"));
        SecurityUtils.requireSelf(credential.getUserId());
        return credential;
    }

    /**
     * The shopper's cart for editing: the ACTIVE one, or the CHECKED_OUT one they are still
     * holding — resumed, because changing the basket means they are no longer mid-checkout.
     *
     * <p>Starting a checkout flips the cart to CHECKED_OUT and it stays there until a payment
     * succeeds. Anyone who abandoned at the gateway is therefore left with a cart that
     * {@link #getCart} displays in full — it resumes exactly this cart — while every button on the
     * page it renders answered 404 "No active cart found", because the mutating endpoints looked
     * for ACTIVE and nothing else. The shopper saw their items and could not change the quantity,
     * remove a line or tick anything. {@link #clearCart} already accepted both; this is the same
     * rule applied to the rest, so one lookup decides what "the cart" is for all of them.
     *
     * <p>Never creates. Callers that must have a cart use {@link #findOrCreateActiveCart}.
     */
    private Cart findEditableCart(UUID authCredentialId) {
        Cart cart = cartRepository
                .findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.ACTIVE)
                .or(() -> cartRepository.findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(
                        authCredentialId, Cart.CartStatus.CHECKED_OUT))
                .orElse(null);
        if (cart != null && cart.getStatus() == Cart.CartStatus.CHECKED_OUT) {
            log.debug("findEditableCart — resuming CHECKED_OUT cart {} for authCredentialId={} (shopper is editing again)",
                    cart.getId(), authCredentialId);
            cart.setStatus(Cart.CartStatus.ACTIVE);
            cart = cartRepository.save(cart);
        }
        return cart;
    }

    private Cart findOrCreateActiveCart(UUID authCredentialId) {
        Cart active = cartRepository
                .findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.ACTIVE)
                .orElse(null);
        if (active != null) {
            return active;
        }
        return createOrResumeUnderLock(
                authCredentialRepository.findById(authCredentialId).orElseThrow());
    }

    private Cart findOrCreateActiveCart(AuthCredentials authCredential) {
        Cart active = cartRepository
                .findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredential.getId(), Cart.CartStatus.ACTIVE)
                .orElse(null);
        if (active != null) {
            return active;
        }
        return createOrResumeUnderLock(authCredential);
    }

    /**
     * The slow path of find-or-create, serialised per shopper.
     *
     * <p>Finding no ACTIVE cart and then creating one is a read followed by an insert with nothing
     * holding the two together, and a storefront page load fires several cart calls at once. Every
     * one of them read "no cart" and every one of them inserted. What that produced depended on the
     * database: where V17's {@code ux_cart_active_per_credential} partial unique index exists, the
     * losing insert failed and the shopper was shown "A record with the same unique value already
     * exists" — the error on the checkout page. Where it does not (V17 only builds the index if the
     * carts table already existed, and on a database created since, Flyway runs before Hibernate
     * makes that table), nothing complained and the shopper quietly got TWO active carts, with
     * their items split across them and only one ever displayed.
     *
     * <p>Locking the credential row closes both. The second caller blocks until the first commits
     * and then re-reads, finding the cart that was just created instead of racing it. The lock is
     * held by Postgres rather than the JVM, so it works across replicas, and it is taken only on
     * the path that creates — an existing cart is returned above without locking anything.
     *
     * <p>MUST run inside the caller's transaction, which is why this carries no
     * {@code @Transactional} of its own: it is reached by self-invocation, so an annotation here
     * would be silently ignored by the proxy and the lock would be released by the very next
     * statement's auto-commit, leaving the race exactly as it was. Both entry points —
     * {@link #getCart} and {@link #addItem} — are transactional for that reason.
     */
    private Cart createOrResumeUnderLock(AuthCredentials authCredential) {
        UUID authCredentialId = authCredential.getId();
        AuthCredentials locked = authCredentialRepository.findByIdForUpdate(authCredentialId)
                .orElseThrow(() -> new IllegalArgumentException("Auth credential not found"));

        // Re-read under the lock: the request we were racing may have created the cart while we
        // waited, and creating a second one now is the bug this lock exists to prevent.
        Cart active = cartRepository
                .findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.ACTIVE)
                .orElse(null);
        if (active != null) {
            return active;
        }

        // If a CHECKED_OUT cart exists, payment never reached SUCCESS (success would have marked it
        // ABANDONED). Revert it to ACTIVE with items intact so the customer can resume.
        Cart stale = cartRepository
                .findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.CHECKED_OUT)
                .orElse(null);
        if (stale != null) {
            log.debug("findOrCreateActiveCart — resuming CHECKED_OUT cart {} for authCredentialId={} (preserving items)",
                    stale.getId(), authCredentialId);
            stale.setStatus(Cart.CartStatus.ACTIVE);
            return cartRepository.save(stale);
        }
        return cartRepository.save(new Cart(locked));
    }

    private void recalculateCartTotal(Cart cart) {
        // cart.totalPrice is BY DEFINITION the SELECTED subtotal — the number the shopper is about
        // to be charged. OrderService prices the order from it, calculateShippingFee's
        // free-delivery threshold reads it, and the promo validator is judged against it. Summing
        // unticked rows into it is exactly the mismatch the selection flag exists to end.
        List<CartItem> items = cartItemRepository.findByCartIdAndSelectedTrue(cart.getId());
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
    // Quick (30-minute) delivery is timezone-checked in the platform's business zone.
    private static final java.time.ZoneId BUSINESS_ZONE = java.time.ZoneId.of("Asia/Dubai");

    private Set<UUID> resolveNearbyStoreIds(Double lat, Double lng) {
        if (lat == null || lng == null) return Collections.emptySet();
        List<UUID> ids = storeLocationRepository.findStoreIdsWithinRadius(lat, lng, THIRTY_MIN_RADIUS_KM);
        // Quick delivery is only offered while the store is OPEN — validate its working hours.
        return ids.stream().filter(this::isStoreOpenNow).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Whether a store is currently within its operating hours. A store with NO hours
     * configured at all is treated as always-open (legacy proximity-only behaviour); once
     * hours are configured, quick delivery is only offered inside an open window.
     */
    private boolean isStoreOpenNow(UUID storeId) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(BUSINESS_ZONE);
        java.time.DayOfWeek today = now.getDayOfWeek();
        java.time.LocalTime time = now.toLocalTime();

        boolean anyHoursConfigured = false;
        for (StoreLocation loc : storeLocationRepository.findAllByStoreIdAndIsActive(storeId, true)) {
            List<StoreOperatingHours> hours = operatingHoursRepository.findAllByLocationId(loc.getId());
            if (!hours.isEmpty()) anyHoursConfigured = true;
            for (StoreOperatingHours h : hours) {
                if (h.getDayOfWeek() == today && isOpenAt(h, time)) return true;
            }
        }
        return !anyHoursConfigured;
    }

    private boolean isOpenAt(StoreOperatingHours h, java.time.LocalTime time) {
        if (Boolean.TRUE.equals(h.getIsClosed())) return false;
        java.time.LocalTime open = h.getOpenTime();
        java.time.LocalTime close = h.getCloseTime();
        if (open == null || close == null) return false;
        // Same-day window (e.g. 09:00–22:00) or an overnight window (e.g. 20:00–02:00).
        return close.isAfter(open)
                ? (!time.isBefore(open) && time.isBefore(close))
                : (!time.isBefore(open) || time.isBefore(close));
    }

    // Pricing policy (same source-of-truth as OrderService).
    private static final String POLICY_BASE_CURRENCY = "AED";

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

        // Pricing policy snapshot in the cart's display currency, read from the same
        // DeliveryFeePolicy bean the order uses so the two cannot disagree.
        //
        // The cart shows the STANDARD delivery rate for the cart's country: no address has been
        // chosen yet, so the delivery method is unknown, and only an address inside a store's
        // 30-minute radius resolves to EXPRESS. An order that does qualify is recalculated at
        // checkout, where the customer sees the final total before paying. The country matters
        // because Quiqup's rate is only charged in the markets they serve.
        String displayCurrency = cart.getCurrency() != null ? cart.getCurrency() : POLICY_BASE_CURRENCY;
        try {
            BigDecimal thresholdAed = deliveryFeePolicy.freeShippingThresholdAed();
            BigDecimal threshold = POLICY_BASE_CURRENCY.equalsIgnoreCase(displayCurrency)
                    ? thresholdAed
                    : currencyExchangeService.convert(thresholdAed, POLICY_BASE_CURRENCY, displayCurrency);
            BigDecimal subtotal = cart.getTotalPrice() != null ? cart.getTotalPrice() : BigDecimal.ZERO;
            boolean qualifies = subtotal.compareTo(threshold) >= 0;
            BigDecimal subtotalAed = POLICY_BASE_CURRENCY.equalsIgnoreCase(displayCurrency)
                    ? subtotal
                    : currencyExchangeService.convert(subtotal, displayCurrency, POLICY_BASE_CURRENCY);
            BigDecimal feeAed = deliveryFeePolicy.cartPreviewFeeAed(cart.getCountryCode(), subtotalAed);
            BigDecimal deliveryFee = feeAed.signum() == 0
                    ? BigDecimal.ZERO
                    : (POLICY_BASE_CURRENCY.equalsIgnoreCase(displayCurrency)
                            ? feeAed
                            : currencyExchangeService.convert(feeAed, POLICY_BASE_CURRENCY, displayCurrency));
            response.setFreeShippingThreshold(threshold);
            response.setDeliveryFee(deliveryFee);
            response.setQualifiesForFreeShipping(qualifies);

            // VAT, and the total the customer will actually be asked for.
            //
            // The basket page has to show the same number the checkout charges, so this reads the
            // SAME VatPolicy the order pipeline does rather than applying 5% of its own. The base
            // is goods + delivery, which is what the order's base will be too — no promo is applied
            // at this stage, so there is nothing to discount yet; a code entered at checkout lowers
            // the base and the tax with it, and the checkout page shows that recomputed figure.
            BigDecimal vat = vatPolicy.vatOn(subtotal.add(deliveryFee), cart.getCountryCode());
            response.setVatRatePercent(vat.signum() > 0 ? vatPolicy.ratePercent() : null);
            response.setVatAmount(vat);
            response.setEstimatedTotal(subtotal.add(deliveryFee).add(vat));
        } catch (Exception ignored) {
            // FX unavailable — leave policy fields null; clients should treat that as "unknown".
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemResponse> itemResponses = new ArrayList<>();
        for (CartItem item : items) {
            itemResponses.add(buildCartItemResponse(item, nearbyStoreIds));
        }
        response.setItems(itemResponses);

        // 30-minute delivery is priced differently from standard, so a cart that can have it must
        // say what it costs. The cart already knew this — every item carries quickDelivery — but the
        // delivery figure above is the STANDARD rate regardless, so a customer who went on to
        // choose 30-minute delivery saw one number in the cart and was charged another at checkout.
        // Quoting both leaves the standard field untouched for clients that only read that.
        // ALL selected items, not ANY item — the same rule OrderService.resolveDeliveryMethod
        // applies when it decides what to actually charge. anyMatch here is how a customer was
        // quoted the express fee and then charged a silently downgraded regular delivery. Unticked
        // rows are excluded: they are not part of the order, so an out-of-radius one must not
        // block express for a cart that qualifies.
        boolean expressAvailable = CartExpressRule.expressAvailable(
                itemResponses.stream().filter(CartItemResponse::isSelected).toList());
        response.setExpressAvailable(expressAvailable);
        // The fee is quoted unconditionally (when FX allows): availability here is judged against
        // the DEVICE's coordinates, but the order judges against the DELIVERY ADDRESS — a cart
        // that reads unavailable can still become an express order at checkout, and the customer
        // deserves the price either way.
        try {
            BigDecimal subtotal = cart.getTotalPrice() != null ? cart.getTotalPrice() : BigDecimal.ZERO;
            String ccy = cart.getCurrency() != null ? cart.getCurrency() : POLICY_BASE_CURRENCY;
            BigDecimal subtotalAed = POLICY_BASE_CURRENCY.equalsIgnoreCase(ccy)
                    ? subtotal
                    : currencyExchangeService.convert(subtotal, ccy, POLICY_BASE_CURRENCY);
            BigDecimal expressAed = deliveryFeePolicy.expressFeeAedForSubtotal(subtotalAed);
            response.setExpressDeliveryFee(
                    expressAed.signum() == 0 || POLICY_BASE_CURRENCY.equalsIgnoreCase(ccy)
                            ? expressAed
                            : currencyExchangeService.convert(expressAed, POLICY_BASE_CURRENCY, ccy));
        } catch (Exception ignored) {
            // FX unavailable — leave the express fee null, same contract as the standard fee.
        }

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
        response.setSelected(item.isSelected());
        response.setUnitPrice(item.getUnitPrice());
        response.setTotalPrice(item.getTotalPrice());
        // Pre-discount prices for strike-through display (null when not discounted).
        if (item.getOriginalUnitPrice() != null) {
            response.setOriginalUnitPrice(item.getOriginalUnitPrice());
            response.setOriginalTotalPrice(
                    item.getOriginalUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        // What a stepper is allowed to count up to. Resolved by the same helper the PATCH ceiling
        // uses, so the number the client shows and the number the server enforces cannot disagree.
        response.setAvailableUnits(availableUnitsFor(item));
        response.setQuickDelivery(item.getStoreId() != null && nearbyStoreIds.contains(item.getStoreId()));
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());

        List<CartItemSpecSelection> selections = specSelectionRepository.findByCartItemId(item.getId());
        List<CartItemSpecSelectionResponse> specResponses = new ArrayList<>();
        for (CartItemSpecSelection sel : selections) {
            CartItemSpecSelectionResponse specResp = new CartItemSpecSelectionResponse();
            specResp.setSpecOptionId(sel.getSpecOption().getId());
            String groupCode = sel.getSpecOption().getGroup().getCode();
            specResp.setGroupCode(groupCode);
            specResp.setGroupName(humanizeSpecCode(groupCode));
            specResp.setValue(sel.getSpecOption().getValue());
            if (sel.getSpecOption().getUnit() != null) {
                specResp.setUnit(sel.getSpecOption().getUnit().name());
            }
            specResp.setColorCode(sel.getSpecOption().getColorCode());
            specResponses.add(specResp);
        }
        response.setSelectedSpecs(specResponses);
        return response;
    }

    /** "operating_system" → "Operating System", "ram" → "RAM" (short tokens upper-cased). */
    private static String humanizeSpecCode(String code) {
        if (code == null || code.isBlank()) return code;
        StringBuilder sb = new StringBuilder();
        for (String part : code.split("_")) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (part.length() <= 3) sb.append(part.toUpperCase());
            else sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
