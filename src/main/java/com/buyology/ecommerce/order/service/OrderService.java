package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.order.dto.BuyNowOrderRequest;
import com.buyology.ecommerce.common.outbox.OutboxEvent;
import com.buyology.ecommerce.common.outbox.OutboxEventRepository;
import com.buyology.ecommerce.promo.dto.ValidatePromoCodeResponse;
import com.buyology.ecommerce.promo.service.PromoCodeService;
import com.buyology.ecommerce.courier.CourierOrderRequest;
import com.buyology.ecommerce.courier.CourierServiceClient;
import com.buyology.ecommerce.courier.DeliveryRabbitMQConfig;
import com.buyology.ecommerce.courier.messaging.event.OrderCancelledEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.OrderTrackingEvent;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.dto.*;
import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.order.event.PaymentFailedEvent;
import com.buyology.ecommerce.order.exception.OrderNotFoundException;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.order.repository.OrderTrackingEventRepository;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final double THIRTY_MIN_RADIUS_KM = 12.5;

    // Pricing constants (in AED, the base settlement currency).
    // Express delivery: 15 AED if subtotal < 100 AED, free otherwise.
    private static final String BASE_CURRENCY = "AED";
    private static final BigDecimal EXPRESS_DELIVERY_FEE = new BigDecimal("15.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("100.00");

    private final OrderRepository orderRepo;
    private final OrderTrackingEventRepository trackingRepo;
    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final CartItemSpecSelectionRepository cartItemSpecSelectionRepo;
    private final UserAddressRepository addressRepo;
    private final PaymentTransactionRepository paymentTransactionRepo;
    private final StoreLocationRepository storeLocationRepo;
    private final StoreProductRepository storeProductRepo;
    private final StoreProductVariantRepository storeProductVariantRepo;
    private final UserProfilesRepository userProfileRepo;
    private final CurrencyExchangeService currencyExchangeService;
    private final ObjectMapper objectMapper;
    private final CourierServiceClient courierServiceClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ContaboObjectService contaboObjectService;
    private final OutboxEventRepository outboxEventRepository;
    private final PromoCodeService promoCodeService;
    private final com.buyology.ecommerce.notification.service.PushNotificationService pushService;
    private final com.buyology.ecommerce.supplier.repository.SupplierRepository supplierRepository;
    private final com.buyology.ecommerce.role.repository.UserRoleRepository userRoleRepository;
    private final com.buyology.ecommerce.courier.profile.repository.CourierProfileRepository courierProfileRepository;
    private final com.buyology.ecommerce.common.service.EmailService emailService;
    private final com.buyology.ecommerce.auth.repository.AuthCredentialRepository authCredentialRepository;
    // Lazy provider avoids a construction-time cycle (PaymentService publishes the events this service listens to).
    private final org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.payment.service.PaymentService> paymentServiceProvider;

    public OrderService(OrderRepository orderRepo,
                        OrderTrackingEventRepository trackingRepo,
                        CartRepository cartRepo,
                        CartItemRepository cartItemRepo,
                        CartItemSpecSelectionRepository cartItemSpecSelectionRepo,
                        UserAddressRepository addressRepo,
                        PaymentTransactionRepository paymentTransactionRepo,
                        StoreLocationRepository storeLocationRepo,
                        StoreProductRepository storeProductRepo,
                        StoreProductVariantRepository storeProductVariantRepo,
                        UserProfilesRepository userProfileRepo,
                        CurrencyExchangeService currencyExchangeService,
                        ObjectMapper objectMapper,
                        CourierServiceClient courierServiceClient,
                        SimpMessagingTemplate messagingTemplate,
                        ContaboObjectService contaboObjectService,
                        OutboxEventRepository outboxEventRepository,
                        PromoCodeService promoCodeService,
                        com.buyology.ecommerce.notification.service.PushNotificationService pushService,
                        com.buyology.ecommerce.supplier.repository.SupplierRepository supplierRepository,
                        com.buyology.ecommerce.role.repository.UserRoleRepository userRoleRepository,
                        com.buyology.ecommerce.courier.profile.repository.CourierProfileRepository courierProfileRepository,
                        com.buyology.ecommerce.common.service.EmailService emailService,
                        com.buyology.ecommerce.auth.repository.AuthCredentialRepository authCredentialRepository,
                        org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.payment.service.PaymentService> paymentServiceProvider) {
        this.orderRepo = orderRepo;
        this.trackingRepo = trackingRepo;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.cartItemSpecSelectionRepo = cartItemSpecSelectionRepo;
        this.addressRepo = addressRepo;
        this.paymentTransactionRepo = paymentTransactionRepo;
        this.storeLocationRepo = storeLocationRepo;
        this.storeProductRepo = storeProductRepo;
        this.storeProductVariantRepo = storeProductVariantRepo;
        this.userProfileRepo = userProfileRepo;
        this.currencyExchangeService = currencyExchangeService;
        this.objectMapper = objectMapper;
        this.courierServiceClient = courierServiceClient;
        this.messagingTemplate = messagingTemplate;
        this.contaboObjectService = contaboObjectService;
        this.outboxEventRepository = outboxEventRepository;
        this.promoCodeService = promoCodeService;
        this.pushService = pushService;
        this.supplierRepository = supplierRepository;
        this.userRoleRepository = userRoleRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.emailService = emailService;
        this.authCredentialRepository = authCredentialRepository;
        this.paymentServiceProvider = paymentServiceProvider;
    }

    // =========================================================================
    // Create order
    // =========================================================================

    /**
     * Creates an order from a CHECKED_OUT cart.
     * The order starts in PENDING_PAYMENT; it transitions to PAID automatically
     * when the payment webhook fires a PaymentSucceededEvent.
     */
    @Transactional
    public OrderResponse createOrder(UUID userId, UUID authCredentialId, CreateOrderRequest req) {
        Cart cart = cartRepo.findById(req.getCartId())
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + req.getCartId()));

        if (cart.getAuthCredential() == null ||
                !authCredentialId.equals(cart.getAuthCredential().getId())) {
            throw new IllegalArgumentException("Cart does not belong to the authenticated user");
        }

        if (cart.getStatus() != Cart.CartStatus.CHECKED_OUT) {
            throw new IllegalStateException(
                    "Cart must be in CHECKED_OUT status to create an order. Current status: " + cart.getStatus());
        }

        List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot create an order from an empty cart");
        }

        UserAddress address = addressRepo.findById(req.getAddressId())
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + req.getAddressId()));

        if (!userId.equals(address.getUser().getId())) {
            throw new IllegalArgumentException("Address does not belong to the authenticated user");
        }

        // Validate customer country. The order is constrained to the user's market:
        // their explicitly selected country, or (if unset) the country the cart was
        // browsed/priced in. If neither is set there is no market constraint, so we
        // accept delivery to the address's own country instead of failing with "(null)".
        UserProfiles profile = userProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found"));

        // A verified phone number is required to place an order (delivery contact).
        if (!profile.isPhoneVerified()) {
            throw new IllegalStateException("Please verify your phone number before placing an order.");
        }

        String marketCountry = profile.getSelectedCountryCode();
        if (marketCountry == null || marketCountry.isBlank()) {
            marketCountry = cart.getCountryCode();
        }
        if (marketCountry != null && !marketCountry.isBlank()
                && !isSameCountry(address.getCountry(), marketCountry)) {
            throw new IllegalArgumentException("You can only purchase products for delivery in your selected country ("
                    + marketCountry + ").");
        }

        // Build order
        Order order = new Order();
        order.setUserId(userId);
        order.setAuthCredentialId(authCredentialId);
        order.setCartId(cart.getId());

        // Resolve delivery method and fees if not explicitly provided (or even if provided, re-calculate for security)
        DeliveryMethod method = req.getDeliveryMethod() != null ? req.getDeliveryMethod() : resolveDeliveryMethod(cartItems, address);
        BigDecimal shippingFee = calculateShippingFee(method, cart.getTotalPrice(), cart.getCurrency());
        String estimatedDeliveryTime = estimateDeliveryTime(method);

        order.setDeliveryMethod(method);
        order.setShippingFee(shippingFee);
        order.setEstimatedDeliveryTime(estimatedDeliveryTime);
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        // Address snapshot
        order.setDeliveryAddressId(address.getId());
        order.setRecipientFirstName(address.getFirstName());
        order.setRecipientLastName(address.getLastName());
        order.setRecipientPhone(address.getPhoneNumber());
        order.setAddressLine1(address.getAddressLine1());
        order.setAddressLine2(address.getAddressLine2());
        order.setCity(address.getCity());
        order.setState(address.getState());
        order.setCountry(address.getCountry());
        order.setPostalCode(address.getPostalCode());
        order.setDeliveryLatitude(address.getLatitude());
        order.setDeliveryLongitude(address.getLongitude());

        // Pricing
        BigDecimal subtotal = cart.getTotalPrice();
        BigDecimal discount = BigDecimal.ZERO;
        UUID appliedPromoId = null;

        if (req.getCouponCode() != null && !req.getCouponCode().isBlank()) {
            List<UUID> productIds = cartItems.stream()
                    .map(ci -> ci.getProduct().getId())
                    .collect(java.util.stream.Collectors.toList());
            ValidatePromoCodeResponse promoResult = promoCodeService.validateAndCalculate(
                    req.getCouponCode(), userId, subtotal, productIds);
            if (promoResult.isValid()) {
                discount = promoResult.getDiscountAmount();
                appliedPromoId = promoResult.getPromoCodeId();
            }
        }

        // Clamp discount so it can never exceed subtotal + shipping (otherwise the total
        // would go negative). Then clamp the total at zero as a final safety floor.
        BigDecimal grossTotal = subtotal.add(shippingFee);
        if (discount.compareTo(grossTotal) > 0) {
            discount = grossTotal;
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        BigDecimal totalAmount = grossTotal.subtract(discount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setTotalAmount(totalAmount);
        
        // Ensure currency is never null (fall back to profile if cart was somehow not stamped)
        String currency = cart.getCurrency();
        if (currency == null || currency.isBlank()) {
            currency = profile.getPreferredCurrency();
        }
        order.setCurrency(currency);
        
        order.setCountryCode(marketCountry != null && !marketCountry.isBlank() ? marketCountry : address.getCountry());
        order.setCouponCode(req.getCouponCode());
        order.setPromoCodeId(appliedPromoId);

        order = orderRepo.save(order);

        // Convert cart items to order items, decrementing store inventory atomically.
        for (CartItem cartItem : cartItems) {
            // Decrement stock atomically and guard against overselling. Stock lives on
            // StoreProductVariant only — items without a variant have no stock to track,
            // so they are skipped (StoreProduct has no stock column). This runs inside the
            // surrounding @Transactional boundary so a failed guard rolls back the order.
            if (cartItem.getVariant() != null && cartItem.getStoreId() != null) {
                int updated = storeProductVariantRepo.decrementStock(
                        cartItem.getStoreId(),
                        cartItem.getProduct().getId(),
                        cartItem.getVariant().getId(),
                        cartItem.getQuantity());
                if (updated != 1) {
                    throw new IllegalStateException("Insufficient stock for product "
                            + cartItem.getProduct().getId() + " variant " + cartItem.getVariant().getId()
                            + " (requested " + cartItem.getQuantity() + ")");
                }
            }

            // Soft-decrement the product's admin-managed display stock (drives the
            // storefront's "almost sold out" urgency). Floored at 0; never blocks the
            // order. The managed entity flushes on commit.
            Product orderedProduct = cartItem.getProduct();
            if (orderedProduct.getStockQuantity() != null) {
                orderedProduct.setStockQuantity(
                        Math.max(0, orderedProduct.getStockQuantity() - cartItem.getQuantity()));
            }

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(cartItem.getProduct().getId());
            item.setVariantId(cartItem.getVariant() != null ? cartItem.getVariant().getId() : null);
            item.setStoreId(cartItem.getStoreId());
            item.setProductSku(cartItem.getProduct().getSku());
            item.setVariantSku(cartItem.getVariant() != null ? cartItem.getVariant().getSku() : null);
            item.setQuantity(cartItem.getQuantity());
            item.setUnitPrice(cartItem.getUnitPrice());
            item.setTotalPrice(cartItem.getTotalPrice());
            item.setSupplierId(cartItem.getProduct().getSupplierId());
            order.getItems().add(item);
        }

        // Initial tracking event
        appendTrackingEvent(order, OrderStatus.PENDING_PAYMENT, "Order created, awaiting payment",
                null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");

        order = orderRepo.save(order);

        // NOTE: promo usage is now recorded on payment success (recordPromoUsageOnPaid),
        // not here — so a code only counts as redeemed once the order is actually paid.

        return toOrderResponse(order);
    }

    /**
     * Creates a "Buy Now" order for a SINGLE product without disturbing the user's
     * persistent cart. We build a throwaway, single-item cart (separate from the
     * active cart), mark it CHECKED_OUT, and run it through the exact same {@link
     * #createOrder} pipeline — so pricing, stock, promo, and payment behave
     * identically to a normal checkout. The user's real cart is never touched, and
     * the ephemeral cart is cleared on payment success like any other.
     */
    @Transactional
    public OrderResponse createBuyNowOrder(UUID userId, UUID authCredentialId, BuyNowOrderRequest req) {
        if (req.getProductId() == null || req.getStoreId() == null) {
            throw new IllegalArgumentException("productId and storeId are required");
        }
        int quantity = (req.getQuantity() != null && req.getQuantity() > 0) ? req.getQuantity() : 1;

        StoreProduct storeProduct = storeProductRepo
                .findByStore_IdAndProduct_IdAndIsActiveTrue(req.getStoreId(), req.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product is not available in the selected store"));

        Product product = storeProduct.getProduct();
        if (product == null || "DELETED".equals(product.getStatus())) {
            throw new IllegalArgumentException("Product not found");
        }

        String storeCountry = storeProduct.getStore().getCountry().getCode();
        String storeCurrency = storeProduct.getStore().getCountry().getCurrency();

        UserProfiles profile = userProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found"));

        // Same single-country purchase rule as add-to-cart: you can browse other
        // countries but only buy from stores in your selected country.
        String userCountry = profile.getSelectedCountryCode();
        if (userCountry != null && !userCountry.isBlank() && !isSameCountry(storeCountry, userCountry)) {
            throw new IllegalArgumentException(
                    "You can only purchase products from stores in your selected country (" + userCountry + ").");
        }

        // Resolve the discounted store price in the store's native currency (same as
        // CartService.addItem). No variant: matches the product-detail Buy Now flow,
        // and createOrder only decrements stock for variant items.
        BigDecimal unitPrice = storeProduct.effectivePrice();
        BigDecimal originalUnitPrice = storeProduct.hasDiscount() ? storeProduct.getStorePrice() : null;

        AuthCredentials credential = authCredentialRepository.findById(authCredentialId)
                .orElseThrow(() -> new IllegalArgumentException("Auth credential not found"));

        // Ephemeral, single-item cart — NOT the user's active cart.
        Cart cart = new Cart(credential);
        cart.setCountryCode(storeCountry);
        cart.setCurrency(storeCurrency);
        cart = cartRepo.save(cart);

        CartItem item = new CartItem(cart, product, null, quantity, unitPrice, req.getStoreId());
        item.setOriginalUnitPrice(originalUnitPrice);
        cartItemRepo.save(item);

        cart.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        cart.setStatus(Cart.CartStatus.CHECKED_OUT);
        cartRepo.save(cart);

        // Reuse the tested order pipeline (address/country checks, pricing, promo,
        // shipping, stock, payment integration) on the ephemeral cart.
        CreateOrderRequest orderReq = new CreateOrderRequest();
        orderReq.setCartId(cart.getId());
        orderReq.setAddressId(req.getAddressId());
        orderReq.setDeliveryMethod(req.getDeliveryMethod());
        orderReq.setShippingFee(req.getShippingFee());
        orderReq.setCouponCode(req.getCouponCode());

        OrderResponse response = createOrder(userId, authCredentialId, orderReq);

        // The ephemeral cart has served its purpose. Mark it ABANDONED so it is
        // never resumed as the user's active cart — findOrCreateActiveCart only
        // resumes CHECKED_OUT carts, never ABANDONED ones — which would otherwise
        // merge this single-product checkout into their real shopping cart on a
        // failed/abandoned payment. It's still cleared by cartId on payment success.
        cart.setStatus(Cart.CartStatus.ABANDONED);
        cartRepo.save(cart);

        return response;
    }

    // =========================================================================
    // Payment event listener
    // =========================================================================

    /**
     * Listens for PaymentSucceededEvent published by PaymentService.
     * Runs in a new transaction AFTER the payment transaction has committed to SUCCESS,
     * so order-creation failures never roll back the payment status.
     *
     * Two paths:
     * 1. Pre-created order: appOrderId is set → transition PENDING_PAYMENT → PAID.
     * 2. Cart-first flow: appOrderId is null, cartId is set → create the order from the cart,
     *    auto-determine delivery method, mark PAID, clear the cart, back-fill appOrderId.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("[ORDER] PaymentSucceededEvent received: transactionId={}", event.getTransactionId());
        PaymentTransaction tx = paymentTransactionRepo.findById(event.getTransactionId())
                .orElse(null);
        if (tx == null) {
            log.warn("[ORDER] No PaymentTransaction found for id={}", event.getTransactionId());
            return;
        }

        if (tx.getAppOrderId() != null) {
            // Path 1 — order already exists, just transition it to PAID
            orderRepo.findById(tx.getAppOrderId()).ifPresent(order -> {
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    if (!isPaidAmountSufficient(order, tx)) {
                        log.error("[ORDER] Underpayment detected for order {} (tx {}): paid {} {} < order total {} {}. "
                                        + "Leaving order in PENDING_PAYMENT for manual review.",
                                order.getId(), tx.getId(), tx.getAmount(), tx.getCurrency(),
                                order.getTotalAmount(), order.getCurrency());
                        return;
                    }
                    order.setStatus(OrderStatus.PAID);
                    order.setPaymentTransactionId(event.getTransactionId());
                    order.setPaidAt(Instant.now());
                    appendTrackingEvent(order, OrderStatus.PAID, "Payment confirmed",
                            null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                    orderRepo.save(order);

                    // Record promo redemption now that the order is actually paid.
                    recordPromoUsageOnPaid(order);

                    // Notify suppliers (owning items) + superadmins of the new paid order.
                    notifyNewOrder(order);

                    // Clear the cart safely (idempotent)
                    if (order.getCartId() != null) {
                        clearCartItemsSafely(order.getCartId());
                    }
                }
            });
        } else if (tx.getCartId() != null) {
            // Path 2 — cart-first flow
            log.info("[ORDER] Cart-first flow: cartId={}", tx.getCartId());
            Cart cart = cartRepo.findById(tx.getCartId()).orElse(null);
            
            // If already cleared, the cart items list will be empty
            if (cart == null || cart.getStatus() == Cart.CartStatus.ABANDONED) {
                log.info("[ORDER] Cart {} already processed or ABANDONED.", tx.getCartId());
                return;
            }

            List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());
            if (cartItems.isEmpty()) {
                log.warn("[ORDER] Cart has no items: cartId={}", cart.getId());
                return;
            }

            // Parse address, shipping fee, and delivery method from transaction metadata
            UUID addressId = null;
            BigDecimal shippingFee = BigDecimal.ZERO;
            DeliveryMethod metaDeliveryMethod = null;
            try {
                if (tx.getMetadata() != null) {
                    log.info("[ORDER] Transaction metadata: {}", tx.getMetadata());
                    JsonNode meta = objectMapper.readTree(tx.getMetadata());
                    if (meta.has("addressId")) addressId = UUID.fromString(meta.get("addressId").asText());
                    if (meta.has("shippingFee")) shippingFee = new BigDecimal(meta.get("shippingFee").asText());
                    if (meta.has("deliveryMethod")) {
                        metaDeliveryMethod = DeliveryMethod.fromValue(meta.get("deliveryMethod").asText());
                    }
                } else {
                    log.warn("[ORDER] Transaction metadata is null: txId={}", tx.getId());
                }
            } catch (Exception e) {
                log.warn("[ORDER] Failed to parse transaction metadata: {}", e.getMessage());
            }

            if (addressId == null) {
                log.warn("[ORDER] addressId is null — cannot create order. metadata={}", tx.getMetadata());
                return;
            }

            UserAddress address = addressRepo.findById(addressId).orElse(null);
            if (address == null) {
                log.warn("[ORDER] UserAddress not found: addressId={}", addressId);
                return;
            }

            if (cart.getAuthCredential() == null) {
                log.warn("[ORDER] Cart has no authCredential: cartId={}", cart.getId());
                return;
            }
            UUID userId = cart.getAuthCredential().getUserId();
            UUID authCredentialId = cart.getAuthCredential().getId();
            if (userId == null || authCredentialId == null) {
                log.warn("[ORDER] Missing userId={} or authCredentialId={}", userId, authCredentialId);
                return;
            }

            // Transition cart to CHECKED_OUT so createOrder's status guard passes
            if (cart.getStatus() != Cart.CartStatus.CHECKED_OUT) {
                cart.setStatus(Cart.CartStatus.CHECKED_OUT);
                cartRepo.save(cart);
            }

            // Use delivery method from metadata if the frontend sent it, BUT only trust
            // EXPRESS when the address has lat/lng — without coordinates we cannot route
            // to the courier backend. Fall back to resolveDeliveryMethod otherwise.
            boolean addressHasCoordinates = address.getLatitude() != null && address.getLongitude() != null;
            DeliveryMethod deliveryMethod = (metaDeliveryMethod != null && (metaDeliveryMethod != DeliveryMethod.EXPRESS || addressHasCoordinates))
                    ? metaDeliveryMethod
                    : resolveDeliveryMethod(cartItems, address);

            CreateOrderRequest req = new CreateOrderRequest();
            req.setCartId(tx.getCartId());
            req.setAddressId(addressId);
            req.setDeliveryMethod(deliveryMethod);
            req.setShippingFee(shippingFee);

            OrderResponse orderResponse = createOrder(userId, authCredentialId, req);

            // Transition immediately to PAID — unless the amount actually paid does not
            // cover the order total computed server-side from the cart (amount tampering).
            Order order = orderRepo.findById(orderResponse.getId()).orElse(null);
            if (order != null) {
                if (!isPaidAmountSufficient(order, tx)) {
                    log.error("[ORDER] Underpayment detected for cart-first order {} (tx {}): paid {} {} < order total {} {}. "
                                    + "Leaving order in PENDING_PAYMENT for manual review.",
                            order.getId(), tx.getId(), tx.getAmount(), tx.getCurrency(),
                            order.getTotalAmount(), order.getCurrency());
                } else {
                    order.setStatus(OrderStatus.PAID);
                    order.setPaymentTransactionId(event.getTransactionId());
                    order.setPaidAt(Instant.now());
                    appendTrackingEvent(order, OrderStatus.PAID, "Payment confirmed",
                            null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                    orderRepo.save(order);
                    // Record promo redemption now that the order is actually paid.
                    recordPromoUsageOnPaid(order);
                    // Notify suppliers + superadmins of the new paid order.
                    notifyNewOrder(order);
                }
            }

            // Back-fill the transaction so future queries can find the order
            tx.setAppOrderId(orderResponse.getId());
            paymentTransactionRepo.save(tx);

            // Courier-backend integration disabled — orders are now managed
            // entirely by admin from the dashboard.
            // if (order != null && order.getDeliveryMethod() == DeliveryMethod.EXPRESS) {
            //     pushToCourier(order);
            // }

            // Clear cart items and mark cart ABANDONED so the customer can start fresh
            clearCartItemsSafely(cart.getId());
        }
    }

    /**
     * Defense-in-depth amount reconciliation: confirm the amount actually paid (the
     * HMAC-verified transaction) covers the order's authoritative server-side total,
     * net of any B2B credit already applied. Compares in the transaction's currency
     * with a 1% tolerance for rounding / FX drift. Fails OPEN on a computation error
     * (logged) so a transient FX glitch never strands a legitimate payment.
     */
    private boolean isPaidAmountSufficient(Order order, PaymentTransaction tx) {
        try {
            BigDecimal due = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            if (order.getCreditApplied() != null && order.getCreditApplied().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal creditInOrderCcy = order.getCreditCurrency() != null
                        && order.getCreditCurrency().equalsIgnoreCase(order.getCurrency())
                        ? order.getCreditApplied()
                        : currencyExchangeService.convert(order.getCreditApplied(),
                                order.getCreditCurrency(), order.getCurrency());
                due = due.subtract(creditInOrderCcy).max(BigDecimal.ZERO);
            }
            BigDecimal dueInTxCcy = order.getCurrency() != null
                    && order.getCurrency().equalsIgnoreCase(tx.getCurrency())
                    ? due
                    : currencyExchangeService.convert(due, order.getCurrency(), tx.getCurrency());
            BigDecimal paid = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount();
            return paid.compareTo(dueInTxCcy.multiply(new BigDecimal("0.99"))) >= 0;
        } catch (Exception e) {
            log.error("[ORDER] Amount reconciliation failed for order {} / tx {}: {} — allowing payment.",
                    order.getId(), tx.getId(), e.getMessage());
            return true;
        }
    }

    /**
     * Listens for PaymentFailedEvent published by PaymentService.
     * Transitions the matching order (if any) to FAILED status. The cart is
     * left untouched so the customer can retry payment without losing items.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("[ORDER] PaymentFailedEvent received: transactionId={} reason={}",
                event.getTransactionId(), event.getReason());

        PaymentTransaction tx = paymentTransactionRepo.findById(event.getTransactionId())
                .orElse(null);
        if (tx == null) {
            log.warn("[ORDER] No PaymentTransaction found for id={}", event.getTransactionId());
            return;
        }

        if (tx.getAppOrderId() == null) {
            log.info("[ORDER] PaymentFailedEvent has no appOrderId — cart-first flow, no order to update.");
            return;
        }

        orderRepo.findById(tx.getAppOrderId()).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                    || order.getStatus() == OrderStatus.PROCESSING) {
                order.setStatus(OrderStatus.FAILED);
                order.setPaymentTransactionId(event.getTransactionId());
                String reason = event.getReason() != null ? event.getReason() : "Payment failed";
                appendTrackingEvent(order, OrderStatus.FAILED, reason,
                        null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                orderRepo.save(order);
                log.info("[ORDER] Order {} transitioned to FAILED.", order.getId());
            }
        });
    }

    /**
     * Safely clears cart items and their specs, then marks the cart as ABANDONED.
     * Uses robust JPQL queries and check-then-delete to avoid StaleStateException.
     */
    private void clearCartItemsSafely(UUID cartId) {
        cartRepo.findById(cartId).ifPresent(cart -> {
            if (cart.getStatus() == Cart.CartStatus.ABANDONED) {
                log.debug("[ORDER] Cart {} already abandoned. Skipping deletion.", cartId);
                return;
            }

            log.info("[ORDER] Safely clearing cart items for cartId={}", cartId);
            
            // Delete specs first (child rows) using robust JPQL
            cartItemSpecSelectionRepo.deleteByCartId(cartId);
            
            // Delete items using robust JPQL (bypasses entity lifecycle check)
            cartItemRepo.deleteByCartId(cartId);
            
            // Update cart status and total
            cart.setStatus(Cart.CartStatus.ABANDONED);
            cart.setTotalPrice(BigDecimal.ZERO);
            cartRepo.saveAndFlush(cart);
            
            log.info("[ORDER] Cart {} cleared successfully.", cartId);
        });
    }

    @SuppressWarnings("unused") // kept for future re-enable of courier-backend integration
    private void pushToCourier(Order order) {
        if (order == null) return;
        
        CourierOrderRequest courierReq = new CourierOrderRequest();
        courierReq.setOrderId(order.getId());
        courierReq.setCustomerId(order.getUserId());
        courierReq.setRecipientFirstName(order.getRecipientFirstName());
        courierReq.setRecipientLastName(order.getRecipientLastName());
        courierReq.setRecipientPhone(order.getRecipientPhone());
        courierReq.setAddressLine1(order.getAddressLine1());
        courierReq.setAddressLine2(order.getAddressLine2());
        courierReq.setCity(order.getCity());
        courierReq.setCountry(order.getCountry());
        courierReq.setDeliveryLatitude(order.getDeliveryLatitude());
        courierReq.setDeliveryLongitude(order.getDeliveryLongitude());
        courierReq.setTotalAmount(order.getTotalAmount());
        courierReq.setShippingFee(order.getShippingFee());
        courierReq.setCurrency(order.getCurrency());
        
        // Use the first item's storeId — EXPRESS orders come from one store
        order.getItems().stream()
                .filter(i -> i.getStoreId() != null)
                .findFirst()
                .ifPresent(i -> courierReq.setStoreId(i.getStoreId()));
        
        log.info("[ORDER] Pushing order {} to courier backend.", order.getId());
        courierServiceClient.pushOrder(courierReq);
    }

    /**
     * Returns EXPRESS if every cart item's store has an active location within
     * the 30-minute delivery radius of the given address, otherwise REGULAR.
     * Also validates that all items are in the same country as the delivery address.
     */
    private DeliveryMethod resolveDeliveryMethod(List<CartItem> cartItems, UserAddress address) {
        if (address.getLatitude() == null || address.getLongitude() == null) {
            return DeliveryMethod.REGULAR;
        }

        String deliveryCountry = address.getCountry();
        boolean allMatchCountry = cartItems.stream()
                .allMatch(item -> storeProductRepo.findByStore_IdAndProduct_IdAndIsActiveTrue(item.getStoreId(), item.getProduct().getId())
                        .map(sp -> sp.getStore().getCountry().getCode().equalsIgnoreCase(deliveryCountry))
                        .orElse(false));

        if (!allMatchCountry) {
            throw new IllegalArgumentException("All products must be from the same country as the delivery address.");
        }

        List<UUID> expressStoreIds = storeLocationRepo.findStoreIdsWithinRadius(
                address.getLatitude(), address.getLongitude(), THIRTY_MIN_RADIUS_KM);
        Set<UUID> expressSet = new HashSet<>(expressStoreIds);
        boolean allLocal = !cartItems.isEmpty() && cartItems.stream()
                .allMatch(item -> item.getStoreId() != null && expressSet.contains(item.getStoreId()));
        return allLocal ? DeliveryMethod.EXPRESS : DeliveryMethod.REGULAR;
    }

    private BigDecimal calculateShippingFee(DeliveryMethod method, BigDecimal subtotal, String currency) {
        if (method == DeliveryMethod.REGULAR) {
            return BigDecimal.ZERO;
        }
        // Free express delivery once subtotal (in AED equivalent) reaches the threshold;
        // otherwise charge 15 AED converted to the cart's display currency.
        BigDecimal subtotalAed = BASE_CURRENCY.equalsIgnoreCase(currency)
                ? subtotal
                : currencyExchangeService.convert(subtotal, currency, BASE_CURRENCY);
        if (subtotalAed.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return currencyExchangeService.convert(EXPRESS_DELIVERY_FEE, BASE_CURRENCY, currency);
    }

    private String estimateDeliveryTime(DeliveryMethod method) {
        if (method == DeliveryMethod.EXPRESS) {
            return "Within 30 minutes";
        } else {
            return "2-3 business days"; // Placeholder for regular order estimate
        }
    }

    // =========================================================================
    // Customer queries
    // =========================================================================

    public OrderResponse getOrderForCustomer(UUID orderId, UUID userId) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toOrderResponse(order);
    }

    public Page<OrderSummaryResponse> listCustomerOrders(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepo.findAllByUserId(userId, pageable).map(this::toSummaryResponse);
    }

    // =========================================================================
    // Admin queries & management
    // =========================================================================

    public OrderResponse getOrderForAdmin(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toOrderResponse(order);
    }

    public OrderAdminResponse getOrderWithProofForAdmin(UUID orderId, UUID adminUserId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderAdminResponse res = toAdminOrderResponse(order);

        // Prefer admin-uploaded proof images (new flow). Fall back to courier-backend
        // proof only for legacy orders that have a deliveryOrderId set.
        if (order.getPickupProofImageKey() != null) {
            res.setPickupProofImageUrl(contaboObjectService.getPresignedUrl(order.getPickupProofImageKey()));
            res.setPickupProofTakenAt(order.getPickupProofTakenAt());
        }
        if (order.getDropoffProofImageKey() != null) {
            res.setDeliveryProofImageUrl(contaboObjectService.getPresignedUrl(order.getDropoffProofImageKey()));
            res.setDeliveryProofTakenAt(order.getDropoffProofTakenAt());
        }

        boolean adminProofPresent = order.getPickupProofImageKey() != null || order.getDropoffProofImageKey() != null;

        if (!adminProofPresent && order.getDeliveryMethod() == DeliveryMethod.EXPRESS && order.getDeliveryOrderId() != null) {
            try {
                org.springframework.http.ResponseEntity<String> proofRes =
                        courierServiceClient.getDeliveryProof(order.getDeliveryOrderId(), adminUserId.toString());

                if (proofRes.getStatusCode().is2xxSuccessful() && proofRes.getBody() != null) {
                    JsonNode proofJson = objectMapper.readTree(proofRes.getBody());
                    res.setPickupProofImageUrl(contaboObjectService.getPresignedUrl(proofJson.path("pickupImageUrl").asText(null)));
                    if (proofJson.has("pickupPhotoTakenAt")) {
                        res.setPickupProofTakenAt(Instant.parse(proofJson.get("pickupPhotoTakenAt").asText()));
                    }
                    res.setDeliveryProofImageUrl(contaboObjectService.getPresignedUrl(proofJson.path("imageUrl").asText(null)));
                    res.setDeliveryProofSignatureUrl(contaboObjectService.getPresignedUrl(proofJson.path("signatureUrl").asText(null)));
                    res.setDeliveredTo(proofJson.path("deliveredTo").asText(null));
                    if (proofJson.has("photoTakenAt")) {
                        res.setDeliveryProofTakenAt(Instant.parse(proofJson.get("photoTakenAt").asText()));
                    }
                }
            } catch (Exception e) {
                log.warn("[ORDER-ADMIN] Failed to fetch delivery proof for order {}: {}", orderId, e.getMessage());
                // Don't fail the whole request if proof fetch fails
            }
        }

        return res;
    }

    public Page<OrderSummaryResponse> listAllOrders(OrderStatus status, DeliveryMethod deliveryMethod,
                                                     UUID storeId, UUID supplierId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepo.findAllWithFilters(status, deliveryMethod, storeId, supplierId, pageable)
                .map(this::toSummaryResponse);
    }

    /** Orders containing the given supplier's items — for the supplier portal orders view. */
    public Page<OrderSummaryResponse> listOrdersForSupplier(UUID supplierId, OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepo.findBySupplierId(supplierId, status, pageable)
                .map(this::toSummaryResponse);
    }

    /**
     * Admin status update — enforces the state machine and applies side effects
     * (milestone timestamps, courier assignment, carrier info via AdminTrackingUpdateRequest).
     */
    @Transactional
    public OrderResponse adminUpdateStatus(UUID orderId, UUID adminUserId, AdminStatusUpdateRequest req) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateTransition(order.getStatus(), req.getStatus());

        // Assign courier when moving to COURIER_ASSIGNED (legacy flow)
        if (req.getStatus() == OrderStatus.COURIER_ASSIGNED && req.getCourierUserId() != null) {
            order.setCourierUserId(req.getCourierUserId());
        }

        // Capture cancellation reason from admin/customer
        if (req.getStatus() == OrderStatus.CANCELLED && req.getCancellationReason() != null) {
            order.setCancellationReason(req.getCancellationReason());
        }

        applyMilestoneTimestamp(order, req.getStatus());
        order.setStatus(req.getStatus());

        appendTrackingEvent(order, req.getStatus(), req.getNotes(),
                null, null, null, adminUserId, "ADMIN");

        Order saved = orderRepo.save(order);
        broadcastStatusUpdate(saved, null);
        notifyCustomerStatus(saved);

        // Admin cancelled → auto-refund + customer emails (same as a customer cancellation).
        if (saved.getStatus() == OrderStatus.CANCELLED) {
            handleCancellationSideEffects(saved, req.getCancellationReason());
        }

        return toOrderResponse(saved);
    }

    @SuppressWarnings("unused") // kept for future re-enable of courier-backend integration
    private void publishOrderCancelledEvent(UUID orderId, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent(orderId, reason, Instant.now());
        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .exchange(DeliveryRabbitMQConfig.ECOMMERCE_EXCHANGE)
                    .routingKey(DeliveryRabbitMQConfig.ORDER_DELIVERY_CANCELLED_KEY)
                    .payload(objectMapper.writeValueAsString(event))
                    .eventVersion(1)
                    .build());
            log.info("[ORDER] Cancellation queued in outbox for courier — orderId={}", orderId);
        } catch (JsonProcessingException e) {
            log.error("[ORDER] Failed to serialize OrderCancelledEvent for orderId={}: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * Admin tracking update — sets carrier details for regular shipments
     * and adds a tracking history entry.
     */
    @Transactional
    public OrderResponse adminAddTracking(UUID orderId, UUID adminUserId, AdminTrackingUpdateRequest req) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateTransition(order.getStatus(), req.getStatus());

        // Require tracking code when shipping
        if (req.getStatus() == OrderStatus.SHIPPED
                && order.getDeliveryMethod() == DeliveryMethod.REGULAR
                && (req.getTrackingCode() == null || req.getTrackingCode().isBlank())) {
            throw new IllegalArgumentException(
                    "trackingCode is required when marking a REGULAR as SHIPPED");
        }

        if (req.getTrackingCode() != null) order.setTrackingCode(req.getTrackingCode());
        if (req.getCarrierName() != null)  order.setCarrierName(req.getCarrierName());

        applyMilestoneTimestamp(order, req.getStatus());
        order.setStatus(req.getStatus());

        appendTrackingEvent(order, req.getStatus(), req.getNotes(),
                null, null, req.getLocationDescription(), adminUserId, "ADMIN");

        Order saved = orderRepo.save(order);
        broadcastStatusUpdate(saved, null);
        return toOrderResponse(saved);
    }

    // =========================================================================
    // Admin proof uploads (pickup / dropoff photos)
    // =========================================================================

    public enum ProofType { PICKUP, DROPOFF }

    /**
     * Stores a pickup or drop-off proof photo for an order. Admin uploads only.
     * Returns the updated admin response with a fresh presigned URL.
     */
    @Transactional
    public OrderAdminResponse adminUploadProof(UUID orderId, UUID adminUserId,
                                                ProofType type,
                                                org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Proof image file is required");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        String objectKey = String.format("orders/%s/%s-%s",
                order.getId(),
                type.name().toLowerCase(),
                UUID.randomUUID());
        contaboObjectService.uploadFile(objectKey, file);

        Instant now = Instant.now();
        if (type == ProofType.PICKUP) {
            order.setPickupProofImageKey(objectKey);
            order.setPickupProofTakenAt(now);
        } else {
            order.setDropoffProofImageKey(objectKey);
            order.setDropoffProofTakenAt(now);
        }

        appendTrackingEvent(order, order.getStatus(),
                type == ProofType.PICKUP ? "Pickup proof uploaded" : "Drop-off proof uploaded",
                null, null, null, objectKey, adminUserId, "ADMIN");

        Order saved = orderRepo.save(order);
        return getOrderWithProofForAdmin(saved.getId(), adminUserId);
    }

    // =========================================================================
    // Customer order cancellation
    // =========================================================================

    /**
     * Allows a customer to cancel their own order while it is still cancellable.
     */
    @Transactional
    public OrderResponse customerCancelOrder(UUID orderId, UUID userId, String reason) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Customers may cancel only up to (and including) IN_COURIER. Once the order is
        // IN_TRANSIT ("courier on the way to delivery") or later, it can no longer be cancelled.
        if (!isCustomerCancellable(order.getStatus())) {
            throw new IllegalStateException(
                    "This order can no longer be cancelled — it is already on its way to you.");
        }
        validateTransition(order.getStatus(), OrderStatus.CANCELLED);

        order.setCancellationReason(reason);
        applyMilestoneTimestamp(order, OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        appendTrackingEvent(order, OrderStatus.CANCELLED,
                reason != null ? reason : "Cancelled by customer",
                null, null, null, userId, "CUSTOMER");

        Order saved = orderRepo.save(order);
        broadcastStatusUpdate(saved, null);
        handleCancellationSideEffects(saved, reason);
        return toOrderResponse(saved);
    }

    /** Statuses a customer is still allowed to cancel from (before the order leaves for delivery). */
    @SuppressWarnings("deprecation") // legacy statuses kept for historical orders
    private boolean isCustomerCancellable(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT, PAID, PACKAGING, IN_COURIER,
                 PROCESSING, COURIER_ASSIGNED, PICKED_UP -> true;
            default -> false; // IN_TRANSIT, SHIPPED, DELIVERED, CANCELLED, FAILED
        };
    }

    /** Records the order's promo redemption — called once the order becomes PAID. */
    private void recordPromoUsageOnPaid(Order order) {
        if (order.getPromoCodeId() == null) return;
        try {
            promoCodeService.recordUsage(order.getPromoCodeId(), order.getId(),
                    order.getUserId(), order.getDiscount());
        } catch (Exception e) {
            log.warn("[ORDER] Failed to record promo usage for paid order {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * On cancellation of a paid order: auto-initiate a refund of the charged amount and email
     * the customer (cancellation + refund). Best-effort — failures are logged, never block the
     * cancellation. No-op for unpaid (PENDING_PAYMENT) orders.
     */
    private void handleCancellationSideEffects(Order order, String reason) {
        String email = customerEmail(order);
        String name = customerName(order);
        String orderNo = order.getId().toString();

        boolean refunded = false;
        BigDecimal refundAmount = null;
        String refundCurrency = null;

        if (order.getPaymentTransactionId() != null) {
            try {
                PaymentTransaction tx = paymentTransactionRepo.findById(order.getPaymentTransactionId()).orElse(null);
                if (tx != null && (tx.getStatus() == com.buyology.ecommerce.payment.enums.PaymentStatus.SUCCESS
                        || tx.getStatus() == com.buyology.ecommerce.payment.enums.PaymentStatus.PARTIALLY_REFUNDED)) {
                    var req = new com.buyology.ecommerce.payment.dto.RefundRequest();
                    req.setTransactionId(tx.getId());
                    req.setAmount(tx.getAmount());
                    req.setReason("Order " + order.getId() + " cancelled");
                    paymentServiceProvider.getObject().initiateRefund(req);
                    refunded = true;
                    refundAmount = tx.getAmount();
                    refundCurrency = tx.getCurrency();
                    log.info("[ORDER] Auto-refund initiated for cancelled order {}", order.getId());
                }
            } catch (Exception e) {
                log.warn("[ORDER] Auto-refund on cancel failed for order {} — needs manual refund: {}",
                        order.getId(), e.getMessage());
            }
        }

        if (email != null) {
            try { emailService.sendOrderCancelledEmail(email, name, orderNo, reason); }
            catch (Exception e) { log.warn("[ORDER] order-cancelled email failed for {}: {}", order.getId(), e.getMessage()); }
            if (refunded && refundAmount != null) {
                try { emailService.sendRefundInitiatedOnCancelEmail(email, name, orderNo, refundAmount.toPlainString(), refundCurrency); }
                catch (Exception e) { log.warn("[ORDER] refund-initiated email failed for {}: {}", order.getId(), e.getMessage()); }
            }
        }
    }

    private String customerEmail(Order order) {
        if (order.getUserId() == null) return null;
        return authCredentialRepository.findByUserId(order.getUserId()).stream()
                .map(com.buyology.ecommerce.auth.domain.AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst().orElse(null);
    }

    private String customerName(Order order) {
        // First name lives on the Users entity (not UserProfiles); the email greets generically
        // when absent, so we keep this dependency-free and return null.
        return null;
    }

    // =========================================================================
    // Courier operations
    // =========================================================================

    public List<OrderSummaryResponse> listCourierOrders(UUID courierUserId) {
        return orderRepo.findAllByCourierUserIdAndDeliveryMethod(courierUserId, DeliveryMethod.EXPRESS)
                .stream().map(this::toSummaryResponse).toList();
    }

    /**
     * Courier tracking update — only permitted for EXPRESS orders assigned to the caller.
     * Allowed target statuses: PICKED_UP, IN_TRANSIT, DELIVERED, FAILED.
     */
    @Transactional
    public OrderResponse courierUpdateTracking(UUID orderId, UUID courierUserId,
                                                CourierTrackingUpdateRequest req) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getDeliveryMethod() != DeliveryMethod.EXPRESS) {
            throw new IllegalStateException("Courier tracking is only available for EXPRESS orders");
        }

        if (!courierUserId.equals(order.getCourierUserId())) {
            throw new IllegalStateException("You are not the assigned courier for this order");
        }

        OrderStatus target = req.getStatus();
        if (target != OrderStatus.PICKED_UP
                && target != OrderStatus.IN_TRANSIT
                && target != OrderStatus.DELIVERED
                && target != OrderStatus.FAILED) {
            throw new IllegalArgumentException(
                    "Couriers may only set status to PICKED_UP, IN_TRANSIT, DELIVERED, or FAILED");
        }

        validateTransition(order.getStatus(), target);

        applyMilestoneTimestamp(order, target);
        order.setStatus(target);

        appendTrackingEvent(order, target, req.getNotes(),
                req.getLatitude(), req.getLongitude(),
                req.getLocationDescription(), req.getProofImageUrl(), courierUserId, "COURIER");

        Order saved = orderRepo.save(order);
        broadcastStatusUpdate(saved, req.getProofImageUrl());
        return toOrderResponse(saved);
    }

    // =========================================================================
    // Courier backend event integration
    // =========================================================================

    /**
     * Called when the courier backend sends a {@code delivery.courier.assigned} event.
     * Stores the delivery order ID (for WebSocket tracking URL) and transitions the
     * ecommerce order to COURIER_ASSIGNED if it is still in PAID status.
     */
    @Transactional
    public void onCourierAssigned(UUID ecommerceOrderId, UUID deliveryId, UUID courierId,
                                   String courierName, String courierPhone) {
        orderRepo.findById(ecommerceOrderId).ifPresent(order -> {
            order.setDeliveryOrderId(deliveryId);
            order.setCourierUserId(courierId);
            order.setCourierName(courierName);
            order.setCourierPhone(courierPhone);
            if (order.getStatus() == OrderStatus.PAID) {
                order.setStatus(OrderStatus.COURIER_ASSIGNED);
                appendTrackingEvent(order, OrderStatus.COURIER_ASSIGNED,
                        "Courier assigned by delivery service",
                        null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
            }
            Order saved = orderRepo.save(order);
            broadcastStatusUpdate(saved, null);
            log.info("[ORDER] Courier assigned: orderId={} courierId={} name='{}'",
                    ecommerceOrderId, courierId, courierName);
        });
    }

    /**
     * Maps a courier-backend {@link DeliveryStatus} name to the corresponding
     * ecommerce {@link OrderStatus} and persists it. No-op for unknown statuses
     * or orders already in a terminal state.
     */
    @SuppressWarnings("deprecation") // legacy statuses kept for historical orders
    @Transactional
    public void syncStatusFromCourier(UUID ecommerceOrderId, String deliveryStatus, String proofImageUrl) {
        log.info("[ORDER-DEBUG] syncStatusFromCourier start: orderId={} status={} proof={}", 
                ecommerceOrderId, deliveryStatus, proofImageUrl);
        OrderStatus target = switch (deliveryStatus) {
            case "COURIER_ASSIGNED",
                 "COURIER_ACCEPTED"       -> OrderStatus.COURIER_ASSIGNED;
            case "PICKED_UP"              -> OrderStatus.PICKED_UP;
            case "ON_THE_WAY"             -> OrderStatus.IN_TRANSIT;
            case "ARRIVED_AT_DESTINATION" -> OrderStatus.IN_TRANSIT;
            case "DELIVERED"              -> OrderStatus.DELIVERED;
            case "FAILED"                 -> OrderStatus.FAILED;
            case "CANCELLED"              -> OrderStatus.CANCELLED;
            default                       -> null;
        };

        if (target == null) return;

        orderRepo.findById(ecommerceOrderId).ifPresent(order -> {
            // Skip if already at target or in a terminal state to avoid duplicate events
            if (order.getStatus() == target) return;
            if (order.getStatus() == OrderStatus.DELIVERED
                    || order.getStatus() == OrderStatus.CANCELLED
                    || order.getStatus() == OrderStatus.FAILED) return;

            // Forward-only guard: courier events must not move the order backwards
            // (e.g. an out-of-order ON_THE_WAY arriving before PICKED_UP). FAILED /
            // CANCELLED are always accepted as terminal courier outcomes.
            boolean terminalOutcome = target == OrderStatus.FAILED || target == OrderStatus.CANCELLED;
            if (!terminalOutcome && courierStatusRank(target) <= courierStatusRank(order.getStatus())) {
                log.warn("[ORDER] Ignoring out-of-order courier status for orderId={}: {} -> {}",
                        ecommerceOrderId, order.getStatus(), target);
                return;
            }

            applyMilestoneTimestamp(order, target);
            order.setStatus(target);
            appendTrackingEvent(order, target, "Synced from courier backend",
                    null, null, null, proofImageUrl, SYSTEM_ACTOR_ID, "SYSTEM");
            orderRepo.save(order);
            log.info("[ORDER] Status synced from courier: orderId={} status={}",
                    ecommerceOrderId, target);
        });
    }

    /**
     * Broadcasts a real-time status update to the customer via WebSocket.
     * Topic: /topic/orders/{orderId}/status
     */
    public void broadcastStatusUpdate(Order order, String proofImageUrl) {
        String destination = "/topic/orders/" + order.getId() + "/status";
        String presignedUrl = contaboObjectService.getPresignedUrl(proofImageUrl);
        java.util.Map<String, Object> payload = java.util.Map.of(
                "orderId", order.getId().toString(),
                "status",  order.getStatus().name(),
                "proofImageUrl", presignedUrl != null ? presignedUrl : "",
                "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend((String) destination, (Object) payload);
        log.debug("[ORDER-WS] Status broadcast orderId={} status={}", order.getId(), order.getStatus());
    }

    // =========================================================================
    // #1 — Notifications, supplier status updates, store-courier assignment
    // =========================================================================

    /** Notify suppliers whose products are in the order, plus all superadmins. */
    private void notifyNewOrder(Order order) {
        try {
            java.util.Map<String, String> data = java.util.Map.of(
                    "orderId", order.getId().toString(), "type", "NEW_ORDER");
            order.getItems().stream()
                    .map(OrderItem::getSupplierId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .forEach(supplierId -> supplierRepository.findById(supplierId).ifPresent(sup -> {
                        if (sup.getUserId() != null) {
                            pushService.sendToUser(sup.getUserId(), "New order received",
                                    "You have a new order to fulfil.", "NEW_ORDER", data);
                        }
                    }));
            userRoleRepository.findUserIdsByRoleName("SUPERADMIN").forEach(uid ->
                    pushService.sendToUser(uid, "New order placed",
                            "A new order has been placed.", "NEW_ORDER", data));
        } catch (Exception e) {
            log.warn("[ORDER] Failed to send new-order notifications for {}: {}", order.getId(), e.getMessage());
        }
    }

    /** Notify the customer that their order's status changed (in-app feed + push). */
    private void notifyCustomerStatus(Order order) {
        try {
            if (order.getUserId() == null) return;
            pushService.sendToUser(order.getUserId(), "Order update",
                    "Your order is now " + order.getStatus().name().replace('_', ' ').toLowerCase() + ".",
                    "ORDER_STATUS",
                    java.util.Map.of("orderId", order.getId().toString(), "type", "ORDER_STATUS"));
        } catch (Exception e) {
            log.warn("[ORDER] Failed to notify customer of status for {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Supplier advances the status of an order containing their products. Suppliers
     * own fulfilment for their items but cannot cancel/fail orders. Same transition
     * rules as the admin flow (PACKAGING → IN_COURIER → IN_TRANSIT → DELIVERED).
     */
    @Transactional
    public OrderResponse supplierUpdateStatus(UUID orderId, UUID supplierUserId,
                                              OrderStatus newStatus, String notes) {
        com.buyology.ecommerce.supplier.domain.Supplier supplier =
                supplierRepository.findByUserId(supplierUserId).orElse(null);
        if (supplier == null) {
            throw new org.springframework.security.access.AccessDeniedException("Supplier account not found");
        }
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean owns = order.getItems().stream()
                .anyMatch(i -> supplier.getId().equals(i.getSupplierId()));
        if (!owns) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This order does not contain your products");
        }
        if (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.FAILED) {
            throw new IllegalArgumentException("Suppliers cannot cancel or fail orders");
        }
        validateTransition(order.getStatus(), newStatus);
        applyMilestoneTimestamp(order, newStatus);
        order.setStatus(newStatus);
        appendTrackingEvent(order, newStatus, notes, null, null, null, supplier.getId(), "SUPPLIER");
        Order saved = orderRepo.save(order);
        broadcastStatusUpdate(saved, null);
        notifyCustomerStatus(saved);
        return toOrderResponse(saved);
    }

    /**
     * Assigns one of the order's store's courier profiles to the order, stamping the
     * existing courier_* columns. Validates the courier belongs to the order's store.
     */
    @Transactional
    public OrderResponse assignStoreCourier(UUID orderId, UUID adminId, UUID courierProfileId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        com.buyology.ecommerce.courier.profile.domain.CourierProfile cp =
                courierProfileRepository.findById(courierProfileId)
                        .orElseThrow(() -> new IllegalArgumentException("Courier not found"));
        UUID orderStoreId = (order.getItems() == null || order.getItems().isEmpty())
                ? null : order.getItems().get(0).getStoreId();
        if (orderStoreId != null && cp.getStoreId() != null && !cp.getStoreId().equals(orderStoreId)) {
            throw new IllegalArgumentException("Courier does not belong to this order's store");
        }
        order.setCourierUserId(cp.getId());
        order.setCourierName((safe(cp.getFirstName()) + " " + safe(cp.getLastName())).trim());
        order.setCourierPhone(cp.getPhone());
        appendTrackingEvent(order, order.getStatus(),
                "Courier assigned: " + order.getCourierName(), null, null, null, adminId, "ADMIN");
        Order saved = orderRepo.save(order);
        broadcastStatusUpdate(saved, null);
        notifyCustomerStatus(saved);
        return toOrderResponse(saved);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * Returns the {@code userId} of the customer who placed the given order,
     * used by the event consumer to target push notifications.
     */
    public java.util.Optional<UUID> findUserIdByOrderId(UUID orderId) {
        return orderRepo.findById(orderId).map(Order::getUserId);
    }

    /**
     * Safety net for dropped/lost payment webhooks. An order can be left in
     * PENDING_PAYMENT if the success path never ran (e.g. the app crashed between the
     * payment transaction committing as SUCCESS and the order transition, or the
     * AFTER_COMMIT event was lost). This job finds such orders whose transaction is
     * actually SUCCESS and completes them. It is PROMOTE-ONLY — it never cancels or
     * fails an order — so it can never void or mis-handle a real payment. Runs every 5 min.
     */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${order.reconciliation-interval-ms:300000}")
    @Transactional
    public void reconcileStuckPayments() {
        List<Order> stuck = orderRepo.findAllByStatus(
                OrderStatus.PENDING_PAYMENT, PageRequest.of(0, 50)).getContent();
        if (stuck.isEmpty()) return;

        Instant cutoff = Instant.now().minus(java.time.Duration.ofMinutes(15));
        int promoted = 0;
        for (Order order : stuck) {
            // Only act on orders that have had time to receive their webhook.
            if (order.getCreatedAt() != null && order.getCreatedAt().isAfter(cutoff)) continue;

            PaymentTransaction tx = paymentTransactionRepo
                    .findFirstByAppOrderIdAndStatusIn(order.getId(),
                            List.of(com.buyology.ecommerce.payment.enums.PaymentStatus.SUCCESS))
                    .orElse(null);
            if (tx == null) continue;                       // no successful payment — leave as-is
            if (!isPaidAmountSufficient(order, tx)) continue; // underpaid — leave for manual review

            order.setStatus(OrderStatus.PAID);
            order.setPaymentTransactionId(tx.getId());
            order.setPaidAt(Instant.now());
            appendTrackingEvent(order, OrderStatus.PAID, "Payment reconciled (webhook recovery)",
                    null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
            orderRepo.save(order);
            if (order.getCartId() != null) clearCartItemsSafely(order.getCartId());
            promoted++;
        }
        if (promoted > 0) {
            log.warn("[RECONCILE] Promoted {} stuck PENDING_PAYMENT order(s) to PAID via webhook recovery", promoted);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Monotonic rank of the delivery-progress statuses a courier backend can report,
     * used to reject backward / out-of-order courier syncs. Statuses outside the
     * courier progression rank -1 so any real courier milestone moves forward from them.
     */
    @SuppressWarnings("deprecation") // legacy courier statuses kept for historical orders
    private static int courierStatusRank(OrderStatus s) {
        return switch (s) {
            case PAID                          -> 0;
            case COURIER_ASSIGNED              -> 1;
            case PICKED_UP, IN_COURIER         -> 2;
            case IN_TRANSIT                    -> 3;
            case DELIVERED                     -> 4;
            default                            -> -1;
        };
    }

    /**
     * Validates that the requested status transition is allowed.
     * Throws IllegalStateException for illegal transitions (→ HTTP 409 via GlobalExceptionHandler).
     */
    @SuppressWarnings("deprecation") // legacy statuses kept for historical orders
    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean allowed = switch (current) {
            // ── New admin-managed flow ────────────────────────────────────────
            case PENDING_PAYMENT  -> next == OrderStatus.PAID || next == OrderStatus.CANCELLED;
            case PAID             -> next == OrderStatus.PACKAGING || next == OrderStatus.CANCELLED;
            case PACKAGING        -> next == OrderStatus.IN_COURIER || next == OrderStatus.CANCELLED;
            case IN_COURIER       -> next == OrderStatus.IN_TRANSIT || next == OrderStatus.CANCELLED || next == OrderStatus.FAILED;
            case IN_TRANSIT       -> next == OrderStatus.DELIVERED || next == OrderStatus.FAILED || next == OrderStatus.CANCELLED;
            // ── Legacy flow (historical orders only) ──────────────────────────
            case PROCESSING       -> next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED;
            case COURIER_ASSIGNED -> next == OrderStatus.PICKED_UP || next == OrderStatus.CANCELLED;
            case PICKED_UP        -> next == OrderStatus.IN_TRANSIT;
            case SHIPPED          -> next == OrderStatus.IN_TRANSIT;
            default               -> false; // DELIVERED, CANCELLED, FAILED are terminal
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid status transition: " + current + " → " + next);
        }
    }

    @SuppressWarnings("deprecation") // SHIPPED kept for legacy orders
    private void applyMilestoneTimestamp(Order order, OrderStatus next) {
        Instant now = Instant.now();
        switch (next) {
            case PAID       -> order.setPaidAt(now);
            case IN_COURIER -> order.setShippedAt(now);
            case SHIPPED    -> order.setShippedAt(now);
            case DELIVERED  -> order.setDeliveredAt(now);
            case CANCELLED  -> order.setCancelledAt(now);
            default         -> { /* no milestone for other statuses */ }
        }
    }

    private void appendTrackingEvent(Order order, OrderStatus status, String notes,
                                      Double lat, Double lng, String locationDescription,
                                      UUID actorId, String actorRole) {
        appendTrackingEvent(order, status, notes, lat, lng, locationDescription, null, actorId, actorRole);
    }

    private void appendTrackingEvent(Order order, OrderStatus status, String notes,
                                      Double lat, Double lng, String locationDescription,
                                      String proofImageUrl,
                                      UUID actorId, String actorRole) {
        OrderTrackingEvent event = new OrderTrackingEvent();
        event.setOrder(order);
        event.setStatus(status);
        event.setNotes(notes);
        event.setLatitude(lat);
        event.setLongitude(lng);
        event.setLocationDescription(locationDescription);
        event.setProofImageUrl(proofImageUrl);
        event.setActorId(actorId);
        event.setActorRole(actorRole);
        order.getTrackingHistory().add(event);
    }

    // =========================================================================
    // Mappers
    // =========================================================================

    private OrderResponse toOrderResponse(Order o) {
        OrderResponse res = new OrderResponse();
        res.setId(o.getId());
        res.setUserId(o.getUserId());
        res.setCartId(o.getCartId());
        res.setPaymentTransactionId(o.getPaymentTransactionId());
        res.setCourierUserId(o.getCourierUserId());
        res.setDeliveryOrderId(o.getDeliveryOrderId());
        res.setCourierName(o.getCourierName());
        res.setCourierPhone(o.getCourierPhone());
        res.setDeliveryMethod(o.getDeliveryMethod());
        res.setStatus(o.getStatus());
        res.setDeliveryAddressId(o.getDeliveryAddressId());
        res.setRecipientFirstName(o.getRecipientFirstName());
        res.setRecipientLastName(o.getRecipientLastName());
        res.setRecipientPhone(o.getRecipientPhone());
        res.setAddressLine1(o.getAddressLine1());
        res.setAddressLine2(o.getAddressLine2());
        res.setCity(o.getCity());
        res.setState(o.getState());
        res.setCountry(o.getCountry());
        res.setPostalCode(o.getPostalCode());
        res.setDeliveryLatitude(o.getDeliveryLatitude());
        res.setDeliveryLongitude(o.getDeliveryLongitude());
        res.setSubtotal(o.getSubtotal());
        res.setShippingFee(o.getShippingFee());
        res.setDiscount(o.getDiscount());
        res.setTotalAmount(o.getTotalAmount());
        res.setCurrency(o.getCurrency());
        res.setCreditApplied(o.getCreditApplied());
        res.setCreditCurrency(o.getCreditCurrency());
        res.setCountryCode(o.getCountryCode());
        res.setCouponCode(o.getCouponCode());
        res.setTrackingCode(o.getTrackingCode());
        res.setCarrierName(o.getCarrierName());
        res.setPaidAt(o.getPaidAt());
        res.setShippedAt(o.getShippedAt());
        res.setDeliveredAt(o.getDeliveredAt());
        res.setCancelledAt(o.getCancelledAt());
        res.setCancellationReason(o.getCancellationReason());
        res.setCreatedAt(o.getCreatedAt());
        res.setUpdatedAt(o.getUpdatedAt());

        // Populate store coordinates from the first item (EXPRESS orders are from one store)
        if (o.getItems() != null && !o.getItems().isEmpty()) {
            UUID storeId = o.getItems().get(0).getStoreId();
            if (storeId != null) {
                storeLocationRepo.findByStoreIdAndIsPrimary(storeId, true).ifPresent(loc -> {
                    res.setStoreLatitude(loc.getLatitude());
                    res.setStoreLongitude(loc.getLongitude());
                });
            }
        }

        res.setItems(o.getItems().stream().map(this::toItemResponse).toList());
        res.setTrackingHistory(o.getTrackingHistory().stream().map(this::toTrackingEventResponse).toList());
        return res;
    }

    private OrderAdminResponse toAdminOrderResponse(Order o) {
        OrderResponse base = toOrderResponse(o);
        OrderAdminResponse res = new OrderAdminResponse();
        // Copy base fields (manual copy or BeanUtils.copyProperties if available)
        res.setId(base.getId());
        res.setUserId(base.getUserId());
        res.setCartId(base.getCartId());
        res.setPaymentTransactionId(base.getPaymentTransactionId());
        res.setCourierUserId(base.getCourierUserId());
        res.setDeliveryOrderId(base.getDeliveryOrderId());
        res.setCourierName(base.getCourierName());
        res.setCourierPhone(base.getCourierPhone());
        res.setDeliveryMethod(base.getDeliveryMethod());
        res.setStatus(base.getStatus());
        res.setDeliveryAddressId(base.getDeliveryAddressId());
        res.setRecipientFirstName(base.getRecipientFirstName());
        res.setRecipientLastName(base.getRecipientLastName());
        res.setRecipientPhone(base.getRecipientPhone());
        res.setAddressLine1(base.getAddressLine1());
        res.setAddressLine2(base.getAddressLine2());
        res.setCity(base.getCity());
        res.setState(base.getState());
        res.setCountry(base.getCountry());
        res.setPostalCode(base.getPostalCode());
        res.setDeliveryLatitude(base.getDeliveryLatitude());
        res.setDeliveryLongitude(base.getDeliveryLongitude());
        res.setSubtotal(base.getSubtotal());
        res.setShippingFee(base.getShippingFee());
        res.setDiscount(base.getDiscount());
        res.setTotalAmount(base.getTotalAmount());
        res.setCurrency(base.getCurrency());
        res.setCountryCode(base.getCountryCode());
        res.setCouponCode(base.getCouponCode());
        res.setEstimatedDeliveryTime(base.getEstimatedDeliveryTime());
        res.setTrackingCode(base.getTrackingCode());
        res.setCarrierName(base.getCarrierName());
        res.setPaidAt(base.getPaidAt());
        res.setShippedAt(base.getShippedAt());
        res.setDeliveredAt(base.getDeliveredAt());
        res.setCancelledAt(base.getCancelledAt());
        res.setCancellationReason(base.getCancellationReason());
        res.setCreatedAt(base.getCreatedAt());
        res.setUpdatedAt(base.getUpdatedAt());
        res.setItems(base.getItems());
        res.setTrackingHistory(base.getTrackingHistory());

        // Set additional admin-only fields
        if (o.getItems() != null && !o.getItems().isEmpty()) {
            res.setStoreId(o.getItems().get(0).getStoreId());
        }

        return res;
    }

    private OrderItemResponse toItemResponse(OrderItem i) {
        OrderItemResponse res = new OrderItemResponse();
        res.setId(i.getId());
        res.setProductId(i.getProductId());
        res.setVariantId(i.getVariantId());
        res.setStoreId(i.getStoreId());
        res.setProductSku(i.getProductSku());
        res.setVariantSku(i.getVariantSku());
        res.setQuantity(i.getQuantity());
        res.setUnitPrice(i.getUnitPrice());
        res.setTotalPrice(i.getTotalPrice());
        res.setCreatedAt(i.getCreatedAt());
        return res;
    }

    private TrackingEventResponse toTrackingEventResponse(OrderTrackingEvent e) {
        TrackingEventResponse res = new TrackingEventResponse();
        res.setId(e.getId());
        res.setStatus(e.getStatus());
        res.setNotes(e.getNotes());
        res.setLatitude(e.getLatitude());
        res.setLongitude(e.getLongitude());
        res.setLocationDescription(e.getLocationDescription());
        res.setProofImageUrl(contaboObjectService.getPresignedUrl(e.getProofImageUrl()));
        res.setActorId(e.getActorId());
        res.setActorRole(e.getActorRole());
        res.setCreatedAt(e.getCreatedAt());
        return res;
    }

    private OrderSummaryResponse toSummaryResponse(Order o) {
        OrderSummaryResponse res = new OrderSummaryResponse();
        res.setId(o.getId());
        res.setUserId(o.getUserId());
        if (o.getItems() != null && !o.getItems().isEmpty()) {
            res.setStoreId(o.getItems().get(0).getStoreId());
        }
        res.setDeliveryMethod(o.getDeliveryMethod());
        res.setStatus(o.getStatus());
        res.setTotalAmount(o.getTotalAmount());
        res.setCurrency(o.getCurrency());
        res.setCountryCode(o.getCountryCode());
        res.setTrackingCode(o.getTrackingCode());
        res.setCarrierName(o.getCarrierName());
        res.setRecipientFirstName(o.getRecipientFirstName());
        res.setRecipientLastName(o.getRecipientLastName());
        res.setCity(o.getCity());
        res.setCountry(o.getCountry());
        res.setPaidAt(o.getPaidAt());
        res.setDeliveredAt(o.getDeliveredAt());
        res.setCreatedAt(o.getCreatedAt());
        res.setUpdatedAt(o.getUpdatedAt());
        return res;
    }

    private boolean isSameCountry(String code1, String code2) {
        return com.buyology.ecommerce.common.utils.CountryCodeUtil.isSameCountry(code1, code2);
    }
}
