package com.buyology.ecommerce.b2b.quote.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.b2b.quote.domain.B2bQuote;
import com.buyology.ecommerce.b2b.quote.domain.B2bQuoteItem;
import com.buyology.ecommerce.b2b.quote.domain.B2bQuoteStatus;
import com.buyology.ecommerce.b2b.quote.dto.*;
import com.buyology.ecommerce.b2b.quote.repository.B2bQuoteItemRepository;
import com.buyology.ecommerce.b2b.quote.repository.B2bQuoteRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.repository.B2bMembershipRepository;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.payment.dto.InitiatePaymentRequest;
import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;
import com.buyology.ecommerce.payment.service.PaymentService;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * B2B Request-for-Quote service.
 *
 * Ownership model: the DRAFT quote is the member's B2B cart, keyed on the caller's
 * auth_credentials.id (sub) — mirroring the V17 one-ACTIVE-cart-per-credential rule
 * (enforced here by the partial unique index uq_b2b_quotes_draft_per_cred). Only members
 * with an ACTIVE {@link B2bMembership} may create/mutate a cart or submit a quote.
 */
@Service
public class B2bQuoteService {

    private static final Logger log = LoggerFactory.getLogger(B2bQuoteService.class);

    /** RFQ rule: every B2B product line must be at least this quantity. */
    public static final int B2B_MIN_QTY_PER_LINE = 5;

    /** In-app notification recipients for quote events: everyone who can price/action quotes. */
    private static final Set<String> QUOTE_ADMIN_ROLES = Set.of("PROCUREMENT", "SUPERADMIN");

    private final B2bQuoteRepository quoteRepo;
    private final B2bQuoteItemRepository itemRepo;
    private final B2bMembershipRepository membershipRepo;
    private final AuthCredentialRepository authCredentialRepository;
    private final StoreProductRepository storeProductRepo;
    private final StoreProductVariantRepository storeProductVariantRepo;
    private final ProductTranslationRepository productTranslationRepository;
    private final OrderRepository orderRepo;
    private final UserAddressRepository addressRepo;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final UserRoleRepository userRoleRepository;

    @Value("${app.admin-email:firdovsirz@gmail.com}")
    private String procurementEmail;

    @Value("${app.web-base-url:https://buyology.online}")
    private String webBaseUrl;

    public B2bQuoteService(B2bQuoteRepository quoteRepo,
                           B2bQuoteItemRepository itemRepo,
                           B2bMembershipRepository membershipRepo,
                           AuthCredentialRepository authCredentialRepository,
                           StoreProductRepository storeProductRepo,
                           StoreProductVariantRepository storeProductVariantRepo,
                           ProductTranslationRepository productTranslationRepository,
                           OrderRepository orderRepo,
                           UserAddressRepository addressRepo,
                           PaymentService paymentService,
                           EmailService emailService,
                           PushNotificationService pushNotificationService,
                           UserRoleRepository userRoleRepository) {
        this.quoteRepo = quoteRepo;
        this.itemRepo = itemRepo;
        this.membershipRepo = membershipRepo;
        this.authCredentialRepository = authCredentialRepository;
        this.storeProductRepo = storeProductRepo;
        this.storeProductVariantRepo = storeProductVariantRepo;
        this.productTranslationRepository = productTranslationRepository;
        this.orderRepo = orderRepo;
        this.addressRepo = addressRepo;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.pushNotificationService = pushNotificationService;
        this.userRoleRepository = userRoleRepository;
    }

    // =========================================================================
    // Member — B2B cart (DRAFT quote)
    // =========================================================================

    /**
     * Get-or-create the caller's DRAFT quote (the B2B cart). The DRAFT row is only
     * persisted once the first item is added (its country/currency are derived from that
     * product); until then we return a transient, empty cart so the storefront has a stable
     * shape without writing a country-less row (country_code is NOT NULL).
     */
    @Transactional
    public B2bQuoteResponse getOrCreateCart(UUID userId) {
        B2bMembership membership = requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);
        B2bQuote cart = quoteRepo.findByCredentialIdAndStatus(credentialId, B2bQuoteStatus.DRAFT)
                .orElse(null);
        if (cart == null) {
            // Transient empty cart — not saved. Shows an empty B2B cart to the member.
            cart = new B2bQuote();
            cart.setCredentialId(credentialId);
            cart.setUserId(userId);
            cart.setMembershipId(membership.getId());
            cart.setStatus(B2bQuoteStatus.DRAFT);
            return B2bQuoteResponse.from(cart, List.of(), B2B_MIN_QTY_PER_LINE);
        }
        return toResponse(cart);
    }

    /** Add a line to the cart (or upsert quantity on an existing line). */
    @Transactional
    public B2bQuoteResponse addItem(UUID userId, AddQuoteItemRequest req) {
        B2bMembership membership = requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);

        int quantity = req.getQuantity() == null ? 0 : req.getQuantity();
        requireMinQty(quantity);

        StoreProduct sp = storeProductRepo.findById(req.getStoreProductId())
                .orElseThrow(() -> new NoSuchElementException("Store product not found: " + req.getStoreProductId()));
        validateB2bEligible(sp);

        // Optional variant must belong to this store-product.
        String variantSku = null;
        if (req.getVariantId() != null) {
            StoreProductVariant spv = storeProductVariantRepo
                    .findByStoreProduct_IdAndVariant_Id(sp.getId(), req.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variant is not available for this product"));
            variantSku = spv.getVariant() != null ? spv.getVariant().getSku() : null;
        }

        Country spCountry = sp.getStore() != null ? sp.getStore().getCountry() : null;
        String productCountry = spCountry != null ? spCountry.getCode() : null;
        String productCurrency = spCountry != null ? spCountry.getCurrency() : null;

        // Get-or-create the DRAFT, seeding its country/currency from the first product added.
        B2bQuote cart = quoteRepo.findByCredentialIdAndStatus(credentialId, B2bQuoteStatus.DRAFT)
                .orElse(null);
        if (cart == null) {
            cart = new B2bQuote();
            cart.setCredentialId(credentialId);
            cart.setUserId(userId);
            cart.setMembershipId(membership.getId());
            cart.setStatus(B2bQuoteStatus.DRAFT);
            cart.setCountryCode(productCountry);
            cart.setCurrency(productCurrency != null ? productCurrency : "AED");
            cart = quoteRepo.save(cart);
        }

        // The cart is scoped to a single B2B country — reject products from another region.
        String cartCountry = cart.getCountryCode();
        if (cartCountry != null && productCountry != null && !cartCountry.equalsIgnoreCase(productCountry)) {
            throw new IllegalArgumentException(
                    "This product is in a different country (" + productCountry + ") than your B2B cart (" + cartCountry + ").");
        }

        Product product = sp.getProduct();
        UUID productId = product.getId();

        // Upsert on the unique (quote, store_product, variant) line.
        B2bQuoteItem line = itemRepo
                .findByQuote_IdAndStoreProductIdAndVariantId(cart.getId(), sp.getId(), req.getVariantId())
                .orElse(null);
        if (line == null) {
            line = new B2bQuoteItem();
            line.setQuote(cart);
            line.setStoreProductId(sp.getId());
            line.setProductId(productId);
            line.setVariantId(req.getVariantId());
            line.setProductTitle(resolveTitle(productId));
            line.setSku(variantSku != null ? variantSku : product.getSku());
            line.setQuantity(quantity);
        } else {
            line.setQuantity(quantity);
        }
        itemRepo.save(line);

        return toResponse(reload(cart.getId()));
    }

    /** Change the quantity of an existing cart line. */
    @Transactional
    public B2bQuoteResponse updateItem(UUID userId, UUID itemId, UpdateQuoteItemRequest req) {
        requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);

        int quantity = req.getQuantity() == null ? 0 : req.getQuantity();
        requireMinQty(quantity);

        B2bQuoteItem line = itemRepo.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Quote line not found: " + itemId));
        B2bQuote cart = line.getQuote();
        requireDraftOwner(cart, credentialId);

        line.setQuantity(quantity);
        itemRepo.save(line);
        return toResponse(reload(cart.getId()));
    }

    /** Remove a line from the cart. */
    @Transactional
    public B2bQuoteResponse removeItem(UUID userId, UUID itemId) {
        requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);

        B2bQuoteItem line = itemRepo.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Quote line not found: " + itemId));
        B2bQuote cart = line.getQuote();
        requireDraftOwner(cart, credentialId);

        itemRepo.delete(line);
        return toResponse(reload(cart.getId()));
    }

    /** Submit the DRAFT cart as one quote request (DRAFT → SUBMITTED). */
    @Transactional
    public B2bQuoteResponse submit(UUID userId, SubmitQuoteRequest req) {
        B2bMembership membership = requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);

        B2bQuote cart = quoteRepo.findByCredentialIdAndStatus(credentialId, B2bQuoteStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("Your B2B cart is empty"));

        List<B2bQuoteItem> lines = itemRepo.findByQuote_IdOrderByCreatedAtAsc(cart.getId());
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot submit an empty quote request");
        }
        for (B2bQuoteItem line : lines) {
            if (line.getQuantity() == null || line.getQuantity() < B2B_MIN_QTY_PER_LINE) {
                throw new IllegalArgumentException(
                        "Every line must have at least " + B2B_MIN_QTY_PER_LINE + " units before you can submit.");
            }
        }

        cart.setStatus(B2bQuoteStatus.SUBMITTED);
        cart.setSubmittedAt(Instant.now());
        if (req != null && req.getMemberNote() != null) {
            cart.setMemberNote(req.getMemberNote());
        }
        cart = quoteRepo.save(cart);

        // Notify procurement by email (best-effort — never fails the submit).
        try {
            emailService.sendB2bQuoteSubmittedNotification(
                    procurementEmail,
                    membership.getCompanyName(),
                    cart.getId().toString(),
                    lines.size(),
                    webBaseUrl + "/procurement/quotes/" + cart.getId());
        } catch (Exception e) {
            log.warn("[B2B-QUOTE] procurement notification failed for quote {}: {}", cart.getId(), e.getMessage());
        }

        // Confirm receipt to the member by email (best-effort).
        try {
            emailService.sendB2bQuoteReceivedEmail(
                    resolveMemberEmail(cart.getUserId(), cart.getCredentialId()),
                    resolveMemberName(cart.getUserId()),
                    cart.getId().toString(),
                    lines.size(),
                    webBaseUrl + "/b2b/quotes/" + cart.getId());
        } catch (Exception e) {
            log.warn("[B2B-QUOTE] quote-received email failed for quote {}: {}", cart.getId(), e.getMessage());
        }

        // In-app notification for every PROCUREMENT/SUPERADMIN user (best-effort).
        notifyQuoteAdmins("B2B_QUOTE_SUBMITTED", "New B2B quote to price",
                (membership.getCompanyName() != null ? membership.getCompanyName() : "A B2B member")
                        + " submitted a quote request (" + lines.size() + " line"
                        + (lines.size() == 1 ? "" : "s") + ").",
                cart.getId());

        return toResponse(reload(cart.getId()));
    }

    // =========================================================================
    // Member — quote history / lifecycle
    // =========================================================================

    public List<B2bQuoteResponse> listMyQuotes(UUID userId) {
        UUID credentialId = resolveCredentialId(userId);
        return quoteRepo.findByCredentialIdOrderByCreatedAtDesc(credentialId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public B2bQuoteResponse getMyQuote(UUID userId, UUID quoteId) {
        UUID credentialId = resolveCredentialId(userId);
        B2bQuote quote = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
        requireOwner(quote, credentialId);
        return toResponse(quote);
    }

    /** Member accepts a QUOTED price before it expires (QUOTED → ACCEPTED). */
    @Transactional
    public B2bQuoteResponse accept(UUID userId, UUID quoteId) {
        requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);
        B2bQuote quote = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
        requireOwner(quote, credentialId);

        if (quote.getStatus() != B2bQuoteStatus.QUOTED) {
            throw new IllegalStateException("Only a quoted request can be accepted (current status: " + quote.getStatus() + ")");
        }
        // Expiry guard — if the validity window has lapsed, mark EXPIRED and refuse.
        if (quote.getValidUntil() != null && Instant.now().isAfter(quote.getValidUntil())) {
            quote.setStatus(B2bQuoteStatus.EXPIRED);
            quoteRepo.save(quote);
            throw new IllegalStateException("This quote has expired. Please request a new quote.");
        }

        quote.setStatus(B2bQuoteStatus.ACCEPTED);
        quote.setAcceptedAt(Instant.now());
        quote = quoteRepo.save(quote);

        // Confirm acceptance to the member by email (best-effort — never fails the accept).
        BigDecimal total = itemRepo.findByQuote_IdOrderByCreatedAtAsc(quote.getId()).stream()
                .map(B2bQuoteItem::quotedLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            emailService.sendB2bQuoteAcceptedEmail(
                    resolveMemberEmail(quote.getUserId(), quote.getCredentialId()),
                    resolveMemberName(quote.getUserId()),
                    quote.getId().toString(),
                    total.toPlainString(),
                    quote.getCurrency(),
                    webBaseUrl + "/b2b/quotes/" + quote.getId());
        } catch (Exception e) {
            log.warn("[B2B-QUOTE] quote-accepted email failed for quote {}: {}", quote.getId(), e.getMessage());
        }

        // Notify admins that a quote was accepted (best-effort).
        notifyQuoteAdmins("B2B_QUOTE_ACCEPTED", "B2B quote accepted",
                "A member accepted quote " + quote.getId() + " (" + quote.getCurrency() + " "
                        + total.toPlainString() + "). It can now be checked out.",
                quote.getId());

        return toResponse(quote);
    }

    /** Member cancels a DRAFT / SUBMITTED / QUOTED quote (→ CANCELLED). */
    @Transactional
    public B2bQuoteResponse cancel(UUID userId, UUID quoteId) {
        UUID credentialId = resolveCredentialId(userId);
        B2bQuote quote = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
        requireOwner(quote, credentialId);

        switch (quote.getStatus()) {
            case DRAFT, SUBMITTED, QUOTED -> {
                quote.setStatus(B2bQuoteStatus.CANCELLED);
                quoteRepo.save(quote);
            }
            default -> throw new IllegalStateException(
                    "A " + quote.getStatus() + " quote cannot be cancelled");
        }
        return toResponse(quote);
    }

    /**
     * Checkout an ACCEPTED quote (ACCEPTED → ORDERED).
     *
     * The consumer {@link com.buyology.ecommerce.order.service.OrderService#createOrder} is
     * tightly coupled to the persistent consumer Cart (it loads a Cart row, enforces
     * CHECKED_OUT status, converts CartItems, etc.), so instead of forcing a fake Cart through
     * it we build the Order records here directly — mirroring that flow — and then reuse the
     * existing {@link PaymentService#initiatePayment} for the Paymob integration. The order's
     * standard PENDING_PAYMENT → PAID transition is handled by the unchanged
     * {@code OrderService.onPaymentSucceeded} Path 1 (appOrderId present).
     *
     * Simplifications (documented deliberately):
     *  - orders.cart_id is NOT NULL, but a B2B quote has no Cart. We set cart_id = quote.id so
     *    the column is populated and traceable; no Cart row exists with that id, so the
     *    cart-clearing step on payment success is a harmless no-op.
     *  - Shipping fee is 0 for B2B orders (bulk pricing is negotiated into the quoted unit
     *    prices); no free-shipping-threshold logic is applied.
     *  - Pricing is authoritative from the quoted lines: subtotal = Σ(quotedUnitPrice · qty),
     *    total = subtotal. No promo codes.
     */
    @Transactional
    public PaymentInitiatedResponse checkout(UUID userId, UUID quoteId, CheckoutQuoteRequest req) {
        requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);
        B2bQuote quote = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
        requireOwner(quote, credentialId);

        if (quote.getStatus() != B2bQuoteStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Only an accepted quote can be checked out (current status: " + quote.getStatus() + ")");
        }
        // Accepted quotes past their validity window can no longer be ordered.
        if (quote.getValidUntil() != null && Instant.now().isAfter(quote.getValidUntil())) {
            quote.setStatus(B2bQuoteStatus.EXPIRED);
            quoteRepo.save(quote);
            throw new IllegalStateException("This quote has expired and can no longer be checked out.");
        }

        List<B2bQuoteItem> lines = itemRepo.findByQuote_IdOrderByCreatedAtAsc(quote.getId());
        if (lines.isEmpty()) {
            throw new IllegalStateException("This quote has no lines to order");
        }
        for (B2bQuoteItem line : lines) {
            if (line.getQuotedUnitPrice() == null) {
                throw new IllegalStateException("This quote is not fully priced and cannot be ordered");
            }
        }

        boolean isPickup = "PICKUP".equalsIgnoreCase(req.getDeliveryMethod());

        // ── Build the order directly (mirrors OrderService.createOrder) ──────────
        Order order = new Order();
        order.setUserId(userId);
        order.setAuthCredentialId(credentialId);
        // See javadoc: no Cart exists; use the quote id so the NOT NULL cart_id column is set.
        order.setCartId(quote.getId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCurrency(quote.getCurrency());
        order.setCountryCode(quote.getCountryCode());
        order.setCountry(quote.getCountryCode());
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);

        String billingName;
        String billingPhone;
        if (isPickup) {
            if (req.getPickupStoreId() == null) {
                throw new IllegalArgumentException("Please choose a store to pick up from.");
            }
            order.setDeliveryMethod(DeliveryMethod.PICKUP);
            order.setPickupStoreId(req.getPickupStoreId());
            billingName = "B2B Member";
            billingPhone = null;
        } else {
            if (req.getAddressId() == null) {
                throw new IllegalArgumentException("addressId is required for delivery");
            }
            UserAddress address = addressRepo.findById(req.getAddressId())
                    .orElseThrow(() -> new IllegalArgumentException("Address not found: " + req.getAddressId()));
            if (address.getUser() == null || !userId.equals(address.getUser().getId())) {
                throw new AccessDeniedException("Address does not belong to the authenticated user");
            }
            order.setDeliveryMethod(DeliveryMethod.REGULAR);
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
            billingName = ((address.getFirstName() == null ? "" : address.getFirstName())
                    + " " + (address.getLastName() == null ? "" : address.getLastName())).trim();
            billingPhone = address.getPhoneNumber();
        }

        // Pricing from the quoted lines (authoritative).
        BigDecimal subtotal = lines.stream()
                .map(B2bQuoteItem::quotedLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal);

        order = orderRepo.save(order);

        // Snapshot quote lines into order items (resolve store/supplier from the store-product).
        for (B2bQuoteItem line : lines) {
            StoreProduct sp = storeProductRepo.findById(line.getStoreProductId()).orElse(null);
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(line.getProductId());
            item.setVariantId(line.getVariantId());
            item.setProductSku(line.getSku());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getQuotedUnitPrice());
            item.setTotalPrice(line.quotedLineTotal());
            if (sp != null) {
                item.setStoreId(sp.getStore() != null ? sp.getStore().getId() : null);
                item.setSupplierId(sp.getProduct() != null ? sp.getProduct().getSupplierId() : null);
            }
            order.getItems().add(item);
        }
        order = orderRepo.save(order);

        // Link the order to the quote and mark it ORDERED.
        quote.setOrderId(order.getId());
        quote.setStatus(B2bQuoteStatus.ORDERED);
        quoteRepo.save(quote);

        // Reuse the existing payment initiation on the created order (order-first flow).
        InitiatePaymentRequest ip = new InitiatePaymentRequest();
        ip.setAppOrderId(order.getId());
        // initiatePayment binds the payer from the order and reconciles the amount; cartId is
        // only used by the cart-first branch, which we never enter here. Set it to the quote id
        // to satisfy any non-null expectations without pointing at a real Cart.
        ip.setCartId(quote.getId());
        ip.setAddressId(req.getAddressId());
        ip.setPickupStoreId(req.getPickupStoreId());
        ip.setDeliveryMethod(order.getDeliveryMethod().name());
        ip.setShippingFee(BigDecimal.ZERO);
        ip.setMethodType(req.getMethodType());
        ip.setAmount(order.getTotalAmount());
        ip.setCurrency(order.getCurrency());
        // customerId is bound to the order's auth_credential_id inside initiatePayment; provide
        // the credential id up front for consistency.
        ip.setCustomerId(credentialId);
        ip.setCustomerEmail(req.getCustomerEmail() != null ? req.getCustomerEmail() : resolveMemberEmail(userId, credentialId));
        ip.setCustomerPhone(billingPhone);
        ip.setBillingName(billingName != null && !billingName.isBlank() ? billingName : "B2B Member");
        ip.setRedirectionUrl(req.getRedirectionUrl());

        return paymentService.initiatePayment(ip);
    }

    // =========================================================================
    // Procurement
    // =========================================================================

    public List<B2bQuoteResponse> listForProcurement(B2bQuoteStatus status) {
        B2bQuoteStatus effective = status != null ? status : B2bQuoteStatus.SUBMITTED;
        return quoteRepo.findByStatusOrderByCreatedAtDesc(effective).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public B2bQuoteResponse getForProcurement(UUID quoteId) {
        B2bQuote quote = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
        return toResponse(quote);
    }

    /** Sidebar badge — count of quotes awaiting a price (SUBMITTED). */
    public long countSubmitted() {
        return quoteRepo.countByStatus(B2bQuoteStatus.SUBMITTED);
    }

    /** Procurement prices every line (SUBMITTED → QUOTED) and emails the member. */
    @Transactional
    public B2bQuoteResponse price(UUID adminCredentialId, UUID quoteId, PriceQuoteRequest req) {
        B2bQuote quote = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
        if (quote.getStatus() != B2bQuoteStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted quote can be priced (current status: " + quote.getStatus() + ")");
        }
        if (req.getValidUntil() == null || req.getValidUntil().isBefore(Instant.now())) {
            throw new IllegalArgumentException("validUntil must be a future date");
        }

        List<B2bQuoteItem> lines = itemRepo.findByQuote_IdOrderByCreatedAtAsc(quote.getId());
        java.util.Map<UUID, B2bQuoteItem> byId = lines.stream()
                .collect(Collectors.toMap(B2bQuoteItem::getId, i -> i));

        // Every line must be priced.
        for (PriceQuoteRequest.LinePrice lp : req.getItems()) {
            B2bQuoteItem line = byId.get(lp.getItemId());
            if (line == null) {
                throw new IllegalArgumentException("Line " + lp.getItemId() + " does not belong to this quote");
            }
            line.setQuotedUnitPrice(lp.getUnitPrice());
        }
        boolean allPriced = lines.stream().allMatch(i -> i.getQuotedUnitPrice() != null);
        if (!allPriced) {
            throw new IllegalArgumentException("Every line must be priced before the quote can be sent.");
        }
        itemRepo.saveAll(lines);

        quote.setStatus(B2bQuoteStatus.QUOTED);
        quote.setQuotedAt(Instant.now());
        quote.setQuotedBy(adminCredentialId);
        quote.setValidUntil(req.getValidUntil());
        if (req.getProcurementNote() != null) {
            quote.setProcurementNote(req.getProcurementNote());
        }
        quote = quoteRepo.save(quote);

        BigDecimal total = lines.stream()
                .map(B2bQuoteItem::quotedLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String memberEmail = resolveMemberEmail(quote.getUserId(), quote.getCredentialId());
        String memberName = resolveMemberName(quote.getUserId());
        try {
            emailService.sendB2bQuoteReadyEmail(
                    memberEmail, memberName, quote.getId().toString(),
                    total.toPlainString(), quote.getCurrency(),
                    quote.getValidUntil() != null ? quote.getValidUntil().toString() : null,
                    webBaseUrl + "/b2b/quotes/" + quote.getId());
        } catch (Exception e) {
            log.warn("[B2B-QUOTE] quote-ready email failed for quote {}: {}", quote.getId(), e.getMessage());
        }

        return toResponse(reload(quote.getId()));
    }

    /** Procurement declines the request (SUBMITTED → REJECTED) and emails the member. */
    @Transactional
    public B2bQuoteResponse reject(UUID quoteId, RejectQuoteRequest req) {
        B2bQuote quote = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
        if (quote.getStatus() != B2bQuoteStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted quote can be rejected (current status: " + quote.getStatus() + ")");
        }
        quote.setStatus(B2bQuoteStatus.REJECTED);
        quote.setProcurementNote(req.getReason());
        quote = quoteRepo.save(quote);

        try {
            emailService.sendB2bQuoteRejectedEmail(
                    resolveMemberEmail(quote.getUserId(), quote.getCredentialId()),
                    resolveMemberName(quote.getUserId()),
                    quote.getId().toString(),
                    req.getReason());
        } catch (Exception e) {
            log.warn("[B2B-QUOTE] quote-rejected email failed for quote {}: {}", quote.getId(), e.getMessage());
        }

        return toResponse(quote);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private B2bMembership requireActiveMembership(UUID userId) {
        UUID resolvedUserId = resolveUsersId(userId);
        B2bMembership membership = membershipRepo.findByUserId(resolvedUserId)
                .orElseThrow(() -> new AccessDeniedException("An active B2B membership is required for this action"));
        if (membership.getStatus() != B2bMembership.MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("Your B2B membership is not active (status: " + membership.getStatus() + ")");
        }
        return membership;
    }

    private void validateB2bEligible(StoreProduct sp) {
        if (!Boolean.TRUE.equals(sp.getIsActive()) || sp.getDeletedAt() != null) {
            throw new IllegalArgumentException("This product is not available");
        }
        if (!Boolean.TRUE.equals(sp.getB2bEnabled())) {
            throw new IllegalArgumentException("This product is not available for B2B ordering");
        }
        Store store = sp.getStore();
        Country country = store != null ? store.getCountry() : null;
        if (country == null || !Boolean.TRUE.equals(country.getB2bEnabled())) {
            throw new IllegalArgumentException("B2B ordering is not available in this region");
        }
    }

    private void requireMinQty(int quantity) {
        if (quantity < B2B_MIN_QTY_PER_LINE) {
            throw new IllegalArgumentException(
                    "Minimum quantity per B2B line is " + B2B_MIN_QTY_PER_LINE + ".");
        }
    }

    private void requireOwner(B2bQuote quote, UUID credentialId) {
        if (quote.getCredentialId() == null || !quote.getCredentialId().equals(credentialId)) {
            throw new AccessDeniedException("You are not allowed to access this quote");
        }
    }

    private void requireDraftOwner(B2bQuote quote, UUID credentialId) {
        requireOwner(quote, credentialId);
        if (quote.getStatus() != B2bQuoteStatus.DRAFT) {
            throw new IllegalStateException("The B2B cart can only be edited while it is a draft");
        }
    }

    private B2bQuote reload(UUID quoteId) {
        return quoteRepo.findById(quoteId)
                .orElseThrow(() -> new NoSuchElementException("Quote not found: " + quoteId));
    }

    private B2bQuoteResponse toResponse(B2bQuote quote) {
        List<B2bQuoteItem> lines = itemRepo.findByQuote_IdOrderByCreatedAtAsc(quote.getId());
        return B2bQuoteResponse.from(quote, lines, B2B_MIN_QTY_PER_LINE);
    }

    private String resolveTitle(UUID productId) {
        List<ProductTranslation> translations = productTranslationRepository.findByProductId(productId);
        if (translations == null || translations.isEmpty()) return null;
        return translations.stream()
                .filter(t -> "en".equalsIgnoreCase(t.getLanguage()))
                .map(ProductTranslation::getTitle)
                .findFirst()
                .orElseGet(() -> translations.get(0).getTitle());
    }

    /**
     * Resolve the caller's auth_credentials.id (sub) from the users.id principal. The JWT filter
     * sets the principal to users.id; the quote owner is keyed on the credential (sub), so we map
     * users.id → its LOCAL/first credential. Falls back to the principal itself if it already is
     * a credential id (defensive).
     */
    private UUID resolveCredentialId(UUID userId) {
        List<AuthCredentials> creds = authCredentialRepository.findByUserId(userId);
        if (!creds.isEmpty()) {
            return creds.stream()
                    .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                    .map(AuthCredentials::getId)
                    .findFirst()
                    .orElse(creds.get(0).getId());
        }
        // Maybe the principal already IS a credential id.
        return authCredentialRepository.findById(userId).map(AuthCredentials::getId).orElse(userId);
    }

    /** users.id from a principal that may already be users.id, or an auth_credentials.id. */
    private UUID resolveUsersId(UUID candidate) {
        if (candidate == null) return null;
        if (membershipRepo.existsByUserId(candidate)) return candidate;
        return authCredentialRepository.findById(candidate)
                .map(AuthCredentials::getUserId)
                .orElse(candidate);
    }

    /**
     * Write an in-app notification (a {@link com.buyology.ecommerce.notification.domain.NotificationHistory}
     * row + push) for every PROCUREMENT/SUPERADMIN user so it surfaces in the dashboard bell's
     * per-user {@code /api/v1/notifications} feed. Wholly best-effort — a notification failure must
     * never break the member's quote action.
     */
    private void notifyQuoteAdmins(String type, String title, String body, UUID quoteId) {
        try {
            List<UUID> adminUserIds = userRoleRepository.findUserIdsByRoleNameIn(QUOTE_ADMIN_ROLES);
            Map<String, String> data = Map.of("type", type, "quoteId", quoteId.toString());
            for (UUID adminUserId : adminUserIds) {
                try {
                    pushNotificationService.sendToUser(adminUserId, title, body, type, data);
                } catch (Exception e) {
                    log.warn("[B2B-QUOTE] admin notification ({}) failed for user {} quote {}: {}",
                            type, adminUserId, quoteId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[B2B-QUOTE] resolving admin notification recipients failed for quote {}: {}",
                    quoteId, e.getMessage());
        }
    }

    private String resolveMemberEmail(UUID userId, UUID credentialId) {
        if (credentialId != null) {
            String email = authCredentialRepository.findById(credentialId)
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);
            if (email != null) return email;
        }
        if (userId != null) {
            return authCredentialRepository.findByUserId(userId).stream()
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String resolveMemberName(UUID userId) {
        if (userId == null) return null;
        return membershipRepo.findByUserId(userId)
                .map(B2bMembership::getMemberName)
                .orElse(null);
    }
}
