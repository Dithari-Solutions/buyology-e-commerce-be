package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.courier.CourierOrderRequest;
import com.buyology.ecommerce.courier.CourierServiceClient;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.OrderTrackingEvent;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.dto.*;
import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
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

    // Delivery Fee Constants (in AED)
    private static final String BASE_CURRENCY = "AED";
    private static final BigDecimal EXPRESS_FEE_LOW_CART = new BigDecimal("15.00");
    private static final BigDecimal EXPRESS_FEE_HIGH_CART = new BigDecimal("10.00");
    private static final BigDecimal CART_TOTAL_THRESHOLD = new BigDecimal("150.00");

    private final OrderRepository orderRepo;
    private final OrderTrackingEventRepository trackingRepo;
    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final CartItemSpecSelectionRepository cartItemSpecSelectionRepo;
    private final UserAddressRepository addressRepo;
    private final PaymentTransactionRepository paymentTransactionRepo;
    private final StoreLocationRepository storeLocationRepo;
    private final StoreProductRepository storeProductRepo;
    private final UserProfilesRepository userProfileRepo;
    private final CurrencyExchangeService currencyExchangeService;
    private final ObjectMapper objectMapper;
    private final CourierServiceClient courierServiceClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ContaboObjectService contaboObjectService;

    public OrderService(OrderRepository orderRepo,
                        OrderTrackingEventRepository trackingRepo,
                        CartRepository cartRepo,
                        CartItemRepository cartItemRepo,
                        CartItemSpecSelectionRepository cartItemSpecSelectionRepo,
                        UserAddressRepository addressRepo,
                        PaymentTransactionRepository paymentTransactionRepo,
                        StoreLocationRepository storeLocationRepo,
                        StoreProductRepository storeProductRepo,
                        UserProfilesRepository userProfileRepo,
                        CurrencyExchangeService currencyExchangeService,
                        ObjectMapper objectMapper,
                        CourierServiceClient courierServiceClient,
                        SimpMessagingTemplate messagingTemplate,
                        ContaboObjectService contaboObjectService) {
        this.orderRepo = orderRepo;
        this.trackingRepo = trackingRepo;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.cartItemSpecSelectionRepo = cartItemSpecSelectionRepo;
        this.addressRepo = addressRepo;
        this.paymentTransactionRepo = paymentTransactionRepo;
        this.storeLocationRepo = storeLocationRepo;
        this.storeProductRepo = storeProductRepo;
        this.userProfileRepo = userProfileRepo;
        this.currencyExchangeService = currencyExchangeService;
        this.objectMapper = objectMapper;
        this.courierServiceClient = courierServiceClient;
        this.messagingTemplate = messagingTemplate;
        this.contaboObjectService = contaboObjectService;
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

        // Validate customer country
        UserProfiles profile = userProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found"));
        if (!address.getCountry().equalsIgnoreCase(profile.getSelectedCountryCode())) {
            throw new IllegalArgumentException("You can only purchase products for delivery in your selected country (" + 
                    profile.getSelectedCountryCode() + ").");
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
        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setTotalAmount(subtotal.add(shippingFee).subtract(discount));
        order.setCurrency(cart.getCurrency());
        order.setCountryCode(cart.getCountryCode());
        order.setCouponCode(req.getCouponCode());

        order = orderRepo.save(order);

        // Convert cart items to order items
        for (CartItem cartItem : cartItems) {
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
            order.getItems().add(item);
        }

        // Initial tracking event
        appendTrackingEvent(order, OrderStatus.PENDING_PAYMENT, "Order created, awaiting payment",
                null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");

        order = orderRepo.save(order);
        return toOrderResponse(order);
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
                    order.setStatus(OrderStatus.PAID);
                    order.setPaymentTransactionId(event.getTransactionId());
                    order.setPaidAt(Instant.now());
                    appendTrackingEvent(order, OrderStatus.PAID, "Payment confirmed",
                            null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                    orderRepo.save(order);

                    // Push EXPRESS orders to courier backend automatically
                    if (order.getDeliveryMethod() == DeliveryMethod.EXPRESS) {
                        pushToCourier(order);
                    }

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

            // Transition immediately to PAID
            Order order = orderRepo.findById(orderResponse.getId()).orElse(null);
            if (order != null) {
                order.setStatus(OrderStatus.PAID);
                order.setPaymentTransactionId(event.getTransactionId());
                order.setPaidAt(Instant.now());
                appendTrackingEvent(order, OrderStatus.PAID, "Payment confirmed",
                        null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                orderRepo.save(order);
            }

            // Back-fill the transaction so future queries can find the order
            tx.setAppOrderId(orderResponse.getId());
            paymentTransactionRepo.save(tx);

            // Push EXPRESS orders to courier backend automatically
            if (order != null && order.getDeliveryMethod() == DeliveryMethod.EXPRESS) {
                pushToCourier(order);
            }

            // Clear cart items and mark cart ABANDONED so the customer can start fresh
            clearCartItemsSafely(cart.getId());
        }
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

        // Convert threshold to cart currency
        BigDecimal threshold = currencyExchangeService.convert(CART_TOTAL_THRESHOLD, BASE_CURRENCY, currency);
        BigDecimal fee;
        if (subtotal.compareTo(threshold) < 0) {
            fee = currencyExchangeService.convert(EXPRESS_FEE_LOW_CART, BASE_CURRENCY, currency);
        } else {
            fee = currencyExchangeService.convert(EXPRESS_FEE_HIGH_CART, BASE_CURRENCY, currency);
        }
        return fee;
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

        // If it's an EXPRESS order with a deliveryOrderId, fetch proof from courier service
        if (order.getDeliveryMethod() == DeliveryMethod.EXPRESS && order.getDeliveryOrderId() != null) {
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
                                                     int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && deliveryMethod != null) {
            return orderRepo.findAllByStatusAndDeliveryMethod(status, deliveryMethod, pageable)
                    .map(this::toSummaryResponse);
        } else if (status != null) {
            return orderRepo.findAllByStatus(status, pageable).map(this::toSummaryResponse);
        } else if (deliveryMethod != null) {
            return orderRepo.findAllByDeliveryMethod(deliveryMethod, pageable).map(this::toSummaryResponse);
        }
        return orderRepo.findAll(pageable).map(this::toSummaryResponse);
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

        // Assign courier when moving to COURIER_ASSIGNED
        if (req.getStatus() == OrderStatus.COURIER_ASSIGNED && req.getCourierUserId() != null) {
            order.setCourierUserId(req.getCourierUserId());
        }

        applyMilestoneTimestamp(order, req.getStatus());
        order.setStatus(req.getStatus());

        appendTrackingEvent(order, req.getStatus(), req.getNotes(),
                null, null, null, adminUserId, "ADMIN");

        Order saved = orderRepo.save(order);
        broadcastStatusUpdate(saved, null);
        return toOrderResponse(saved);
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
    @Transactional
    public void syncStatusFromCourier(UUID ecommerceOrderId, String deliveryStatus, String proofImageUrl) {
        OrderStatus target = switch (deliveryStatus) {
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

    /**
     * Returns the {@code userId} of the customer who placed the given order,
     * used by the event consumer to target push notifications.
     */
    public java.util.Optional<UUID> findUserIdByOrderId(UUID orderId) {
        return orderRepo.findById(orderId).map(Order::getUserId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Validates that the requested status transition is allowed.
     * Throws IllegalStateException for illegal transitions (→ HTTP 409 via GlobalExceptionHandler).
     */
    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean allowed = switch (current) {
            case PENDING_PAYMENT  -> next == OrderStatus.PAID || next == OrderStatus.CANCELLED;
            case PAID             -> next == OrderStatus.PROCESSING
                                     || next == OrderStatus.COURIER_ASSIGNED
                                     || next == OrderStatus.CANCELLED;
            case PROCESSING       -> next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED;
            case COURIER_ASSIGNED -> next == OrderStatus.PICKED_UP || next == OrderStatus.CANCELLED;
            case PICKED_UP        -> next == OrderStatus.IN_TRANSIT;
            case SHIPPED          -> next == OrderStatus.IN_TRANSIT;
            case IN_TRANSIT       -> next == OrderStatus.DELIVERED || next == OrderStatus.FAILED;
            default               -> false; // DELIVERED, CANCELLED, FAILED are terminal
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid status transition: " + current + " → " + next);
        }
    }

    private void applyMilestoneTimestamp(Order order, OrderStatus next) {
        Instant now = Instant.now();
        switch (next) {
            case PAID      -> order.setPaidAt(now);
            case SHIPPED   -> order.setShippedAt(now);
            case DELIVERED -> order.setDeliveredAt(now);
            case CANCELLED -> order.setCancelledAt(now);
            default        -> { /* no milestone for other statuses */ }
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
        res.setCountryCode(o.getCountryCode());
        res.setCouponCode(o.getCouponCode());
        res.setTrackingCode(o.getTrackingCode());
        res.setCarrierName(o.getCarrierName());
        res.setPaidAt(o.getPaidAt());
        res.setShippedAt(o.getShippedAt());
        res.setDeliveredAt(o.getDeliveredAt());
        res.setCancelledAt(o.getCancelledAt());
        res.setCreatedAt(o.getCreatedAt());
        res.setUpdatedAt(o.getUpdatedAt());

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
}
