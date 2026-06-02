package com.buyology.ecommerce.promo.service;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.promo.domain.DiscountType;
import com.buyology.ecommerce.promo.domain.PromoCode;
import com.buyology.ecommerce.promo.domain.PromoCodeUsage;
import com.buyology.ecommerce.promo.dto.*;
import com.buyology.ecommerce.promo.repository.PromoCodeRepository;
import com.buyology.ecommerce.promo.repository.PromoCodeUsageRepository;
import com.buyology.ecommerce.user.domain.Users;
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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PromoCodeService {

    private static final Logger log = LoggerFactory.getLogger(PromoCodeService.class);

    private final PromoCodeRepository promoCodeRepo;
    private final PromoCodeUsageRepository usageRepo;
    private final UserRepository userRepo;
    private final AuthCredentialRepository authCredentialRepo;
    private final PushNotificationService pushService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public PromoCodeService(PromoCodeRepository promoCodeRepo,
                            PromoCodeUsageRepository usageRepo,
                            UserRepository userRepo,
                            AuthCredentialRepository authCredentialRepo,
                            PushNotificationService pushService,
                            EmailService emailService,
                            ObjectMapper objectMapper) {
        this.promoCodeRepo = promoCodeRepo;
        this.usageRepo = usageRepo;
        this.userRepo = userRepo;
        this.authCredentialRepo = authCredentialRepo;
        this.pushService = pushService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
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

        if (pc.getExpiresAt() != null && pc.getExpiresAt().isBefore(Instant.now())) {
            return ValidatePromoCodeResponse.invalid("Promo code has expired");
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
        pc.setDescription(req.getDescription());
        pc.setActive(true);

        if (req.getApplicableProductIds() != null && !req.getApplicableProductIds().isEmpty()) {
            pc.setApplicableProductIds(toJson(req.getApplicableProductIds()));
        }
        if (req.getApplicableCategoryIds() != null && !req.getApplicableCategoryIds().isEmpty()) {
            pc.setApplicableCategoryIds(toJson(req.getApplicableCategoryIds()));
        }

        return toResponse(promoCodeRepo.save(pc));
    }

    public List<PromoCodeResponse> listAll() {
        return promoCodeRepo.findAll().stream()
                .map(this::toResponse)
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

        List<Users> targets;
        if (req.getTargetUserIds() != null && !req.getTargetUserIds().isEmpty()) {
            targets = req.getTargetUserIds().stream()
                    .map(id -> userRepo.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            targets = userRepo.findAll().stream()
                    .filter(u -> u.getUserType() == Users.UserType.CUSTOMER)
                    .collect(Collectors.toList());
        }

        String title = "Exclusive offer for you!";
        String body = "Use code " + pc.getCode() + " to get "
                + (pc.getDiscountType() == DiscountType.PERCENTAGE
                ? pc.getDiscountValue() + "% off"
                : pc.getDiscountValue() + " off")
                + " your next order!";

        Map<String, String> data = Map.of("promoCode", pc.getCode(), "type", "PROMO");

        for (Users user : targets) {
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
        log.info("Promo '{}' sent to {} users", pc.getCode(), targets.size());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

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
