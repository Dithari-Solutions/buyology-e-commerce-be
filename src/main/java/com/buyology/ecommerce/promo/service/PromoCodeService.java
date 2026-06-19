package com.buyology.ecommerce.promo.service;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.promo.domain.DiscountType;
import com.buyology.ecommerce.promo.domain.PromoCode;
import com.buyology.ecommerce.promo.domain.PromoCodeUsage;
import com.buyology.ecommerce.promo.domain.TokenRedemptionConfig;
import com.buyology.ecommerce.promo.dto.*;
import com.buyology.ecommerce.promo.repository.PromoCodeRepository;
import com.buyology.ecommerce.promo.repository.PromoCodeUsageRepository;
import com.buyology.ecommerce.promo.repository.TokenRedemptionConfigRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PromoCodeService {

    private static final Logger log = LoggerFactory.getLogger(PromoCodeService.class);

    private static final SecureRandom RNG = new SecureRandom();
    // Excludes ambiguous characters (0/O, 1/I) so codes are easy to read/type.
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final PromoCodeRepository promoCodeRepo;
    private final PromoCodeUsageRepository usageRepo;
    private final UserRepository userRepo;
    private final AuthCredentialRepository authCredentialRepo;
    private final PushNotificationService pushService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final TokenRedemptionConfigRepository tokenConfigRepo;
    private final UserProfilesRepository userProfilesRepo;
    private final ProductRepository productRepository;

    public PromoCodeService(PromoCodeRepository promoCodeRepo,
                            PromoCodeUsageRepository usageRepo,
                            UserRepository userRepo,
                            AuthCredentialRepository authCredentialRepo,
                            PushNotificationService pushService,
                            EmailService emailService,
                            ObjectMapper objectMapper,
                            TokenRedemptionConfigRepository tokenConfigRepo,
                            UserProfilesRepository userProfilesRepo,
                            ProductRepository productRepository) {
        this.promoCodeRepo = promoCodeRepo;
        this.usageRepo = usageRepo;
        this.userRepo = userRepo;
        this.authCredentialRepo = authCredentialRepo;
        this.pushService = pushService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.tokenConfigRepo = tokenConfigRepo;
        this.userProfilesRepo = userProfilesRepo;
        this.productRepository = productRepository;
    }

    // ── Customer: validate promo code ────────────────────────────────────────

    public ValidatePromoCodeResponse validateAndCalculate(String code, UUID userId,
                                                         BigDecimal orderAmount,
                                                         List<UUID> productIds) {
        Optional<PromoCode> opt = promoCodeRepo.findByCodeIgnoreCaseAndIsActiveTrue(code);
        if (opt.isEmpty()) {
            return ValidatePromoCodeResponse.invalid("Promo code not found or inactive");
        }
        PromoCode pc = opt.get();

        // Personal codes (token-redeemed or admin-issued) belong to one user only.
        if (pc.getTargetUserId() != null && !pc.getTargetUserId().equals(userId)) {
            return ValidatePromoCodeResponse.invalid("This promo code is not valid for your account");
        }

        if (pc.getExpiresAt() != null && pc.getExpiresAt().isBefore(Instant.now())) {
            return ValidatePromoCodeResponse.invalid("Promo code has expired");
        }

        // Per-user signup window (e.g. WELCOME10 is valid for 7 days after the customer signs up).
        if (pc.getValidDaysFromSignup() != null) {
            Users user = userRepo.findById(userId).orElse(null);
            if (user == null || user.getCreatedAt() == null) {
                return ValidatePromoCodeResponse.invalid("This promo code is not valid for your account");
            }
            Instant windowEnd = user.getCreatedAt().plus(pc.getValidDaysFromSignup(), ChronoUnit.DAYS);
            if (Instant.now().isAfter(windowEnd)) {
                return ValidatePromoCodeResponse.invalid(
                        "This welcome offer is only valid for " + pc.getValidDaysFromSignup() + " days after signup");
            }
        }

        if (pc.getMinimumOrderAmount() != null && orderAmount.compareTo(pc.getMinimumOrderAmount()) < 0) {
            return ValidatePromoCodeResponse.invalid(
                    "Minimum order amount of " + pc.getMinimumOrderAmount() + " required");
        }

        if (pc.getMaxUsesTotal() != null && usageRepo.countByPromoCode(pc) >= pc.getMaxUsesTotal()) {
            return ValidatePromoCodeResponse.invalid("Promo code has reached its maximum usage limit");
        }

        if (pc.getMaxUsesPerCustomer() != null && usageRepo.countByPromoCodeAndUserId(pc, userId) >= pc.getMaxUsesPerCustomer()) {
            return ValidatePromoCodeResponse.invalid("You have already used this promo code the maximum number of times");
        }

        if (pc.getApplicableProductIds() != null && productIds != null && !productIds.isEmpty()) {
            List<UUID> allowedProducts = parseUuidList(pc.getApplicableProductIds());
            if (!allowedProducts.isEmpty()) {
                boolean hasMatch = productIds.stream().anyMatch(allowedProducts::contains);
                if (!hasMatch) {
                    return ValidatePromoCodeResponse.invalid("Promo code is not applicable to the selected products");
                }
            }
        }

        // Excluded categories (e.g. WELCOME10 cannot be used on laptops): reject the whole
        // order if any item belongs to an excluded category.
        if (pc.getExcludedCategoryIds() != null && productIds != null && !productIds.isEmpty()) {
            List<UUID> excludedCategories = parseUuidList(pc.getExcludedCategoryIds());
            if (!excludedCategories.isEmpty()) {
                List<UUID> orderCategoryIds = productRepository.findCategoryIdsByProductIds(productIds);
                boolean hasExcluded = orderCategoryIds.stream().anyMatch(excludedCategories::contains);
                if (hasExcluded) {
                    return ValidatePromoCodeResponse.invalid(
                            "This promo code can't be used on some items in your order (e.g. laptops)");
                }
            }
        }

        BigDecimal discount = calculateDiscount(pc, orderAmount);
        return ValidatePromoCodeResponse.valid(pc.getId(), discount, pc.getDiscountType(), pc.getDiscountValue());
    }

    @Transactional
    public void recordUsage(UUID promoCodeId, UUID orderId, UUID userId, BigDecimal discountApplied) {
        // A missing promo must fail the transaction, not silently no-op while the
        // order keeps the discount.
        PromoCode pc = promoCodeRepo.findById(promoCodeId)
                .orElseThrow(() -> new NoSuchElementException("Promo code not found: " + promoCodeId));

        PromoCodeUsage usage = new PromoCodeUsage();
        usage.setPromoCode(pc);
        usage.setOrderId(orderId);
        usage.setUserId(userId);
        usage.setDiscountApplied(discountApplied);
        try {
            usageRepo.saveAndFlush(usage);
        } catch (DataIntegrityViolationException e) {
            // Unique constraint (promo_code_id, user_id, order_id) tripped by a
            // concurrent/retried checkout — the redemption was already recorded.
            throw new IllegalStateException("Promo code already used", e);
        }
    }

    // ── Admin: create promo code ─────────────────────────────────────────────

    @Transactional
    public PromoCodeResponse createPromoCode(CreatePromoCodeRequest req) {
        validateDiscount(req.getDiscountType(), req.getDiscountValue());
        if (promoCodeRepo.existsByCodeIgnoreCase(req.getCode())) {
            throw new IllegalArgumentException("Promo code already exists: " + req.getCode());
        }

        PromoCode pc = new PromoCode();
        pc.setCode(req.getCode().toUpperCase());
        pc.setDiscountType(req.getDiscountType());
        pc.setDiscountValue(req.getDiscountValue());
        pc.setMinimumOrderAmount(req.getMinimumOrderAmount());
        pc.setMaxUsesTotal(req.getMaxUsesTotal());
        pc.setMaxUsesPerCustomer(req.getMaxUsesPerCustomer());
        pc.setExpiresAt(req.getExpiresAt());
        pc.setValidDaysFromSignup(req.getValidDaysFromSignup());
        pc.setDescription(req.getDescription());
        pc.setActive(true);

        if (req.getApplicableProductIds() != null && !req.getApplicableProductIds().isEmpty()) {
            pc.setApplicableProductIds(toJson(req.getApplicableProductIds()));
        }
        if (req.getApplicableCategoryIds() != null && !req.getApplicableCategoryIds().isEmpty()) {
            pc.setApplicableCategoryIds(toJson(req.getApplicableCategoryIds()));
        }
        if (req.getExcludedCategoryIds() != null && !req.getExcludedCategoryIds().isEmpty()) {
            pc.setExcludedCategoryIds(toJson(req.getExcludedCategoryIds()));
        }

        return toResponse(promoCodeRepo.save(pc));
    }

    public List<PromoCodeResponse> listAll() {
        return promoCodeRepo.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Admin: who redeemed this promo, on which order, for how much (newest first). */
    public List<PromoUsageResponse> listUsages(UUID promoCodeId) {
        return usageRepo.findByPromoCode_IdOrderByUsedAtDesc(promoCodeId).stream()
                .map(u -> {
                    String name = userRepo.findById(u.getUserId())
                            .map(usr -> ((usr.getFirstName() == null ? "" : usr.getFirstName()) + " "
                                    + (usr.getLastName() == null ? "" : usr.getLastName())).trim())
                            .filter(s -> !s.isBlank())
                            .orElse(null);
                    String email = authCredentialRepo.findByUserId(u.getUserId()).stream()
                            .findFirst()
                            .map(c -> c.getEmail())
                            .orElse(null);
                    return new PromoUsageResponse(u.getUserId(), name, email,
                            u.getOrderId(), u.getDiscountApplied(), u.getUsedAt());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public PromoCodeResponse updatePromoCode(UUID id, CreatePromoCodeRequest req) {
        validateDiscount(req.getDiscountType(), req.getDiscountValue());
        PromoCode pc = promoCodeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Promo code not found: " + id));
        pc.setDiscountType(req.getDiscountType());
        pc.setDiscountValue(req.getDiscountValue());
        pc.setMinimumOrderAmount(req.getMinimumOrderAmount());
        pc.setMaxUsesTotal(req.getMaxUsesTotal());
        pc.setMaxUsesPerCustomer(req.getMaxUsesPerCustomer());
        pc.setExpiresAt(req.getExpiresAt());
        pc.setDescription(req.getDescription());
        return toResponse(promoCodeRepo.save(pc));
    }

    @Transactional
    public void deactivate(UUID id) {
        promoCodeRepo.findById(id).ifPresent(pc -> {
            pc.setActive(false);
            promoCodeRepo.save(pc);
        });
    }

    @Async
    public void sendPromoToCustomers(UUID promoCodeId, SendPromoRequest req) {
        PromoCode pc = promoCodeRepo.findById(promoCodeId)
                .orElseThrow(() -> new NoSuchElementException("Promo code not found: " + promoCodeId));

        String title = "Exclusive offer for you!";
        String body = "Use code " + pc.getCode() + " to get "
                + (pc.getDiscountType() == DiscountType.PERCENTAGE
                ? pc.getDiscountValue() + "% off"
                : pc.getDiscountValue() + " off")
                + " your next order!";

        Map<String, String> data = Map.of("promoCode", pc.getCode(), "type", "PROMO");

        int sent = 0;
        if (req.getTargetUserIds() != null && !req.getTargetUserIds().isEmpty()) {
            // Batch fetch instead of N findById calls.
            List<Users> targets = userRepo.findAllById(req.getTargetUserIds());
            for (Users user : targets) {
                sendPromoToUser(user, pc, title, body, data, req);
            }
            sent = targets.size();
        } else {
            // Broadcast to all customers, paged so we never load the whole table at once.
            int pageNum = 0;
            org.springframework.data.domain.Page<Users> page;
            do {
                page = userRepo.findByUserType(Users.UserType.CUSTOMER,
                        org.springframework.data.domain.PageRequest.of(pageNum, 500));
                for (Users user : page.getContent()) {
                    sendPromoToUser(user, pc, title, body, data, req);
                }
                sent += page.getNumberOfElements();
                pageNum++;
            } while (page.hasNext());
        }
        log.info("Promo '{}' sent to {} users", pc.getCode(), sent);
    }

    private void sendPromoToUser(Users user, PromoCode pc, String title, String body,
                                 Map<String, String> data, SendPromoRequest req) {
        try {
            if (req.isSendPush()) {
                pushService.sendToUser(user.getId(), title, body, data);
            }
            if (req.isSendEmail()) {
                String email = authCredentialRepo.findByUserId(user.getId()).stream()
                        .map(c -> c.getEmail())
                        .filter(e -> e != null && !e.isBlank())
                        .findFirst().orElse(null);
                if (email != null) {
                    emailService.sendPromoCodeEmail(email, pc.getCode(), body);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send promo to user {}: {}", user.getId(), e.getMessage());
        }
    }

    // ── Admin: issue a coupon to ONE user + email only them ──────────────────

    @Transactional
    public PromoCodeResponse issuePersonalCode(IssuePersonalCodeRequest req) {
        validateDiscount(req.getDiscountType(), req.getDiscountValue());
        Users user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + req.getUserId()));

        int maxUses = (req.getMaxUses() != null && req.getMaxUses() > 0) ? req.getMaxUses() : 1;

        PromoCode pc = new PromoCode();
        pc.setCode(generateUniqueCode("VIP-"));
        pc.setDiscountType(req.getDiscountType());
        pc.setDiscountValue(req.getDiscountValue());
        pc.setMinimumOrderAmount(req.getMinimumOrderAmount());
        pc.setMaxUsesTotal(maxUses);
        pc.setMaxUsesPerCustomer(maxUses);
        pc.setTargetUserId(user.getId());
        pc.setExpiresAt(req.getValidityDays() != null && req.getValidityDays() > 0
                ? Instant.now().plus(req.getValidityDays(), ChronoUnit.DAYS) : null);
        pc.setDescription(req.getDescription() != null && !req.getDescription().isBlank()
                ? req.getDescription() : "Personal offer");
        pc.setActive(true);
        PromoCode saved = promoCodeRepo.save(pc);

        // Notify only this user.
        String body = "Use code " + saved.getCode() + " to get " + offerText(saved) + "!";
        try {
            String email = authCredentialRepo.findByUserId(user.getId()).stream()
                    .map(c -> c.getEmail())
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst().orElse(null);
            if (req.isSendEmail() && email != null) {
                emailService.sendPromoCodeEmail(email, saved.getCode(), body);
            }
            if (req.isSendPush()) {
                pushService.sendToUser(user.getId(), "A gift for you 🎁", body,
                        Map.of("promoCode", saved.getCode(), "type", "PROMO"));
            }
        } catch (Exception e) {
            log.warn("Failed to notify user {} of personal code: {}", user.getId(), e.getMessage());
        }
        return toResponse(saved);
    }

    // ── Customer: redeem tokens for a personal coupon ────────────────────────

    public TokenRedemptionInfoResponse getRedemptionInfo(UUID userId) {
        TokenRedemptionConfig cfg = getOrCreateConfig();
        Integer remaining = cfg.getMaxRedemptionsPerCustomer() == null ? null
                : Math.max(0, cfg.getMaxRedemptionsPerCustomer()
                        - (int) promoCodeRepo.countByTargetUserIdAndRedeemedFromTokensNotNull(userId));
        return new TokenRedemptionInfoResponse(cfg.isEnabled(), cfg.getTokenCost(),
                cfg.getDiscountType(), cfg.getDiscountValue(), remaining);
    }

    @Transactional
    public RedeemTokensResponse redeemTokens(UUID userId) {
        TokenRedemptionConfig cfg = getOrCreateConfig();
        if (!cfg.isEnabled()) {
            throw new IllegalStateException("Token redemption is currently unavailable");
        }

        if (cfg.getMaxRedemptionsPerCustomer() != null
                && promoCodeRepo.countByTargetUserIdAndRedeemedFromTokensNotNull(userId)
                    >= cfg.getMaxRedemptionsPerCustomer()) {
            throw new IllegalStateException("You have reached the maximum number of token redemptions");
        }

        // Atomic conditional debit — guards against concurrent double-spend.
        int debited = userProfilesRepo.debitTokens(userId, cfg.getTokenCost());
        if (debited == 0) {
            throw new IllegalStateException("You don't have enough tokens to redeem");
        }

        PromoCode pc = new PromoCode();
        pc.setCode(generateUniqueCode("RWD-"));
        pc.setDiscountType(cfg.getDiscountType());
        pc.setDiscountValue(cfg.getDiscountValue());
        pc.setMinimumOrderAmount(cfg.getMinimumOrderAmount());
        pc.setMaxUsesTotal(cfg.getMaxUsesPerCoupon());
        pc.setMaxUsesPerCustomer(cfg.getMaxUsesPerCoupon());
        pc.setTargetUserId(userId);
        pc.setRedeemedFromTokens(cfg.getTokenCost());
        pc.setExpiresAt(cfg.getCouponValidityDays() > 0
                ? Instant.now().plus(cfg.getCouponValidityDays(), ChronoUnit.DAYS) : null);
        pc.setDescription("Redeemed for " + cfg.getTokenCost() + " tokens");
        pc.setActive(true);
        promoCodeRepo.save(pc);

        int remainingTokens = userProfilesRepo.findByUserId(userId)
                .map(UserProfiles::getTokens).orElse(0);
        Integer redemptionsRemaining = cfg.getMaxRedemptionsPerCustomer() == null ? null
                : Math.max(0, cfg.getMaxRedemptionsPerCustomer()
                        - (int) promoCodeRepo.countByTargetUserIdAndRedeemedFromTokensNotNull(userId));

        // Best-effort email of the freshly minted code.
        try {
            String email = authCredentialRepo.findByUserId(userId).stream()
                    .map(c -> c.getEmail())
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst().orElse(null);
            if (email != null) {
                emailService.sendPromoCodeEmail(email, pc.getCode(), offerText(pc));
            }
        } catch (Exception e) {
            log.warn("Failed to email redeemed code to user {}: {}", userId, e.getMessage());
        }

        return new RedeemTokensResponse(pc.getCode(), pc.getDiscountType(), pc.getDiscountValue(),
                pc.getMinimumOrderAmount(), pc.getExpiresAt(), remainingTokens, redemptionsRemaining);
    }

    // ── Admin: token-redemption configuration ────────────────────────────────

    @Transactional
    public TokenRedemptionConfig getOrCreateConfig() {
        return tokenConfigRepo.findTopByOrderByUpdatedAtAsc()
                .orElseGet(() -> tokenConfigRepo.save(new TokenRedemptionConfig()));
    }

    @Transactional
    public TokenRedemptionConfigDto updateConfig(TokenRedemptionConfigDto dto) {
        validateDiscount(dto.getDiscountType(), dto.getDiscountValue());
        if (dto.getTokenCost() <= 0) {
            throw new IllegalArgumentException("Token cost must be greater than 0");
        }
        if (dto.getMaxUsesPerCoupon() <= 0) {
            throw new IllegalArgumentException("Max uses per coupon must be at least 1");
        }
        if (dto.getCouponValidityDays() < 0) {
            throw new IllegalArgumentException("Coupon validity days cannot be negative");
        }
        if (dto.getMaxRedemptionsPerCustomer() != null && dto.getMaxRedemptionsPerCustomer() < 0) {
            throw new IllegalArgumentException("Max redemptions per customer cannot be negative");
        }
        TokenRedemptionConfig cfg = getOrCreateConfig();
        cfg.setEnabled(dto.isEnabled());
        cfg.setTokenCost(dto.getTokenCost());
        cfg.setDiscountType(dto.getDiscountType());
        cfg.setDiscountValue(dto.getDiscountValue());
        cfg.setMinimumOrderAmount(dto.getMinimumOrderAmount());
        cfg.setCouponValidityDays(dto.getCouponValidityDays());
        cfg.setMaxUsesPerCoupon(dto.getMaxUsesPerCoupon());
        cfg.setMaxRedemptionsPerCustomer(dto.getMaxRedemptionsPerCustomer());
        return TokenRedemptionConfigDto.from(tokenConfigRepo.save(cfg));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String offerText(PromoCode pc) {
        return pc.getDiscountType() == DiscountType.PERCENTAGE
                ? pc.getDiscountValue().stripTrailingZeros().toPlainString() + "% off your next order"
                : pc.getDiscountValue().stripTrailingZeros().toPlainString() + " off your next order";
    }

    private String generateUniqueCode(String prefix) {
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET.charAt(RNG.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!promoCodeRepo.existsByCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique promo code, please retry");
    }

    private void validateDiscount(DiscountType type, BigDecimal value) {
        // Bean Validation already rejects null / <= 0 on the DTO (@NotNull, @DecimalMin("0.01")).
        // The percentage upper bound is type-dependent, so it is enforced here.
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than 0");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
    }

    private BigDecimal calculateDiscount(PromoCode pc, BigDecimal orderAmount) {
        BigDecimal discount;
        if (pc.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = orderAmount.multiply(pc.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = pc.getDiscountValue();
        }
        // Clamp to [0, orderAmount]: a discount can never be negative nor exceed the order total.
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return discount.min(orderAmount);
    }

    private PromoCodeResponse toResponse(PromoCode pc) {
        PromoCodeResponse r = new PromoCodeResponse();
        r.setId(pc.getId());
        r.setCode(pc.getCode());
        r.setDiscountType(pc.getDiscountType());
        r.setDiscountValue(pc.getDiscountValue());
        r.setMinimumOrderAmount(pc.getMinimumOrderAmount());
        r.setMaxUsesTotal(pc.getMaxUsesTotal());
        r.setMaxUsesPerCustomer(pc.getMaxUsesPerCustomer());
        r.setTotalUsed(usageRepo.countByPromoCode(pc));
        r.setExpiresAt(pc.getExpiresAt());
        r.setTargetUserId(pc.getTargetUserId());
        r.setActive(pc.isActive());
        r.setDescription(pc.getDescription());
        r.setCreatedAt(pc.getCreatedAt());
        return r;
    }

    private List<UUID> parseUuidList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
