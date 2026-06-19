package com.buyology.ecommerce.promo;

import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import com.buyology.ecommerce.promo.domain.DiscountType;
import com.buyology.ecommerce.promo.domain.PromoCode;
import com.buyology.ecommerce.promo.repository.PromoCodeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ensures the public WELCOME10 signup promo exists and is configured:
 * 10% off, usable once per customer, valid for 7 days from the customer's signup,
 * and NOT usable on laptops. The signup flow already emails the WELCOME10 code, so
 * this makes that code actually redeemable at checkout. Idempotent on every boot.
 */
@Component
public class WelcomePromoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WelcomePromoSeeder.class);

    private static final String CODE = "WELCOME10";
    private static final int VALID_DAYS_FROM_SIGNUP = 7;
    private static final BigDecimal DISCOUNT_PERCENT = new BigDecimal("10.00");

    private final PromoCodeRepository promoCodeRepo;
    private final ProductCategoryTranslationRepository categoryTranslationRepo;
    private final ObjectMapper objectMapper;

    public WelcomePromoSeeder(PromoCodeRepository promoCodeRepo,
                              ProductCategoryTranslationRepository categoryTranslationRepo,
                              ObjectMapper objectMapper) {
        this.promoCodeRepo = promoCodeRepo;
        this.categoryTranslationRepo = categoryTranslationRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            String excludedJson = resolveLaptopExclusionJson();

            PromoCode existing = promoCodeRepo.findByCodeIgnoreCase(CODE).orElse(null);
            if (existing == null) {
                PromoCode pc = new PromoCode();
                pc.setCode(CODE);
                pc.setDiscountType(DiscountType.PERCENTAGE);
                pc.setDiscountValue(DISCOUNT_PERCENT);
                pc.setMaxUsesPerCustomer(1);   // one use per customer
                pc.setMaxUsesTotal(null);      // unlimited globally
                pc.setValidDaysFromSignup(VALID_DAYS_FROM_SIGNUP);
                pc.setExcludedCategoryIds(excludedJson);
                pc.setActive(true);
                pc.setDescription("Welcome bonus: 10% off your first order — valid 7 days, excludes laptops.");
                promoCodeRepo.save(pc);
                log.info("[PROMO] Seeded {} (excludedCategories={})", CODE, excludedJson);
                return;
            }

            // Bring an existing row up to spec without clobbering admin tweaks unnecessarily.
            boolean changed = false;
            if (existing.getValidDaysFromSignup() == null) {
                existing.setValidDaysFromSignup(VALID_DAYS_FROM_SIGNUP);
                changed = true;
            }
            if (existing.getExcludedCategoryIds() == null && excludedJson != null) {
                existing.setExcludedCategoryIds(excludedJson);
                changed = true;
            }
            if (existing.getMaxUsesPerCustomer() == null) {
                existing.setMaxUsesPerCustomer(1);
                changed = true;
            }
            if (changed) {
                promoCodeRepo.save(existing);
                log.info("[PROMO] Updated {} config (validDays/excludedCategories/maxUsesPerCustomer)", CODE);
            }
        } catch (Exception e) {
            // Never block app startup on a seed failure.
            log.warn("[PROMO] WELCOME10 seeding skipped: {}", e.getMessage());
        }
    }

    /** JSON array of laptop category ids (EN names containing "laptop"), or null if none found. */
    private String resolveLaptopExclusionJson() {
        try {
            List<UUID> laptopCategoryIds = categoryTranslationRepo.findCategoryIdsByNameContaining("EN", "laptop");
            if (laptopCategoryIds == null || laptopCategoryIds.isEmpty()) {
                log.warn("[PROMO] No 'laptop' category found — WELCOME10 created without a laptop exclusion");
                return null;
            }
            return objectMapper.writeValueAsString(laptopCategoryIds);
        } catch (Exception e) {
            log.warn("[PROMO] Could not resolve laptop categories: {}", e.getMessage());
            return null;
        }
    }
}
