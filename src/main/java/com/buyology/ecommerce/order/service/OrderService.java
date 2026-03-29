package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
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
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final OrderRepository orderRepo;
    private final OrderTrackingEventRepository trackingRepo;
    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final UserAddressRepository addressRepo;

    public OrderService(OrderRepository orderRepo,
                        OrderTrackingEventRepository trackingRepo,
                        CartRepository cartRepo,
                        CartItemRepository cartItemRepo,
                        UserAddressRepository addressRepo) {
        this.orderRepo = orderRepo;
        this.trackingRepo = trackingRepo;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.addressRepo = addressRepo;
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

        // Build order
        Order order = new Order();
        order.setUserId(userId);
        order.setAuthCredentialId(authCredentialId);
        order.setCartId(cart.getId());
        order.setDeliveryMethod(req.getDeliveryMethod());
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
        BigDecimal shippingFee = req.getShippingFee() != null ? req.getShippingFee() : BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
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
     * Transitions the matching order from PENDING_PAYMENT to PAID.
     * Silently ignores events for unknown orders (defensive — may arrive before order is saved
     * in edge cases, or for non-order payments).
     */
    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        orderRepo.findById(event.getOrderId()).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.setStatus(OrderStatus.PAID);
                order.setPaymentTransactionId(event.getTransactionId());
                order.setPaidAt(Instant.now());
                appendTrackingEvent(order, OrderStatus.PAID, "Payment confirmed",
                        null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                orderRepo.save(order);
            }
        });
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

        return toOrderResponse(orderRepo.save(order));
    }

    /**
     * Admin tracking update — sets carrier details for international shipments
     * and adds a tracking history entry.
     */
    @Transactional
    public OrderResponse adminAddTracking(UUID orderId, UUID adminUserId, AdminTrackingUpdateRequest req) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateTransition(order.getStatus(), req.getStatus());

        // Require tracking code when shipping internationally
        if (req.getStatus() == OrderStatus.SHIPPED
                && order.getDeliveryMethod() == DeliveryMethod.INTERNATIONAL
                && (req.getTrackingCode() == null || req.getTrackingCode().isBlank())) {
            throw new IllegalArgumentException(
                    "trackingCode is required when marking an INTERNATIONAL order as SHIPPED");
        }

        if (req.getTrackingCode() != null) order.setTrackingCode(req.getTrackingCode());
        if (req.getCarrierName() != null)  order.setCarrierName(req.getCarrierName());

        applyMilestoneTimestamp(order, req.getStatus());
        order.setStatus(req.getStatus());

        appendTrackingEvent(order, req.getStatus(), req.getNotes(),
                null, null, req.getLocationDescription(), adminUserId, "ADMIN");

        return toOrderResponse(orderRepo.save(order));
    }

    // =========================================================================
    // Courier operations
    // =========================================================================

    public List<OrderSummaryResponse> listCourierOrders(UUID courierUserId) {
        return orderRepo.findAllByCourierUserIdAndDeliveryMethod(courierUserId, DeliveryMethod.LOCAL_EXPRESS)
                .stream().map(this::toSummaryResponse).toList();
    }

    /**
     * Courier tracking update — only permitted for LOCAL_EXPRESS orders assigned to the caller.
     * Allowed target statuses: PICKED_UP, IN_TRANSIT, DELIVERED, FAILED.
     */
    @Transactional
    public OrderResponse courierUpdateTracking(UUID orderId, UUID courierUserId,
                                                CourierTrackingUpdateRequest req) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getDeliveryMethod() != DeliveryMethod.LOCAL_EXPRESS) {
            throw new IllegalStateException("Courier tracking is only available for LOCAL_EXPRESS orders");
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
                req.getLocationDescription(), courierUserId, "COURIER");

        return toOrderResponse(orderRepo.save(order));
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
        OrderTrackingEvent event = new OrderTrackingEvent();
        event.setOrder(order);
        event.setStatus(status);
        event.setNotes(notes);
        event.setLatitude(lat);
        event.setLongitude(lng);
        event.setLocationDescription(locationDescription);
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
        res.setTrackingHistory(o.getTrackingHistory().stream().map(this::toTrackingResponse).toList());
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

    private TrackingEventResponse toTrackingResponse(OrderTrackingEvent e) {
        TrackingEventResponse res = new TrackingEventResponse();
        res.setId(e.getId());
        res.setStatus(e.getStatus());
        res.setNotes(e.getNotes());
        res.setLatitude(e.getLatitude());
        res.setLongitude(e.getLongitude());
        res.setLocationDescription(e.getLocationDescription());
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
