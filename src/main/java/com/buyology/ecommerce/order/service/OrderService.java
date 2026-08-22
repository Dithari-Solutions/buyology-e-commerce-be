package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductMediaRepository;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreLocation;
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
import com.buyology.ecommerce.user.domain.Users;
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
import com.buyology.ecommerce.order.event.OrderPaidEvent;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final double THIRTY_MIN_RADIUS_KM = com.buyology.ecommerce.store.service.ExpressDeliveryRadius.KM;

    // Delivery pricing lives in DeliveryFeePolicy — the cart reads the same bean, so the fee quoted
    // while shopping cannot drift from the fee charged at checkout.
    private static final String BASE_CURRENCY = "AED";
    private static final String STOREFRONT_URL = "https://buyology.online";

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
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductMediaRepository productMediaRepository;
    private final UserProfilesRepository userProfileRepo;
    private final com.buyology.ecommerce.user.repository.UserRepository userRepo;
    private final com.buyology.ecommerce.user.service.AccountStatusValidator accountStatusValidator;
    private final CurrencyExchangeService currencyExchangeService;
    private final DeliveryFeePolicy deliveryFeePolicy;
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
    private final com.buyology.ecommerce.membership.service.CreditReturnService creditReturnService;
    /**
     * This bean, through its proxy. Needed because {@link #applyCancellationSideEffects} runs after
     * the cancellation has committed and must open a transaction of its own — a plain {@code this.}
     * call would bypass the proxy, and with it the propagation that makes those writes land.
     */
    private final org.springframework.beans.factory.ObjectProvider<OrderService> selfProvider;
    /** Plain injection, NOT selfProvider: this one is meant to join the caller's transaction. */
    private final com.buyology.ecommerce.store.service.StockReservationService stockReservationService;
    private final com.buyology.ecommerce.quiqup.service.QuiqupCancelService quiqupCancelService;
    private final com.buyology.ecommerce.cart.service.CartCheckoutCleanupService cartCheckoutCleanupService;
    private final com.buyology.ecommerce.payment.service.PaymentAnomalyService paymentAnomalyService;
    private final com.buyology.ecommerce.payment.service.PaidAmountPolicy paidAmountPolicy;
    private final com.buyology.ecommerce.payment.repository.PaymentAnomalyRepository paymentAnomalyRepo;
    /** Publishes OrderPaidEvent so downstream integrations (ERPNext) stay decoupled from this service. */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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
                        ProductTranslationRepository productTranslationRepository,
                        ProductMediaRepository productMediaRepository,
                        UserProfilesRepository userProfileRepo,
                        com.buyology.ecommerce.user.repository.UserRepository userRepo,
                        com.buyology.ecommerce.user.service.AccountStatusValidator accountStatusValidator,
                        CurrencyExchangeService currencyExchangeService,
                        DeliveryFeePolicy deliveryFeePolicy,
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
                        org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.payment.service.PaymentService> paymentServiceProvider,
                        com.buyology.ecommerce.membership.service.CreditReturnService creditReturnService,
                        org.springframework.beans.factory.ObjectProvider<OrderService> selfProvider,
                        com.buyology.ecommerce.store.service.StockReservationService stockReservationService,
                        com.buyology.ecommerce.quiqup.service.QuiqupCancelService quiqupCancelService,
                        com.buyology.ecommerce.cart.service.CartCheckoutCleanupService cartCheckoutCleanupService,
                        com.buyology.ecommerce.payment.service.PaymentAnomalyService paymentAnomalyService,
                        com.buyology.ecommerce.payment.service.PaidAmountPolicy paidAmountPolicy,
                        com.buyology.ecommerce.payment.repository.PaymentAnomalyRepository paymentAnomalyRepo,
                        org.springframework.context.ApplicationEventPublisher eventPublisher) {
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
        this.productTranslationRepository = productTranslationRepository;
        this.productMediaRepository = productMediaRepository;
        this.userProfileRepo = userProfileRepo;
        this.userRepo = userRepo;
        this.accountStatusValidator = accountStatusValidator;
        this.currencyExchangeService = currencyExchangeService;
        this.deliveryFeePolicy = deliveryFeePolicy;
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
        this.creditReturnService = creditReturnService;
        this.selfProvider = selfProvider;
        this.stockReservationService = stockReservationService;
        this.quiqupCancelService = quiqupCancelService;
        this.cartCheckoutCleanupService = cartCheckoutCleanupService;
        this.paymentAnomalyService = paymentAnomalyService;
        this.paidAmountPolicy = paidAmountPolicy;
        this.paymentAnomalyRepo = paymentAnomalyRepo;
        this.eventPublisher = eventPublisher;
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
        // Block ordering for accounts pending deletion — they must recover their account first.
        accountStatusValidator.requireActiveAccount(userId);

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

        // Idempotency, in two halves.
        //
        // The PAID half is unconditional and runs before anything else is loaded: a PAID order
        // means this cart's money is already taken, and nothing about coupons, addresses or
        // delivery methods may ever cause a second order — or a second charge — for it. Running it
        // first also keeps it reachable when the profile or promo checks below would throw.
        //
        // The PENDING_PAYMENT half is decided LATER, once the current checkout has been fully
        // resolved — because deciding it on the cart subtotal alone was the bug: a promo, an
        // address change or a switch to pickup leaves the subtotal untouched, and the customer got
        // the old order verbatim. See CheckoutIdentity.
        List<Order> priorOrders = orderRepo.findAllByCartIdAndStatusInOrderByCreatedAtDesc(
                cart.getId(), List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID));
        for (Order prior : priorOrders) {
            if (prior.getStatus() == OrderStatus.PAID) {
                log.info("[ORDER] createOrder: cart {} is already PAID by order {} — returning it "
                        + "(idempotent)", cart.getId(), prior.getId());
                return toOrderResponse(prior);
            }
        }

        // Only the lines the shopper has ticked become the order. Unticked lines stay in the cart
        // untouched: not priced, not stock-decremented, not shipped, not charged. cart.totalPrice
        // is already the selected subtotal (CartService.recalculateCartTotal), so the pricing and
        // the free-shipping threshold below read the same basis as this list.
        List<CartItem> cartItems = cartItemRepo.findByCartIdAndSelectedTrue(cart.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot create an order: no items are selected for checkout");
        }

        // The order is constrained to the user's market: their explicitly selected country,
        // or (if unset) the country the cart was browsed/priced in.
        UserProfiles profile = userProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found"));

        // A verified phone number is required to place an order (delivery / pickup contact).
        if (!profile.isPhoneVerified()) {
            throw new IllegalStateException("Please verify your phone number before placing an order.");
        }

        String marketCountry = profile.getSelectedCountryCode();
        if (marketCountry == null || marketCountry.isBlank()) {
            marketCountry = cart.getCountryCode();
        }

        // Resolve the CURRENT checkout completely — method after the EXPRESS downgrade, the fee
        // that method actually costs, and where the goods go — before deciding anything about
        // prior orders. The reuse decision needs the resolved values, not the request's.
        FulfilmentPlan plan = resolveFulfilment(userId, req, cart, cartItems, profile, marketCountry);

        // What the order will be stamped with; also inputs to the reuse decision.
        String currency = cart.getCurrency();
        if (currency == null || currency.isBlank()) {
            currency = profile.getPreferredCurrency();
        }
        String orderCountryCode = (marketCountry != null && !marketCountry.isBlank())
                ? marketCountry : plan.country();

        // The PENDING_PAYMENT half of the idempotency: reuse a prior order only when EVERY price-
        // and fulfilment-deciding input still matches; supersede the rest. A double-tap matches
        // trivially. A changed checkout supersedes — which returns the stale order's stock (via
        // transitionTo) and its promo reservation before the fresh order asks for both.
        Order reusable = null;
        for (Order prior : priorOrders) {
            if (reusable == null && CheckoutIdentity.isSameCheckout(
                    prior, cart, cartItems, req, plan, currency, orderCountryCode)) {
                reusable = prior;
            } else {
                supersedeStaleOrder(prior, cart.getId());
            }
        }
        if (reusable != null) {
            log.info("[ORDER] createOrder: reusing PENDING_PAYMENT order {} for cart {} — the "
                    + "checkout is identical (idempotent)", reusable.getId(), cart.getId());
            return toOrderResponse(reusable);
        }

        // Build order
        Order order = new Order();
        order.setUserId(userId);
        order.setAuthCredentialId(authCredentialId);
        order.setCartId(cart.getId());
        order.setDeliveryMethod(plan.method());
        order.setShippingFee(plan.shippingFee());
        order.setEstimatedDeliveryTime(plan.estimatedDeliveryTime());
        order.setCountry(plan.country());
        if (plan.method() == DeliveryMethod.PICKUP) {
            order.setPickupStoreId(plan.pickupStoreId());
            order.setPickupStoreName(plan.pickupStoreName());
            order.setPickupStoreAddress(plan.pickupStoreAddress());
            // Pickup contact + market snapshot (no delivery address).
            order.setRecipientFirstName(profile.getUser() != null ? profile.getUser().getFirstName() : null);
            order.setRecipientLastName(profile.getUser() != null ? profile.getUser().getLastName() : null);
            order.setRecipientPhone(profile.getPhoneNumber());
        } else {
            UserAddress address = plan.address();
            // Address snapshot
            order.setDeliveryAddressId(address.getId());
            order.setRecipientFirstName(address.getFirstName());
            order.setRecipientLastName(address.getLastName());
            order.setRecipientPhone(address.getPhoneNumber());
            order.setAddressLine1(address.getAddressLine1());
            order.setAddressLine2(address.getAddressLine2());
            order.setCity(address.getCity());
            order.setState(address.getState());
            order.setPostalCode(address.getPostalCode());
            order.setDeliveryLatitude(address.getLatitude());
            order.setDeliveryLongitude(address.getLongitude());
        }

        transitionTo(order, OrderStatus.PENDING_PAYMENT);

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
        BigDecimal grossTotal = subtotal.add(order.getShippingFee());
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
        
        order.setCurrency(currency);
        order.setCountryCode(orderCountryCode);
        order.setCouponCode(req.getCouponCode());
        order.setPromoCodeId(appliedPromoId);

        order = orderRepo.save(order);

        // Take the code out of circulation now, not when the order is paid.
        //
        // Redemptions used to be recorded only at PAID, and every limit check counted redemptions,
        // so a code sat there looking unused for as long as an order went unpaid. Ten orders could
        // each be created carrying the same single-use code, each pass validation, and each keep
        // its discount once paid. Reserving at checkout closes that: the second order sees the
        // first order's claim. The reservation is released if this order is cancelled or its
        // payment fails, so an unpaid order does not hold a code forever.
        //
        // A refusal here means the code ran out between validating it and claiming it — a genuine
        // race for its last use. The order is still PENDING_PAYMENT and nobody has been charged, so
        // the discount comes back off and the customer sees the real total before paying.
        if (appliedPromoId != null
                && !promoCodeService.reserveUsage(appliedPromoId, order.getId(), userId, discount)) {
            log.warn("[ORDER] Promo {} was exhausted before order {} could claim it — "
                    + "removing the {} discount", appliedPromoId, order.getId(), discount);
            order.setPromoCodeId(null);
            order.setDiscount(BigDecimal.ZERO);
            order.setTotalAmount(grossTotal);
            order = orderRepo.save(order);
        }

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

        // Record that this order is now holding stock. Every line above either decremented the
        // store listing, or soft-decremented the product's display stock, or both — and the loop
        // throws on a failed decrement, so reaching here means they all succeeded. This stamp is
        // what tells StockReservationService there is something to give back, and its absence is
        // what stops B2B quote orders (which never come through here and never decrement) from
        // being credited units they never took.
        order.setStockReservedAt(Instant.now());

        // Initial tracking event
        appendTrackingEvent(order, OrderStatus.PENDING_PAYMENT, "Order created, awaiting payment",
                null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");

        order = orderRepo.save(order);

        // NOTE: promo usage is now recorded on payment success (recordPromoUsageOnPaid),
        // not here — so a code only counts as redeemed once the order is actually paid.

        return toOrderResponse(order);
    }

    /**
     * Everything about a checkout that decides the delivery fee and where the goods go.
     *
     * <p>Package-private so {@link CheckoutIdentity} can compare a prior order against it without
     * reaching into OrderService.
     */
    record FulfilmentPlan(DeliveryMethod method,
                          BigDecimal shippingFee,
                          String estimatedDeliveryTime,
                          UUID pickupStoreId, String pickupStoreName, String pickupStoreAddress,
                          UUID addressId, UserAddress address,
                          String country) {
    }

    /**
     * Resolves the current checkout — validation, the EXPRESS downgrade, the fee — WITHOUT touching
     * an order. Extracted from createOrder so the result exists before the reuse decision: whether
     * a prior order is still this checkout can only be judged against the RESOLVED method and fee,
     * not the raw request.
     */
    private FulfilmentPlan resolveFulfilment(UUID userId, CreateOrderRequest req, Cart cart,
                                             List<CartItem> cartItems, UserProfiles profile,
                                             String marketCountry) {
        if (req.getDeliveryMethod() == DeliveryMethod.PICKUP) {
            // ── Store pickup: customer collects from a chosen branch; no address, no shipping ──
            if (req.getPickupStoreId() == null) {
                throw new IllegalArgumentException("Please choose a store to pick up from.");
            }
            StoreLocation branch = storeLocationRepo
                    .findByStoreIdAndIsPrimary(req.getPickupStoreId(), true)
                    .orElseGet(() -> storeLocationRepo
                            .findAllByStoreIdAndIsActive(req.getPickupStoreId(), true)
                            .stream().findFirst().orElse(null));
            if (branch == null) {
                throw new IllegalArgumentException("The selected store is not available for pickup.");
            }
            Store pickupStore = branch.getStore();
            if (pickupStore == null) {
                throw new IllegalArgumentException("The selected store is not available for pickup.");
            }

            // The customer may only collect from a store in their own market/country.
            String storeCountry = (branch.getCountry() != null && !branch.getCountry().isBlank())
                    ? branch.getCountry()
                    : (pickupStore.getCountry() != null ? pickupStore.getCountry().getCode() : null);
            if (marketCountry != null && !marketCountry.isBlank()
                    && storeCountry != null && !isSameCountry(storeCountry, marketCountry)) {
                throw new IllegalArgumentException(
                        "You can only pick up from a store in your selected country (" + marketCountry + ").");
            }

            return new FulfilmentPlan(DeliveryMethod.PICKUP, BigDecimal.ZERO,
                    estimateDeliveryTime(DeliveryMethod.PICKUP),
                    pickupStore.getId(), pickupStore.getName(), buildPickupAddress(branch),
                    null, null, storeCountry);
        }

        UserAddress address = addressRepo.findById(req.getAddressId())
                .orElseThrow(() -> new IllegalArgumentException(
                        req.getAddressId() == null
                                ? "addressId is required for delivery"
                                : "Address not found: " + req.getAddressId()));

        if (address.getUser() == null || !userId.equals(address.getUser().getId())) {
            throw new IllegalArgumentException("Address does not belong to the authenticated user");
        }

        // The delivery address must be in the user's market (when one is set).
        if (marketCountry != null && !marketCountry.isBlank()
                && !isSameCountry(address.getCountry(), marketCountry)) {
            throw new IllegalArgumentException("You can only purchase products for delivery in your selected country ("
                    + marketCountry + ").");
        }

        // Honor the customer's choice. EXPRESS is re-validated against the address (store within
        // the 30-min radius); if it can't be fulfilled we quietly downgrade to REGULAR rather than
        // failing the order — this must never throw, so it can't regress an order that previously
        // succeeded.
        DeliveryMethod requested = req.getDeliveryMethod();
        DeliveryMethod method;
        if (requested == DeliveryMethod.EXPRESS) {
            DeliveryMethod resolved;
            try {
                resolved = resolveDeliveryMethod(cartItems, address);
            } catch (RuntimeException ex) {
                resolved = DeliveryMethod.REGULAR;
            }
            method = resolved;
        } else if (requested != null) {
            method = requested;
        } else {
            method = resolveDeliveryMethod(cartItems, address);
        }
        BigDecimal shippingFee = calculateShippingFee(
                cart.getTotalPrice(), cart.getCurrency(), method, address.getCountry());

        return new FulfilmentPlan(method, shippingFee, estimateDeliveryTime(method),
                null, null, null, address.getId(), address, address.getCountry());
    }

    /**
     * Cancels a prior PENDING_PAYMENT order that no longer matches the checkout being submitted.
     *
     * <p>Plain private and REQUIRED — deliberately neither REQUIRES_NEW nor proxied. This is not an
     * after-commit callback; it runs inside createOrder's own transaction, and that is the point:
     * if createOrder rolls back, the stale order must be payable again with its stock and its promo
     * claim intact, not half-cancelled with no replacement.
     *
     * <p>Deliberately does NOT call applyCancellationSideEffects: that path refunds money and
     * emails cancellation notices, and a PENDING_PAYMENT order has taken no money — firing it here
     * would email the customer a cancellation in the middle of their own checkout.
     *
     * <p>Must run BEFORE the promo validation and the stock decrement below it: the stale order is
     * holding the very code and the very units the fresh order is about to ask for.
     */
    private void supersedeStaleOrder(Order stale, UUID cartId) {
        String reason = "Superseded by a re-entered checkout for cart " + cartId;
        log.warn("[ORDER] createOrder: checkout for cart {} no longer matches PENDING_PAYMENT order {} "
                + "(fee/coupon/address/method/basket changed); cancelling it", cartId, stale.getId());
        stale.setCancellationReason(reason);
        // transitionTo stamps cancelledAt and returns the stock, in this transaction.
        transitionTo(stale, OrderStatus.CANCELLED);
        appendTrackingEvent(stale, OrderStatus.CANCELLED, reason,
                null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
        orderRepo.save(stale);
        // Joins this transaction on purpose — see the class comment above.
        promoCodeService.releaseReservation(stale.getId());
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
        // Block ordering for accounts pending deletion — they must recover their account first.
        accountStatusValidator.requireActiveAccount(userId);

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
            // Path 1 — the order exists. Resolve, classify, and make sure EVERY reachable state
            // ends in a written record. The old shape — `if (PENDING_PAYMENT) { ... }` with no
            // else — is how a payment settling after a cancellation vanished: money captured, no
            // log, no refund, and the reconciler scanning only PENDING_PAYMENT never saw it.
            Order order = orderRepo.findById(tx.getAppOrderId()).orElse(null);
            if (order == null) {
                paymentAnomalyService.recordAndAlert(
                        com.buyology.ecommerce.payment.enums.PaymentAnomalyKind.ORPHANED_NO_ORDER,
                        tx, tx.getAppOrderId(), null,
                        "SUCCESS payment references a missing order row", "LISTENER");
                return;
            }

            var classification = com.buyology.ecommerce.payment.service.PaymentAnomalyService.classify(
                    order.getStatus(),
                    tx.getId().equals(order.getPaymentTransactionId()),
                    isPaidAmountSufficient(order, tx),
                    // A supplier: the extra query runs only on the anomaly branch, never on the
                    // happy path every payment takes.
                    () -> paymentAnomalyService.settledByAnotherSuccessfulPayment(order.getId(), tx.getId()));

            switch (classification.outcome()) {
                case ALREADY_APPLIED -> log.info(
                        "[ORDER] Payment {} already settled order {} ({}); duplicate event ignored.",
                        tx.getId(), order.getId(), order.getStatus());
                case ANOMALY -> {
                    // Durable evidence FIRST (REQUIRES_NEW, its own connection), so it survives
                    // whatever this transaction does next. Then a tracking line so the order's own
                    // history shows the money event.
                    paymentAnomalyService.recordAndAlert(classification.kind(), tx, order.getId(),
                            order.getStatus(),
                            "paid " + tx.getAmount() + " " + tx.getCurrency() + " while the order was "
                                    + order.getStatus() + " (total " + order.getTotalAmount() + " "
                                    + order.getCurrency() + ")", "LISTENER");
                    appendTrackingEvent(order, order.getStatus(),
                            "Payment " + tx.getId() + " (" + tx.getAmount() + " " + tx.getCurrency()
                                    + ") arrived while the order was " + order.getStatus()
                                    + " — flagged for payment review",
                            null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                    orderRepo.save(order);
                }
                case APPLY -> applySettledPayment(order, tx, event);
            }
        } else if (tx.getCartId() != null) {

            // Path 2 — cart-first flow
            log.info("[ORDER] Cart-first flow: cartId={}", tx.getCartId());
            Cart cart = cartRepo.findById(tx.getCartId()).orElse(null);
            
            // If already cleared, the cart items list will be empty
            if (cart == null || cart.getStatus() == Cart.CartStatus.ABANDONED) {
                log.info("[ORDER] Cart {} already processed or ABANDONED.", tx.getCartId());
                return;
            }

            List<CartItem> cartItems = cartItemRepo.findByCartIdAndSelectedTrue(cart.getId());
            if (cartItems.isEmpty()) {
                log.warn("[ORDER] Cart has no items: cartId={}", cart.getId());
                return;
            }

            // Parse address, shipping fee, delivery method, and pickup store from transaction metadata
            UUID addressId = null;
            UUID pickupStoreId = null;
            BigDecimal shippingFee = BigDecimal.ZERO;
            DeliveryMethod metaDeliveryMethod = null;
            try {
                if (tx.getMetadata() != null) {
                    log.info("[ORDER] Transaction metadata: {}", tx.getMetadata());
                    JsonNode meta = objectMapper.readTree(tx.getMetadata());
                    if (meta.has("addressId")) addressId = UUID.fromString(meta.get("addressId").asText());
                    if (meta.has("pickupStoreId")) pickupStoreId = UUID.fromString(meta.get("pickupStoreId").asText());
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

            boolean isPickup = metaDeliveryMethod == DeliveryMethod.PICKUP;

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

            CreateOrderRequest req = new CreateOrderRequest();
            req.setCartId(tx.getCartId());

            // Carry the coupon from an order-first order already standing on this cart. Paymob's
            // metadata never carries couponCode, so without this the stricter CheckoutIdentity
            // would read "coupon removed", supersede a discounted order, rebuild it at full price
            // — and the underpayment check would then flag the customer's own correct payment.
            orderRepo.findFirstByCartIdAndStatusIn(tx.getCartId(), List.of(OrderStatus.PENDING_PAYMENT))
                    .ifPresent(pending -> req.setCouponCode(pending.getCouponCode()));

            if (isPickup) {
                if (pickupStoreId == null) {
                    log.warn("[ORDER] PICKUP order but pickupStoreId is null — cannot create order. metadata={}", tx.getMetadata());
                    return;
                }
                req.setDeliveryMethod(DeliveryMethod.PICKUP);
                req.setPickupStoreId(pickupStoreId);
                req.setShippingFee(BigDecimal.ZERO);
            } else {
                if (addressId == null) {
                    log.warn("[ORDER] addressId is null — cannot create order. metadata={}", tx.getMetadata());
                    return;
                }
                UserAddress address = addressRepo.findById(addressId).orElse(null);
                if (address == null) {
                    log.warn("[ORDER] UserAddress not found: addressId={}", addressId);
                    return;
                }

                // Use delivery method from metadata if the frontend sent it, BUT only trust
                // EXPRESS when the address has lat/lng — without coordinates we cannot route
                // to the courier backend. Fall back to resolveDeliveryMethod otherwise.
                boolean addressHasCoordinates = address.getLatitude() != null && address.getLongitude() != null;
                DeliveryMethod deliveryMethod = (metaDeliveryMethod != null && (metaDeliveryMethod != DeliveryMethod.EXPRESS || addressHasCoordinates))
                        ? metaDeliveryMethod
                        : resolveDeliveryMethod(cartItems, address);
                req.setAddressId(addressId);
                req.setDeliveryMethod(deliveryMethod);
                req.setShippingFee(shippingFee);
            }

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
                    // "Manual review" is only real if a review queue holds it. The order id is
                    // passed explicitly — the tx back-fill has not run yet at this point.
                    paymentAnomalyService.recordAndAlert(
                            com.buyology.ecommerce.payment.enums.PaymentAnomalyKind.UNDERPAID,
                            tx, order.getId(), order.getStatus(),
                            "cart-first: paid " + tx.getAmount() + " " + tx.getCurrency()
                                    + " against an order total of " + order.getTotalAmount() + " "
                                    + order.getCurrency(), "LISTENER");
                    appendTrackingEvent(order, order.getStatus(),
                            "Underpaid — payment " + tx.getId() + " does not cover the order total; "
                                    + "flagged for payment review",
                            null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                    orderRepo.save(order);
                } else {
                    transitionTo(order, OrderStatus.PAID);
                    order.setPaymentTransactionId(event.getTransactionId());
                    order.setPaidAt(Instant.now());
                    appendTrackingEvent(order, OrderStatus.PAID, "Payment confirmed",
                            null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                    orderRepo.save(order);
                    // Record promo redemption now that the order is actually paid.
                    recordPromoUsageOnPaid(order);
                    // Notify suppliers + superadmins of the new paid order.
                    notifyNewOrder(order);
                    // Email the customer their order confirmation (climate/SDG content).
                    sendOrderConfirmationEmailFor(order);
                    // Hand off to downstream integrations (ERPNext sales order + invoice).
                    eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));
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
            cartCheckoutCleanupService.clearOrderedItems(cart.getId());
        }
    }

    /**
     * Defense-in-depth amount reconciliation: confirm the amount actually paid (the
     * HMAC-verified transaction) covers the order's authoritative server-side total,
     * net of any B2B credit already applied. Compares in the transaction's currency
     * with a 1% tolerance for rounding / FX drift. Fails OPEN on a computation error
     * (logged) so a transient FX glitch never strands a legitimate payment.
     */
    /** Delegates to the extracted policy so the classification is testable. Behaviour unchanged. */
    private boolean isPaidAmountSufficient(Order order, PaymentTransaction tx) {
        return paidAmountPolicy.covers(order, tx);
    }


    /**
     * The happy path, verbatim from before the classification existed: promote to PAID and run
     * every paid-order side effect.
     */
    private void applySettledPayment(Order order, PaymentTransaction tx, PaymentSucceededEvent event) {
        transitionTo(order, OrderStatus.PAID);
        order.setPaymentTransactionId(event.getTransactionId());
        order.setPaidAt(Instant.now());
        appendTrackingEvent(order, OrderStatus.PAID, "Payment confirmed",
                null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
        orderRepo.save(order);

        // Record promo redemption now that the order is actually paid.
        recordPromoUsageOnPaid(order);

        // Notify suppliers (owning items) + superadmins of the new paid order.
        notifyNewOrder(order);

        // Email the customer their order confirmation (climate/SDG content).
        sendOrderConfirmationEmailFor(order);

        // Hand off to downstream integrations (ERPNext sales order + invoice).
        // Listeners run after this transaction commits, so a slow or failing
        // integration can never affect the order or the payment.
        eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));

        // Clear the cart safely (idempotent)
        if (order.getCartId() != null) {
            cartCheckoutCleanupService.clearOrderedItems(order.getCartId());
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
                transitionTo(order, OrderStatus.FAILED);
                order.setPaymentTransactionId(event.getTransactionId());
                String reason = event.getReason() != null ? event.getReason() : "Payment failed";
                appendTrackingEvent(order, OrderStatus.FAILED, reason,
                        null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
                orderRepo.save(order);
                // The order can never be paid now, so the code it was holding goes back. Without
                // this a customer whose card was declined would have burned their own single-use
                // code on an order that charged them nothing.
                //
                // Deliberately unguarded and in this transaction. If the release fails, the FAILED
                // status must fail with it: an order still sitting at PENDING_PAYMENT has not
                // finished, and one that is payable while its code has been given away is exactly
                // the double redemption the reservation exists to prevent.
                promoCodeService.releaseReservation(order.getId());
                log.info("[ORDER] Order {} transitioned to FAILED.", order.getId());
            }
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
        // Without a delivery country we can't assert store-country parity → fall back to
        // REGULAR instead of throwing a spurious "same country" error on a valid order.
        if (deliveryCountry == null || deliveryCountry.isBlank()) {
            return DeliveryMethod.REGULAR;
        }
        boolean allMatchCountry = cartItems.stream()
                .allMatch(item -> storeProductRepo.findByStore_IdAndProduct_IdAndIsActiveTrue(item.getStoreId(), item.getProduct().getId())
                        .map(sp -> sp.getStore() != null
                                && sp.getStore().getCountry() != null
                                && sp.getStore().getCountry().getCode() != null
                                && sp.getStore().getCountry().getCode().equalsIgnoreCase(deliveryCountry))
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

    /**
     * The delivery fee for this order, in the cart's display currency.
     *
     * <p>Method- and country-dependent: 30-minute delivery (our own couriers) and standard delivery
     * are priced differently, and Quiqup's rate applies only where Quiqup actually deliver. See
     * {@link DeliveryFeePolicy}, which both this and the cart read so the fee quoted while shopping
     * cannot drift from the fee charged.
     */
    private BigDecimal calculateShippingFee(BigDecimal subtotal, String currency,
                                            DeliveryMethod method, String deliveryCountry) {
        BigDecimal subtotalAed = BASE_CURRENCY.equalsIgnoreCase(currency)
                ? subtotal
                : currencyExchangeService.convert(subtotal, currency, BASE_CURRENCY);
        BigDecimal feeAed = deliveryFeePolicy.feeAed(method, deliveryCountry, subtotalAed);
        if (feeAed.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return BASE_CURRENCY.equalsIgnoreCase(currency)
                ? feeAed
                : currencyExchangeService.convert(feeAed, BASE_CURRENCY, currency);
    }

    private String estimateDeliveryTime(DeliveryMethod method) {
        if (method == DeliveryMethod.PICKUP) {
            return "Ready for pickup within 24 hours";
        } else if (method == DeliveryMethod.EXPRESS) {
            return "Within 30 minutes";
        } else {
            return "2-3 business days"; // Placeholder for regular order estimate
        }
    }

    /** Builds a single-line address for a pickup store branch (snapshotted onto the order). */
    private String buildPickupAddress(StoreLocation branch) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (branch.getBranchName() != null && !branch.getBranchName().isBlank()) parts.add(branch.getBranchName());
        if (branch.getAddress() != null && !branch.getAddress().isBlank()) parts.add(branch.getAddress());
        if (branch.getCity() != null && !branch.getCity().isBlank()) parts.add(branch.getCity());
        if (branch.getCountry() != null && !branch.getCountry().isBlank()) parts.add(branch.getCountry());
        return String.join(", ", parts);
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
        return toSummaryPage(orderRepo.findAllWithFilters(status, deliveryMethod, storeId, supplierId, pageable));
    }

    /** Orders containing the given supplier's items — for the supplier portal orders view. */
    public Page<OrderSummaryResponse> listOrdersForSupplier(UUID supplierId, OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return toSummaryPage(orderRepo.findBySupplierId(supplierId, status, pageable));
    }

    /**
     * Maps a page of orders to summaries, enriching each with the customer's name + email. Names/emails
     * are batch-loaded for the whole page (two queries total) to avoid an N+1 across list rows.
     */
    private Page<OrderSummaryResponse> toSummaryPage(Page<Order> orders) {
        List<UUID> userIds = orders.getContent().stream()
                .map(Order::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Users> usersById = userIds.isEmpty() ? Map.of()
                : userRepo.findAllById(userIds).stream()
                    .collect(java.util.stream.Collectors.toMap(Users::getId, u -> u, (a, b) -> a));

        Map<UUID, String> emailByUserId = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            for (var c : authCredentialRepository.findByUserIdIn(userIds)) {
                String email = c.getEmail();
                if (email != null && !email.isBlank()) emailByUserId.putIfAbsent(c.getUserId(), email);
            }
        }

        Map<UUID, String> profilePhoneByUserId = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserProfiles p : userProfileRepo.findByUserIdIn(userIds)) {
                UUID uid = p.getUser() != null ? p.getUser().getId() : null;
                String phone = p.getPhoneNumber();
                if (uid != null && phone != null && !phone.isBlank()) profilePhoneByUserId.putIfAbsent(uid, phone);
            }
        }

        return orders.map(o -> {
            OrderSummaryResponse res = toSummaryResponse(o);
            Users u = o.getUserId() != null ? usersById.get(o.getUserId()) : null;
            // Prefer the account name; fall back to the order's recipient snapshot.
            String firstName = u != null ? u.getFirstName() : null;
            String lastName  = u != null ? u.getLastName()  : null;
            if (isBlank(firstName) && isBlank(lastName)) {
                firstName = o.getRecipientFirstName();
                lastName  = o.getRecipientLastName();
            }
            res.setCustomerFirstName(firstName);
            res.setCustomerLastName(lastName);
            if (o.getUserId() != null) res.setCustomerEmail(emailByUserId.get(o.getUserId()));
            // Prefer the verified profile phone; fall back to the order's recipient snapshot.
            String phone = o.getUserId() != null ? profilePhoneByUserId.get(o.getUserId()) : null;
            res.setCustomerPhone(firstNonBlank(phone, o.getRecipientPhone()));
            return res;
        });
    }

    /**
     * Admin status update — enforces the state machine and applies side effects
     * (milestone timestamps, courier assignment, carrier info via AdminTrackingUpdateRequest).
     */
    @Transactional
    public OrderResponse adminUpdateStatus(UUID orderId, UUID adminUserId, AdminStatusUpdateRequest req) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateTransition(order.getStatus(), req.getStatus(), order.getDeliveryMethod());

        // Assign courier when moving to COURIER_ASSIGNED (legacy flow)
        if (req.getStatus() == OrderStatus.COURIER_ASSIGNED && req.getCourierUserId() != null) {
            order.setCourierUserId(req.getCourierUserId());
        }

        // Capture cancellation reason from admin/customer
        if (req.getStatus() == OrderStatus.CANCELLED && req.getCancellationReason() != null) {
            order.setCancellationReason(req.getCancellationReason());
        }

        transitionTo(order, req.getStatus());

        // The durability hinge of the courier-cancel design: the INTENT to stop the Quiqup job is
        // written in the same transaction that cancels the order, so a replica dying between the
        // commit and the after-commit callback still leaves a row the retry job will find. Without
        // this the crash window silently reverts to the old behaviour — refund out, courier still
        // driving.
        if (req.getStatus() == OrderStatus.CANCELLED
                && order.getQuiqupOrderId() != null && !order.getQuiqupOrderId().isBlank()
                && order.getQuiqupCancelStatus() == null) {
            order.setQuiqupCancelStatus("PENDING");
            order.setQuiqupCancelRequestedAt(Instant.now());
        }

        appendTrackingEvent(order, req.getStatus(), req.getNotes(),
                null, null, null, adminUserId, "ADMIN");

        Order saved = orderRepo.save(order);
        // Fire notifications ONLY after the status change is durably committed — otherwise a
        // constraint failure at flush/commit rolls the status back while the customer has
        // already been emailed/pushed (the exact "email sent but status didn't update" bug).
        runAfterCommit(() -> {
            broadcastStatusUpdate(saved, null);
            notifyCustomerStatus(saved);
            if (saved.getStatus() == OrderStatus.CANCELLED) {
                // Stop the courier BEFORE any money moves. An admin cancellation is authoritative,
                // so the order stays CANCELLED whatever Quiqup says — only the money leg is gated
                // on the courier being verifiably stopped. No transaction is open here (the
                // committed one is still bound to the thread but does nothing), and the cancel
                // service does its own writes in REQUIRES_NEW.
                var courier = quiqupCancelService.cancelForOrder(saved.getId(), req.getCancellationReason());
                selfProvider.getObject().applyCancellationSideEffects(
                        saved, req.getCancellationReason(), courier.refundAllowed());
            }
        });

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

        validateTransition(order.getStatus(), req.getStatus(), order.getDeliveryMethod());

        // Require tracking code when shipping
        if (req.getStatus() == OrderStatus.SHIPPED
                && order.getDeliveryMethod() == DeliveryMethod.REGULAR
                && (req.getTrackingCode() == null || req.getTrackingCode().isBlank())) {
            throw new IllegalArgumentException(
                    "trackingCode is required when marking a REGULAR as SHIPPED");
        }

        if (req.getTrackingCode() != null) order.setTrackingCode(req.getTrackingCode());
        if (req.getCarrierName() != null)  order.setCarrierName(req.getCarrierName());

        transitionTo(order, req.getStatus());

        appendTrackingEvent(order, req.getStatus(), req.getNotes(),
                null, null, req.getLocationDescription(), adminUserId, "ADMIN");

        Order saved = orderRepo.save(order);
        runAfterCommit(() -> broadcastStatusUpdate(saved, null));
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
     *
     * <p>Deliberately NOT {@code @Transactional}: when the order has been handed to Quiqup, the
     * courier must be verifiably stopped BEFORE we cancel anything — and that is an HTTP call of up
     * to the configured timeout, which must not hold a pooled connection. The cancellation itself
     * happens in {@link #applyCustomerCancellation}, reached through the proxy so its
     * {@code @Transactional} actually applies.
     *
     * <p>Ordering is the whole point. Cancel-first-ask-later is the bug this replaces: the refund
     * went out while the courier kept driving, and the customer kept the goods and the money.
     */
    public OrderResponse customerCancelOrder(UUID orderId, UUID userId, String reason) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // The cheap guard first, so an obviously-too-late cancel fails instantly with the usual
        // message and never pays the courier round-trip.
        if (!isCustomerCancellable(order.getStatus())) {
            throw new IllegalStateException(
                    "This order can no longer be cancelled — it is already on its way to you.");
        }

        boolean dispatched = order.getQuiqupOrderId() != null && !order.getQuiqupOrderId().isBlank();
        var courier = dispatched
                ? quiqupCancelService.cancelForOrder(orderId, reason)
                : new com.buyology.ecommerce.quiqup.service.QuiqupCancelService.CancelResult(
                        com.buyology.ecommerce.quiqup.service.QuiqupCancelService.Outcome.NOTHING_TO_CANCEL,
                        "never dispatched");

        switch (courier.outcome()) {
            case CONFIRMED, NOTHING_TO_CANCEL -> { /* safe to proceed */ }
            case REFUSED_TOO_LATE -> throw new IllegalStateException(
                    "This order can no longer be cancelled — the courier has already collected it. "
                    + "Refuse the delivery at the door, or start a return once it arrives.");
            case UNCONFIRMED, NEEDS_HUMAN -> {
                if (quiqupCancelService.strictCustomerPreflight()) {
                    // Nothing has been written, so "try again in a minute" is completely clean.
                    throw new IllegalStateException(
                            "We could not reach the courier to stop this delivery. Nothing has been "
                            + "changed — please try again in a minute.");
                }
                // Cancel-anyway mode: commit the cancellation, withhold the refund, and let the
                // retry job resolve the courier. transitionTo withholds the stock for the same
                // evidence, so nothing is sellable while the parcel may still be moving.
            }
        }

        try {
            return selfProvider.getObject().applyCustomerCancellation(orderId, userId, reason, courier);
        } catch (RuntimeException e) {
            if (dispatched && courier.refundAllowed()) {
                // The courier is stopped but the order could not be cancelled (a concurrent admin
                // move inside the HTTP window). Three facts now disagree — job cancelled, order
                // live, stock held — and only a human can re-dispatch.
                log.error("[QUIQUP] Cancelled Quiqup job {} but order {} could not be cancelled — "
                        + "the job needs re-dispatching by hand", order.getQuiqupOrderId(), orderId, e);
                alertSuperadmins(orderId, "Quiqup job cancelled but order still live",
                        "Order " + orderId + ": the courier was stopped but the cancellation failed. "
                        + "Re-dispatch or cancel by hand.");
            }
            throw e;
        }
    }

    /**
     * The transactional half of a customer cancellation. Public and proxy-invoked only — call it
     * through {@code selfProvider} or the {@code @Transactional} silently does not apply.
     *
     * @param preflight what the courier pre-flight concluded; decides whether money may move
     */
    @Transactional
    public OrderResponse applyCustomerCancellation(UUID orderId, UUID userId, String reason,
                                                   com.buyology.ecommerce.quiqup.service.QuiqupCancelService.CancelResult preflight) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Re-checked inside the transaction: the pre-flight window is long enough for a concurrent
        // admin move, and the guard outside was only ever the cheap first pass.
        if (!isCustomerCancellable(order.getStatus())) {
            throw new IllegalStateException(
                    "This order can no longer be cancelled — it is already on its way to you.");
        }
        validateTransition(order.getStatus(), OrderStatus.CANCELLED, order.getDeliveryMethod());

        order.setCancellationReason(reason);
        transitionTo(order, OrderStatus.CANCELLED);
        appendTrackingEvent(order, OrderStatus.CANCELLED,
                reason != null ? reason : "Cancelled by customer",
                null, null, null, userId, "CUSTOMER");

        Order saved = orderRepo.save(order);
        boolean refundAllowed = preflight.refundAllowed();
        runAfterCommit(() -> {
            broadcastStatusUpdate(saved, null);
            selfProvider.getObject().applyCancellationSideEffects(saved, reason, refundAllowed);
        });
        return toOrderResponse(saved);
    }

    /** Statuses a customer is still allowed to cancel from (before the order leaves for delivery). */
    @SuppressWarnings("deprecation") // legacy statuses kept for historical orders
    private boolean isCustomerCancellable(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT, PAID, PACKAGING, READY_FOR_PICKUP, IN_COURIER,
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
     * On cancellation of a paid order: return any B2B credit, auto-initiate a refund of the charged
     * amount, and email the customer. Best-effort — failures are logged, never block the
     * cancellation. No-op for unpaid (PENDING_PAYMENT) orders.
     *
     * <p><strong>Must be invoked after the cancellation commits, never inside its transaction.</strong>
     * Everything here is irreversible from the database's point of view: money leaves through Paymob,
     * credit lands in a wallet, mail goes out. Running it inside the transaction meant any later
     * failure — a constraint at flush, a mapping error in the response — rolled the status back to
     * PAID while the refund was already on its way, leaving an order that is fully refunded, still
     * shippable, and looks untouched. Every caller wraps it in {@code runAfterCommit} for that
     * reason, which also swallows failures here rather than letting them undo the cancellation.
     *
     * <p>REQUIRES_NEW, and called through {@code selfProvider} so the annotation is actually honoured.
     * Inside an after-commit callback the finished transaction is still bound to the thread, so a
     * REQUIRED transaction started here would <em>join</em> it — flushing its writes into a session
     * nobody will ever commit and then closing it. The refund would reach Paymob and every row
     * recording it would disappear. The same reasoning is why {@code onPaymentSucceeded} and
     * {@code onPaymentFailed} above are REQUIRES_NEW.
     *
     * <p>{@code refundAllowed} is the courier gate. False means the order's Quiqup job could not be
     * verified as stopped, so everything of value — the gateway refund, the B2B credit, the
     * withheld stock, and the "your money is coming" emails — waits, and a superadmin is alerted
     * instead. The retry job re-invokes this with {@code true} once Quiqup confirms. Paths with no
     * courier involvement pass {@code true} unconditionally.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyCancellationSideEffects(Order order, String reason, boolean refundAllowed) {
        String email = customerEmail(order);
        String name = customerName(order);
        String orderNo = order.getId().toString();

        boolean refunded = false;
        BigDecimal refundAmount = null;
        String refundCurrency = null;

        if (!refundAllowed) {
            // The courier gate. False means the Quiqup job carrying this order's parcel could not
            // be verified as stopped: the parcel may be moving toward the customer right now, and
            // paying the money back while it does is the double loss this gate exists to prevent.
            // Everything of VALUE below waits — the refund, the B2B credit, the "your money is
            // coming" emails, and the stock. The retry job re-runs this with refundAllowed=true
            // once Quiqup confirms; REFUSED_TOO_LATE and NEEDS_HUMAN land on a superadmin instead.
            log.error("[ORDER] NOT auto-refunding cancelled order {} — the Quiqup job {} was not "
                    + "confirmed cancelled (quiqupCancelStatus={}). The parcel may still be moving; "
                    + "a human or the retry job decides the money.",
                    order.getId(), order.getQuiqupOrderId(), order.getQuiqupCancelStatus());
        } else {
            // The credit leg, which nothing used to return. See CreditReturnService.
            //
            // Guarded because it can genuinely throw — WalletService.addCredit raises
            // NoSuchElementException for a member with no wallet row — and unguarded it took the
            // gateway refund and both customer emails down with it, silently, from inside an
            // after-commit callback. A credit line needing a manual correction is a support
            // ticket; a cancelled order that was never refunded is the customer's money.
            try {
                creditReturnService.returnForCancelledOrder(order.getId(), order.getCreditApplied());
            } catch (Exception e) {
                log.error("[ORDER] Could not return B2B credit for cancelled order {} — needs manual "
                        + "correction: {}", order.getId(), e.getMessage(), e);
            }

            // Stock that transitionTo withheld because the courier was not yet confirmed stopped.
            // This runs in a REQUIRES_NEW transaction AFTER the cancellation committed, so there is
            // no outer lock to self-block on, and stockRestoredAt keeps it exactly-once — for the
            // common paths (customer pre-flight, partner-confirmed) the release already happened
            // inside transitionTo and this is a no-op.
            try {
                orderRepo.findById(order.getId()).ifPresent(stockReservationService::releaseForOrder);
            } catch (Exception e) {
                log.error("[ORDER] Could not release withheld stock for cancelled order {}: {}",
                        order.getId(), e.getMessage(), e);
            }
        }

        // Give back a promo code this order was only holding. A code it actually spent stays spent:
        // that order was paid, the customer got the discount, and the refund settles it in money.
        // Deliberately NOT behind the courier gate — a reservation is bookkeeping, not value handed
        // to the customer, and the stale-order and payment-failed paths depend on it always firing.
        //
        // The independent variant, because this runs after the cancellation has committed: joining
        // would let a failure here mark the surrounding transaction rollback-only, and catching it
        // would then surface as an UnexpectedRollbackException that mentions nothing about promos.
        try {
            promoCodeService.releaseReservationIndependently(order.getId());
        } catch (Exception e) {
            log.warn("[ORDER] Could not release the promo reservation held by cancelled order {}: {}",
                    order.getId(), e.getMessage());
        }

        if (refundAllowed && order.getCancelRefundInitiatedAt() != null) {
            // A cheap short-circuit and an audit trail, NOT the double-refund guard — that stays
            // RefundClaimStore, which counts SUCCESS and PENDING refunds before any HTTP reaches
            // Paymob. This just keeps a retried side-effect pass from re-sending the email pair.
            log.info("[ORDER] Cancellation refund already initiated for order {} at {}; skipping",
                    order.getId(), order.getCancelRefundInitiatedAt());
        } else if (refundAllowed && order.getPaymentTransactionId() != null) {
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
                    // Stamped in this REQUIRES_NEW transaction, so a retried pass sees it.
                    orderRepo.findById(order.getId()).ifPresent(o -> {
                        o.setCancelRefundInitiatedAt(Instant.now());
                        orderRepo.save(o);
                    });
                    log.info("[ORDER] Auto-refund initiated for cancelled order {}", order.getId());
                }
            } catch (Exception e) {
                log.warn("[ORDER] Auto-refund on cancel failed for order {} — needs manual refund: {}",
                        order.getId(), e.getMessage());
            }
        }

        if (!refundAllowed) {
            // The customer must NOT be told their order is cancelled and their money is coming
            // while a courier may still be carrying the parcel. A person makes contact instead.
            alertSuperadmins(order.getId(), "Cancelled order awaiting courier confirmation",
                    "Order " + orderNo + " is cancelled but its Quiqup job is not confirmed stopped. "
                    + "Refund and customer contact are on hold.");
            return;
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

    /** Push an operational alert to every superadmin. Best-effort; never throws. */
    private void alertSuperadmins(UUID orderId, String title, String body) {
        try {
            java.util.Map<String, String> data = java.util.Map.of(
                    "orderId", orderId.toString(), "type", "ORDER_ATTENTION");
            userRoleRepository.findUserIdsByRoleName("SUPERADMIN").forEach(uid ->
                    pushService.sendToUser(uid, title, body, "ORDER_ATTENTION", data));
        } catch (Exception e) {
            log.warn("[ORDER] Could not alert superadmins about order {}: {}", orderId, e.getMessage());
        }
    }

    private String customerEmail(Order order) {
        if (order.getUserId() == null) return null;
        return authCredentialRepository.findByUserId(order.getUserId()).stream()
                .map(com.buyology.ecommerce.auth.domain.AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst().orElse(null);
    }

    /**
     * Populates the customer's name/email/phone on the response so the admin order-detail view can show
     * who placed the order. The account record is often incomplete — email/password signups never set
     * Users.firstName/lastName, and a phone is only stored after SMS verification — so we fall back to
     * the order's recipient snapshot (copied from the chosen address at checkout) and then to the
     * customer's default saved address, both of which reliably carry a name and phone.
     */
    private void populateCustomerContact(OrderResponse res, Order order) {
        UUID userId = order.getUserId();
        if (userId == null) return;

        Users user = userRepo.findById(userId).orElse(null);
        String firstName = user != null ? user.getFirstName() : null;
        String lastName  = user != null ? user.getLastName()  : null;

        var creds = authCredentialRepository.findByUserId(userId);
        res.setCustomerEmail(creds.stream()
                .map(com.buyology.ecommerce.auth.domain.AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst().orElse(null));
        String authPhone = creds.stream()
                .map(com.buyology.ecommerce.auth.domain.AuthCredentials::getPhoneNumber)
                .filter(p -> p != null && !p.isBlank())
                .findFirst().orElse(null);
        String profilePhone = userProfileRepo.findByUserId(userId)
                .map(UserProfiles::getPhoneNumber)
                .filter(p -> p != null && !p.isBlank())
                .orElse(null);

        // Prefer account phone, then the order's recipient snapshot.
        String phone = firstNonBlank(profilePhone, authPhone, order.getRecipientPhone());
        // Prefer the account name, then the order's recipient snapshot.
        if (isBlank(firstName) && isBlank(lastName)) {
            firstName = order.getRecipientFirstName();
            lastName  = order.getRecipientLastName();
        }

        // Last resort (pickup orders by email/password customers carry no snapshot name/phone):
        // pull from the customer's default saved address.
        if (user != null && ((isBlank(firstName) && isBlank(lastName)) || isBlank(phone))) {
            UserAddress def = addressRepo.findByUserAndIsDefaultTrue(user).orElse(null);
            if (def != null) {
                if (isBlank(firstName) && isBlank(lastName)) {
                    firstName = def.getFirstName();
                    lastName  = def.getLastName();
                }
                if (isBlank(phone)) phone = def.getPhoneNumber();
            }
        }

        res.setCustomerFirstName(firstName);
        res.setCustomerLastName(lastName);
        res.setCustomerPhone(phone);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String customerName(Order order) {
        // First name lives on the Users entity (not UserProfiles); the email greets generically
        // when absent, so we keep this dependency-free and return null.
        return null;
    }

    /** Best-effort order-confirmation email (with climate/SDG content) once an order is PAID. */
    private void sendOrderConfirmationEmailFor(Order order) {
        try {
            String email = customerEmail(order);
            if (email == null) return;

            List<OrderItem> orderItems = order.getItems() != null ? order.getItems() : java.util.List.of();
            // The order item only snapshots SKUs — resolve the English product name to show in the email.
            Map<UUID, String> nameByProductId = resolveEnglishProductNames(orderItems);

            List<com.buyology.ecommerce.common.service.EmailService.OrderEmailItem> lines = new ArrayList<>();
            int deviceCount = 0;
            for (OrderItem it : orderItems) {
                int qty = it.getQuantity() == null ? 0 : it.getQuantity();
                deviceCount += qty;
                String fallbackSku = (it.getVariantSku() != null && !it.getVariantSku().isBlank())
                        ? it.getVariantSku() : it.getProductSku();
                String itemName = it.getProductId() != null ? nameByProductId.get(it.getProductId()) : null;
                if (itemName == null || itemName.isBlank()) itemName = fallbackSku;
                lines.add(new com.buyology.ecommerce.common.service.EmailService.OrderEmailItem(
                        itemName, qty, it.getUnitPrice(), it.getTotalPrice()));
            }

            String orderDate = order.getCreatedAt() != null
                    ? java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
                        .withZone(java.time.ZoneId.of("Asia/Dubai")).format(order.getCreatedAt())
                    : "";
            String displayNo = "#" + order.getId().toString().substring(0, 8).toUpperCase();
            String orderUrl = STOREFRONT_URL + "/en/orders/" + order.getId();

            emailService.sendOrderConfirmationEmail(
                    email, order.getRecipientFirstName(), displayNo, orderDate, lines, order.getCurrency(),
                    order.getSubtotal(), order.getShippingFee(), order.getDiscount(), order.getTotalAmount(),
                    buildAddressBlock(order), order.getEstimatedDeliveryTime(), deviceCount, orderUrl);
        } catch (Exception e) {
            log.warn("[ORDER] order-confirmation email failed for {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Resolves a display name for each order item's product, preferring the English title and
     * falling back to any available translation. Returns a productId → name map (one bulk query,
     * no Product proxies). Callers fall back to the SKU when a product has no translation.
     */
    private Map<UUID, String> resolveEnglishProductNames(List<OrderItem> items) {
        List<UUID> productIds = items.stream()
                .map(OrderItem::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (productIds.isEmpty()) return java.util.Map.of();

        Map<UUID, String> english = new java.util.HashMap<>();
        Map<UUID, String> fallback = new java.util.HashMap<>();
        for (Object[] row : productTranslationRepository.findTitleRowsByProductIds(productIds)) {
            UUID pid = (UUID) row[0];
            String lang = (String) row[1];
            String title = (String) row[2];
            if (pid == null || title == null || title.isBlank()) continue;
            if ("EN".equalsIgnoreCase(lang)) {
                english.putIfAbsent(pid, title);
            } else {
                fallback.putIfAbsent(pid, title);
            }
        }
        Map<UUID, String> result = new java.util.HashMap<>(fallback);
        result.putAll(english); // English wins over any fallback translation
        return result;
    }

    /**
     * Resolves a presigned primary-image URL for each order item's product, batched in one query.
     * Prefers the media flagged {@code isPrimary}, else the lowest {@code orderIndex}; uses the
     * thumbnail when available. Returns a productId → URL map (products without media are omitted).
     */
    private Map<UUID, String> resolvePrimaryImageUrls(List<OrderItem> items) {
        List<UUID> productIds = items.stream()
                .map(OrderItem::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (productIds.isEmpty()) return java.util.Map.of();

        Map<UUID, ProductMedia> bestByProduct = new java.util.HashMap<>();
        for (ProductMedia m : productMediaRepository.findByProductIdIn(productIds)) {
            UUID pid = m.getProduct() != null ? m.getProduct().getId() : null;
            if (pid == null || m.getUrl() == null || m.getUrl().isBlank()) continue;
            ProductMedia current = bestByProduct.get(pid);
            if (current == null || isBetterPrimary(m, current)) {
                bestByProduct.put(pid, m);
            }
        }

        Map<UUID, String> urls = new java.util.HashMap<>();
        bestByProduct.forEach((pid, m) -> {
            String key = (m.getThumbnailUrl() != null && !m.getThumbnailUrl().isBlank())
                    ? m.getThumbnailUrl() : m.getUrl();
            String url = contaboObjectService.getPresignedUrl(key);
            if (url != null && !url.isBlank()) urls.put(pid, url);
        });
        return urls;
    }

    /** True when {@code candidate} is a better "primary" pick than {@code current} (isPrimary wins, then lower orderIndex). */
    private static boolean isBetterPrimary(ProductMedia candidate, ProductMedia current) {
        boolean candPrimary = Boolean.TRUE.equals(candidate.getIsPrimary());
        boolean currPrimary = Boolean.TRUE.equals(current.getIsPrimary());
        if (candPrimary != currPrimary) return candPrimary;
        int candIdx = candidate.getOrderIndex() == null ? Integer.MAX_VALUE : candidate.getOrderIndex();
        int currIdx = current.getOrderIndex() == null ? Integer.MAX_VALUE : current.getOrderIndex();
        return candIdx < currIdx;
    }

    /** Best-effort per-status fulfilment email (PACKAGING / IN_COURIER / IN_TRANSIT / DELIVERED). */
    private void sendStatusEmailFor(Order order) {
        try {
            String email = customerEmail(order);
            if (email == null) return;
            String displayNo = "#" + order.getId().toString().substring(0, 8).toUpperCase();
            String orderUrl = STOREFRONT_URL + "/en/orders/" + order.getId();
            // For ready-for-pickup, include the store name/address so the customer knows where to collect.
            String pickupLocation = null;
            if (order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
                pickupLocation = java.util.stream.Stream.of(order.getPickupStoreName(), order.getPickupStoreAddress())
                        .filter(s -> s != null && !s.isBlank())
                        .reduce((a, b) -> a + " — " + b).orElse(null);
            }
            emailService.sendOrderStatusEmail(
                    email, order.getRecipientFirstName(), displayNo, order.getStatus().name(), orderUrl, pickupLocation);
        } catch (Exception e) {
            log.warn("[ORDER] order-status email failed for {}: {}", order.getId(), e.getMessage());
        }
    }

    /** One-line-per-row HTML delivery address built from the order's address snapshot. */
    private String buildAddressBlock(Order order) {
        StringBuilder sb = new StringBuilder();
        // Store pickup: show the branch the customer collects from instead of a delivery address.
        if (order.getDeliveryMethod() == DeliveryMethod.PICKUP) {
            appendAddrPart(sb, "Collect from: " + (order.getPickupStoreName() == null ? "our store" : order.getPickupStoreName()));
            appendAddrPart(sb, order.getPickupStoreAddress());
            return sb.toString();
        }
        String recipient = ((order.getRecipientFirstName() == null ? "" : order.getRecipientFirstName()) + " "
                + (order.getRecipientLastName() == null ? "" : order.getRecipientLastName())).trim();
        appendAddrPart(sb, recipient);
        appendAddrPart(sb, order.getAddressLine1());
        appendAddrPart(sb, order.getAddressLine2());
        String cityLine = java.util.stream.Stream.of(order.getCity(), order.getState(), order.getPostalCode())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + ", " + b).orElse("");
        appendAddrPart(sb, cityLine);
        appendAddrPart(sb, order.getCountry());
        return sb.toString();
    }

    private void appendAddrPart(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            sb.append(part.replace("<", "&lt;").replace(">", "&gt;")).append("<br/>");
        }
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

        validateTransition(order.getStatus(), target, order.getDeliveryMethod());

        transitionTo(order, target);

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
                transitionTo(order, OrderStatus.COURIER_ASSIGNED);
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

            transitionTo(order, target);
            appendTrackingEvent(order, target, "Synced from courier backend",
                    null, null, null, proofImageUrl, SYSTEM_ACTOR_ID, "SYSTEM");
            orderRepo.save(order);
            log.info("[ORDER] Status synced from courier: orderId={} status={}",
                    ecommerceOrderId, target);
        });
    }

    /**
     * Applies a status a third-party delivery partner reported, under the same guards the courier
     * flow uses.
     *
     * <p>A sibling of {@link #syncStatusFromCourier} rather than a caller of it: that method speaks
     * our own courier service's vocabulary and lands on the deprecated {@code COURIER_ASSIGNED} /
     * {@code PICKED_UP} statuses kept for historical data. A partner integration should map onto
     * the current ones, so it passes the target status in already resolved and this method only
     * decides whether the move is allowed.
     *
     * <p>The guards matter more here than for our own couriers, because webhook deliveries arrive
     * over the public internet, are retried by the sender, and carry no ordering promise: the same
     * event can land twice, and a later one can overtake an earlier one. Terminal states are never
     * moved out of, and nothing may move an order backwards.
     *
     * @param orderId the order the partner is delivering
     * @param target  the status their update implies, already mapped to our vocabulary
     * @param note    what to record on the order's tracking history
     * @return true when the order actually moved
     */
    @Transactional
    public boolean syncStatusFromDeliveryPartner(UUID orderId, OrderStatus target, String note) {
        if (target == null) {
            return false;
        }
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        if (order.getStatus() == target) {
            return false;
        }
        if (order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.FAILED) {
            log.warn("[QUIQUP] Ignoring {} for order {} — already terminal at {}",
                    target, orderId, order.getStatus());
            return false;
        }

        boolean terminalOutcome = target == OrderStatus.FAILED || target == OrderStatus.CANCELLED;
        if (!terminalOutcome && courierStatusRank(target) <= courierStatusRank(order.getStatus())) {
            log.warn("[QUIQUP] Ignoring out-of-order status for order {}: {} -> {}",
                    orderId, order.getStatus(), target);
            return false;
        }

        if (target == OrderStatus.CANCELLED) {
            // Quiqup themselves cancelled the job, so the courier is provably stopped — there is
            // nothing to call back at them. Stamped BEFORE transitionTo, because transitionTo's
            // stock gate reads this field: without it a partner-cancelled order would have its
            // units withheld waiting on a confirmation that already happened.
            order.setQuiqupCancelStatus("CONFIRMED_BY_PARTNER");
            order.setQuiqupCancelConfirmedAt(Instant.now());
        }
        transitionTo(order, target);
        appendTrackingEvent(order, target, note, null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
        Order saved = orderRepo.save(order);
        log.info("[QUIQUP] Order {} moved to {} by the delivery partner", orderId, target);

        // Same shape as the courier flow: tell the customer only once the move is committed, so a
        // rolled-back transaction cannot leave them holding a notification for a delivery that did
        // not happen.
        runAfterCommit(() -> {
            broadcastStatusUpdate(saved, null);
            notifyCustomerStatus(saved);

            if (target == OrderStatus.CANCELLED) {
                // The delivery partner cancelling the job cancels the customer's order — this method
                // has already made that terminal, and nothing can re-deliver against it. The money
                // has to follow the goods: without this the customer paid, the parcel stayed on our
                // shelf, and the order sat CANCELLED with the payment untouched and nobody looking.
                // refundAllowed=true: the partner's own cancellation IS the confirmation.
                selfProvider.getObject().applyCancellationSideEffects(saved, note, true);
            } else if (target == OrderStatus.FAILED && saved.getPaymentTransactionId() != null) {
                // A failed delivery is NOT auto-refunded: the parcel is coming back to us and
                // whether the customer wants their money or another attempt is theirs to say. But
                // it is money held against goods they do not have, and FAILED is terminal, so it
                // cannot be left to be noticed by chance.
                log.error("[ORDER] Order {} FAILED at the delivery partner while paid (transaction {})"
                        + " — needs a human decision on refund vs redelivery",
                        orderId, saved.getPaymentTransactionId());
            }
        });
        return true;
    }

    /**
     * Records a delivery event that arrived for an order we had already cancelled.
     *
     * <p>Deliberately does NOT move the status — CANCELLED is terminal and that guard is right —
     * but the parcel evidently moved anyway, which is goods and money that need a human. Before
     * this, such events were dropped without trace: the order read as cleanly cancelled while the
     * courier delivered it.
     *
     * <p>REQUIRES_NEW because it is invoked from the webhook path, which may carry no transaction,
     * and because this record must commit regardless of anything the caller does next.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPostCancellationMovement(UUID orderId, String quiqupStatus) {
        orderRepo.findById(orderId).ifPresent(o -> {
            o.setQuiqupCancelStatus("REFUSED_TOO_LATE");
            String detail = "Quiqup reported '" + quiqupStatus + "' after we cancelled this order";
            o.setQuiqupCancelError(detail.length() > 1000 ? detail.substring(0, 1000) : detail);
            appendTrackingEvent(o, o.getStatus(),
                    "Delivery partner reported '" + quiqupStatus + "' AFTER cancellation — the parcel was not stopped",
                    null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
            orderRepo.save(o);
        });
        log.error("[QUIQUP] Order {} is CANCELLED but Quiqup reported '{}' — the parcel was delivered "
                + "or is in transit despite the cancellation. Refund/recovery needs a human.",
                orderId, quiqupStatus);
        alertSuperadmins(orderId, "Cancelled order is still being delivered",
                "Order " + orderId + ": Quiqup reported '" + quiqupStatus + "' after cancellation. "
                + "The parcel was not stopped — decide refund vs recovery.");
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

    /** Notify the customer that their order's status changed (in-app feed + push + email). */
    /**
     * Runs a side-effect only AFTER the current transaction commits — so customer
     * notifications (email/push) and WebSocket broadcasts never fire for a status
     * change that later rolls back. Previously these ran inline BEFORE commit: the
     * email lookup ({@link #customerEmail}) issues a JPA query that force-flushes the
     * pending status UPDATE + tracking INSERT mid-transaction, so a constraint failure
     * at flush/commit rolled the status back while the customer had already been
     * emailed/pushed and the admin saw a bare 409. Deferring keeps the persistence
     * step clean and makes the notification exactly mirror the committed state. Falls
     * back to immediate execution when no transaction is active (e.g. called from a
     * non-transactional context).
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.warn("[ORDER] after-commit side-effect failed: {}", e.getMessage());
                    }
                }
            });
        } else {
            action.run();
        }
    }

    private void notifyCustomerStatus(Order order) {
        try {
            if (order.getUserId() == null) return;
            String title = "Order update";
            String body;
            if (order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
                title = "Ready for pickup";
                body = (order.getPickupStoreName() != null && !order.getPickupStoreName().isBlank())
                        ? "Your order is ready to collect at " + order.getPickupStoreName() + "."
                        : "Your order is packed and ready to collect at the store.";
            } else {
                body = "Your order is now " + order.getStatus().name().replace('_', ' ').toLowerCase() + ".";
            }
            pushService.sendToUser(order.getUserId(), title, body,
                    "ORDER_STATUS",
                    java.util.Map.of("orderId", order.getId().toString(), "type", "ORDER_STATUS"));
        } catch (Exception e) {
            log.warn("[ORDER] Failed to notify customer of status for {}: {}", order.getId(), e.getMessage());
        }
        // Per-status fulfilment email — no-ops for statuses without a customer milestone
        // (PAID is emailed as the order confirmation; CANCELLED via the cancellation flow).
        sendStatusEmailFor(order);
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
        validateTransition(order.getStatus(), newStatus, order.getDeliveryMethod());
        transitionTo(order, newStatus);
        appendTrackingEvent(order, newStatus, notes, null, null, null, supplier.getId(), "SUPPLIER");
        Order saved = orderRepo.save(order);
        runAfterCommit(() -> {
            broadcastStatusUpdate(saved, null);
            notifyCustomerStatus(saved);
        });
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
     *
     * <p>Cluster-safety, stated once because three fixes now meet here: this job runs on BOTH
     * replicas (no ShedLock). It is promote-only — transitionTo(PAID) never touches stock — and
     * the cart clear is safe to run twice ONLY because clearOrderedItems deletes selected rows and
     * leaves survivors unticked, so the second replica's pass matches zero rows. Do not "improve"
     * this by re-ticking survivors.
     */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${order.reconciliation-interval-ms:300000}")
    @Transactional
    public void reconcileStuckPayments() {
        List<Order> stuck = orderRepo.findAllByStatus(
                OrderStatus.PENDING_PAYMENT, PageRequest.of(0, 200)).getContent();
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
            // An order already in the payment-review queue is a human's problem, not this job's.
            if (paymentAnomalyRepo.existsByAppOrderIdAndResolutionNot(order.getId(), "RESOLVED")) continue;
            if (!isPaidAmountSufficient(order, tx)) {
                // Recording is what makes "leave for manual review" true rather than aspirational.
                paymentAnomalyService.recordAndAlert(
                        com.buyology.ecommerce.payment.enums.PaymentAnomalyKind.UNDERPAID,
                        tx, order.getId(), order.getStatus(),
                        "reconciler: paid " + tx.getAmount() + " " + tx.getCurrency()
                                + " against an order total of " + order.getTotalAmount() + " "
                                + order.getCurrency(), "RECONCILER");
                continue;
            }

            transitionTo(order, OrderStatus.PAID);
            order.setPaymentTransactionId(tx.getId());
            order.setPaidAt(Instant.now());
            appendTrackingEvent(order, OrderStatus.PAID, "Payment reconciled (webhook recovery)",
                    null, null, null, SYSTEM_ACTOR_ID, "SYSTEM");
            orderRepo.save(order);
            if (order.getCartId() != null) cartCheckoutCleanupService.clearOrderedItems(order.getCartId());
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
    private void validateTransition(OrderStatus current, OrderStatus next, DeliveryMethod method) {
        boolean isPickup = method == DeliveryMethod.PICKUP;
        boolean allowed = switch (current) {
            // ── New admin-managed flow ────────────────────────────────────────
            case PENDING_PAYMENT  -> next == OrderStatus.PAID || next == OrderStatus.CANCELLED;
            case PAID             -> next == OrderStatus.PACKAGING || next == OrderStatus.CANCELLED;
            // Pickup orders branch to READY_FOR_PICKUP; delivery orders go to a courier.
            case PACKAGING        -> isPickup
                                       ? next == OrderStatus.READY_FOR_PICKUP || next == OrderStatus.CANCELLED
                                       : next == OrderStatus.IN_COURIER || next == OrderStatus.CANCELLED;
            case READY_FOR_PICKUP -> next == OrderStatus.DELIVERED || next == OrderStatus.CANCELLED || next == OrderStatus.FAILED;
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

    /**
     * The one place an order's status changes.
     *
     * <p>It exists because stock did not use to come back. createOrder decrements a store
     * listing's stock the moment the order is built, and six different places wrote the terminal
     * status that should have returned it — customer cancel, admin cancel, the delivery partner,
     * the courier sync, the payment-failed listener, and createOrder's own cancellation of a stale
     * prior order. Every one of them would have had to remember, and none of them did: three
     * declined cards on a five-unit variant left it at two with nothing sold.
     *
     * <p>So the release is not something a branch calls. It is something a status change does, and
     * a terminal branch added later gets it by writing its status the only way this class allows.
     */
    private void transitionTo(Order order, OrderStatus target) {
        OrderStatus from = order.getStatus();
        applyMilestoneTimestamp(order, target);
        order.setStatus(target);

        if (releasesStock(from, target)) {
            if (target == OrderStatus.CANCELLED && courierNotConfirmedStopped(order)) {
                // The one case where "cancelled" does not mean "the goods are ours again": a
                // dispatched order whose Quiqup job is not verifiably stopped. Putting these units
                // back would let the last one sell while a courier hands it to the cancelling
                // customer — an oversell on top of a withheld refund. The retry job releases them
                // (through applyCancellationSideEffects) the moment Quiqup confirms.
                log.error("[STOCK] Order {} cancelled but its Quiqup job {} is not confirmed stopped "
                        + "(status={}). Withholding {} — the units stay reserved until the courier "
                        + "is verifiably stopped.", order.getId(), order.getQuiqupOrderId(),
                        order.getQuiqupCancelStatus(), "the stock release");
            } else {
                // In THIS transaction, deliberately — see StockReservationService.releaseForOrder
                // for why splitting it off would both hang and double-count.
                stockReservationService.releaseForOrder(order);
            }
        } else if (target == OrderStatus.FAILED
                && order.getStockReservedAt() != null && order.getStockRestoredAt() == null) {
            log.error("[STOCK] Order {} failed at {} while still holding reserved stock. The parcel "
                    + "is out of our hands, so the units are NOT returned automatically — restock "
                    + "them when it physically comes back.", order.getId(), from);
        }
    }

    /**
     * True when this order was handed to Quiqup and the job is not verifiably stopped.
     *
     * <p>The evidence bar is the same one the refund uses: CONFIRMED (we checked) or
     * CONFIRMED_BY_PARTNER (they cancelled it themselves). PENDING, UNCONFIRMED, NEEDS_HUMAN,
     * REFUSED_TOO_LATE and null-with-a-job all mean a courier may still be moving.
     */
    private static boolean courierNotConfirmedStopped(Order order) {
        if (order.getQuiqupOrderId() == null || order.getQuiqupOrderId().isBlank()) {
            return false;   // never dispatched — nothing to stop
        }
        String status = order.getQuiqupCancelStatus();
        return !"CONFIRMED".equals(status) && !"CONFIRMED_BY_PARTNER".equals(status);
    }

    /**
     * Which terminal transitions put the units back on the shelf.
     *
     * <p>Cancellation always does: it is only allowed up to IN_COURIER, the business already
     * auto-refunds there, and the goods come back to us. A failed <em>payment</em> does too —
     * nothing ever left. A failed <em>delivery</em> does not: that parcel is with a courier or on
     * its way back, and inventing a sellable unit for it oversells the last one. Whether a human
     * refunds or redelivers is already an open question at that point.
     */
    @SuppressWarnings("deprecation") // PROCESSING kept for historical orders
    static boolean releasesStock(OrderStatus from, OrderStatus to) {
        if (to == OrderStatus.CANCELLED) {
            return true;
        }
        return to == OrderStatus.FAILED
                && (from == OrderStatus.PENDING_PAYMENT || from == OrderStatus.PROCESSING);
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
        res.setPickupStoreId(o.getPickupStoreId());
        res.setPickupStoreName(o.getPickupStoreName());
        res.setPickupStoreAddress(o.getPickupStoreAddress());
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

        // Resolve product display name + primary image per item, batched (one query each) so the
        // order-detail view can show real products, not just SKUs.
        Map<UUID, String> productNames = resolveEnglishProductNames(o.getItems());
        Map<UUID, String> productImages = resolvePrimaryImageUrls(o.getItems());
        res.setItems(o.getItems().stream().map(i -> toItemResponse(i, productNames, productImages)).toList());
        res.setTrackingHistory(o.getTrackingHistory().stream().map(this::toTrackingEventResponse).toList());

        // Customer account contact (email/phone) for admin/order-detail views. Looked up once per
        // single-order response — list endpoints use toSummaryResponse, so no N+1 here.
        populateCustomerContact(res, o);
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
        res.setPickupStoreId(base.getPickupStoreId());
        res.setPickupStoreName(base.getPickupStoreName());
        res.setPickupStoreAddress(base.getPickupStoreAddress());
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

        // Carry the customer contact resolved by toOrderResponse (no extra query).
        res.setCustomerFirstName(base.getCustomerFirstName());
        res.setCustomerLastName(base.getCustomerLastName());
        res.setCustomerEmail(base.getCustomerEmail());
        res.setCustomerPhone(base.getCustomerPhone());

        // Set additional admin-only fields
        if (o.getItems() != null && !o.getItems().isEmpty()) {
            res.setStoreId(o.getItems().get(0).getStoreId());
        }

        // Dispatch state is admin-only: whether the order reached Quiqup, and why it did not.
        res.setQuiqupOrderId(o.getQuiqupOrderId());
        res.setQuiqupStatus(o.getQuiqupStatus());
        res.setQuiqupDispatchedAt(o.getQuiqupDispatchedAt());
        res.setQuiqupDispatchError(o.getQuiqupDispatchError());
        // The cancel leg, so an admin can see WHY a cancelled order's refund is being held and
        // recover it — before this, the only cancel control needed a Quiqup job id shown nowhere.
        res.setQuiqupCancelStatus(o.getQuiqupCancelStatus());
        res.setQuiqupCancelConfirmedAt(o.getQuiqupCancelConfirmedAt());
        res.setQuiqupCancelError(o.getQuiqupCancelError());
        res.setCancelRefundInitiatedAt(o.getCancelRefundInitiatedAt());
        return res;
    }

    private OrderItemResponse toItemResponse(OrderItem i, Map<UUID, String> productNames,
                                             Map<UUID, String> productImages) {
        OrderItemResponse res = new OrderItemResponse();
        res.setId(i.getId());
        res.setProductId(i.getProductId());
        res.setVariantId(i.getVariantId());
        res.setStoreId(i.getStoreId());
        if (i.getProductId() != null) {
            res.setProductName(productNames.get(i.getProductId()));
            res.setProductImage(productImages.get(i.getProductId()));
        }
        // Fall back to the SKU when the product has no translated title, so the row is never nameless.
        if (res.getProductName() == null || res.getProductName().isBlank()) {
            res.setProductName(i.getVariantSku() != null && !i.getVariantSku().isBlank()
                    ? i.getVariantSku() : i.getProductSku());
        }
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
