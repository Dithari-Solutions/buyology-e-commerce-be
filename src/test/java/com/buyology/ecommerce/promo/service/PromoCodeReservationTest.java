package com.buyology.ecommerce.promo.service;

import com.buyology.ecommerce.promo.domain.PromoCode;
import com.buyology.ecommerce.promo.domain.PromoCodeUsage;
import com.buyology.ecommerce.promo.repository.PromoCodeRepository;
import com.buyology.ecommerce.promo.repository.PromoCodeUsageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the moment a promo code stops being available to the next customer.
 *
 * <p>It used to be the moment an order was <em>paid</em>, and every limit check counted paid
 * redemptions. So a code sat there looking untouched for as long as an order went unpaid: place ten
 * orders carrying the same single-use code, pay all ten, and all ten keep the discount. Nothing
 * rejected it — the unique constraint identifies a redemption by (promo, customer, order), and
 * those were ten different orders. On a personal token-redemption code, that is one code the
 * customer earned, spent as many times as they cared to check out.
 *
 * <p>So the tests below are mostly about counting: what a limit check has to see, and when the code
 * has to come back because the order holding it will never be paid.
 */
class PromoCodeReservationTest {

    private static final UUID PROMO = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID ORDER = UUID.fromString("3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607");
    private static final UUID OTHER_ORDER = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final UUID CUSTOMER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final BigDecimal DISCOUNT = new BigDecimal("500.00");

    private final PromoCodeRepository promoRepo = mock(PromoCodeRepository.class);
    private final PromoCodeUsageRepository usageRepo = mock(PromoCodeUsageRepository.class);

    private final PromoCodeService service = new PromoCodeService(
            promoRepo, usageRepo, null, null, null, null, null, null, null, null);

    private PromoCode singleUseCode() {
        PromoCode pc = new PromoCode();
        pc.setCode("WELCOME500");
        pc.setMaxUsesTotal(1);
        when(promoRepo.findByIdForUpdate(PROMO)).thenReturn(Optional.of(pc));
        when(promoRepo.findById(PROMO)).thenReturn(Optional.of(pc));
        return pc;
    }

    private PromoCodeUsage savedUsage() {
        ArgumentCaptor<PromoCodeUsage> captor = ArgumentCaptor.forClass(PromoCodeUsage.class);
        verify(usageRepo).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ── The bug ──────────────────────────────────────────────────────────────

    @Test
    void aSecondOrderCannotClaimACodeAnUnpaidOrderIsAlreadyHolding() {
        // The first order reserved it and has not been paid for. Under the old rules this count was
        // zero — reservations did not exist — and this order sailed through with the same discount.
        PromoCode pc = singleUseCode();
        when(usageRepo.countByPromoCode(pc)).thenReturn(1L);

        assertFalse(service.reserveUsage(PROMO, OTHER_ORDER, CUSTOMER, DISCOUNT));

        verify(usageRepo, never()).saveAndFlush(any());
    }

    @Test
    void claimsTheCodeWhenItIsStillAvailable() {
        PromoCode pc = singleUseCode();
        when(usageRepo.countByPromoCode(pc)).thenReturn(0L);

        assertTrue(service.reserveUsage(PROMO, ORDER, CUSTOMER, DISCOUNT));

        PromoCodeUsage row = savedUsage();
        assertEquals(PromoCodeUsage.Status.RESERVED, row.getStatus(),
                "a claim on an unpaid order is a hold, not a redemption — it has to be releasable");
        assertEquals(ORDER, row.getOrderId());
        assertEquals(CUSTOMER, row.getUserId());
        assertEquals(DISCOUNT, row.getDiscountApplied());
    }

    @Test
    void takesTheWriteLockBeforeCounting() {
        // Two checkouts racing for the last use of a code both read "one left" and both take it
        // unless the read-check-insert is serialised. findByIdForUpdate is what serialises it, so
        // reading the promo any other way here would silently reopen the race.
        PromoCode pc = singleUseCode();
        when(usageRepo.countByPromoCode(pc)).thenReturn(0L);

        service.reserveUsage(PROMO, ORDER, CUSTOMER, DISCOUNT);

        verify(promoRepo).findByIdForUpdate(PROMO);
        verify(promoRepo, never()).findById(PROMO);
    }

    @Test
    void respectsThePerCustomerLimitToo() {
        PromoCode pc = new PromoCode();
        pc.setCode("ONEEACH");
        pc.setMaxUsesPerCustomer(1);
        when(promoRepo.findByIdForUpdate(PROMO)).thenReturn(Optional.of(pc));
        when(usageRepo.countByPromoCodeAndUserId(pc, CUSTOMER)).thenReturn(1L);

        assertFalse(service.reserveUsage(PROMO, ORDER, CUSTOMER, DISCOUNT));
    }

    @Test
    void anUnlimitedCodeIsNeverRefused() {
        PromoCode pc = new PromoCode();
        pc.setCode("SUMMER");           // no total, no per-customer limit
        when(promoRepo.findByIdForUpdate(PROMO)).thenReturn(Optional.of(pc));
        when(usageRepo.countByPromoCode(pc)).thenReturn(9_000L);

        assertTrue(service.reserveUsage(PROMO, ORDER, CUSTOMER, DISCOUNT));
    }

    @Test
    void reservingTwiceForTheSameOrderIsANoOp() {
        // A retried checkout must not count as a second claim on the code.
        PromoCode pc = singleUseCode();
        when(usageRepo.findByPromoCode_IdAndOrderId(PROMO, ORDER))
                .thenReturn(Optional.of(new PromoCodeUsage()));

        assertTrue(service.reserveUsage(PROMO, ORDER, CUSTOMER, DISCOUNT));

        verify(usageRepo, never()).saveAndFlush(any());
    }

    @Test
    void refusesAnOrderWhoseCodeHasBeenDeleted() {
        when(promoRepo.findByIdForUpdate(PROMO)).thenReturn(Optional.empty());

        assertFalse(service.reserveUsage(PROMO, ORDER, CUSTOMER, DISCOUNT));
    }

    // ── Payment turns the hold into a redemption ─────────────────────────────

    @Test
    void payingFlipsTheReservationRatherThanAddingASecondRow() {
        singleUseCode();
        PromoCodeUsage held = new PromoCodeUsage();
        held.setStatus(PromoCodeUsage.Status.RESERVED);
        when(usageRepo.findByPromoCode_IdAndOrderId(PROMO, ORDER)).thenReturn(Optional.of(held));

        service.recordUsage(PROMO, ORDER, CUSTOMER, DISCOUNT);

        assertEquals(PromoCodeUsage.Status.REDEEMED, held.getStatus());
        verify(usageRepo).save(held);
        verify(usageRepo, never()).saveAndFlush(any());
    }

    @Test
    void stillRecordsARedemptionForAnOrderPlacedBeforeReservationsExisted() {
        // Orders already in flight at deploy time reach payment with nothing to flip, and their
        // redemption still has to be counted.
        singleUseCode();
        when(usageRepo.findByPromoCode_IdAndOrderId(PROMO, ORDER)).thenReturn(Optional.empty());

        service.recordUsage(PROMO, ORDER, CUSTOMER, DISCOUNT);

        assertEquals(PromoCodeUsage.Status.REDEEMED, savedUsage().getStatus());
    }

    // ── Giving the code back ─────────────────────────────────────────────────

    @Test
    void releasingOnlyEverTakesBackAHold() {
        // A redeemed code was spent by a customer who received the discount. Handing it back on
        // cancellation would let that discount be taken twice — once in money, once in a fresh use.
        service.releaseReservation(ORDER);

        verify(usageRepo).deleteByOrderIdAndStatus(ORDER, PromoCodeUsage.Status.RESERVED);
        verify(usageRepo, never()).deleteByOrderIdAndStatus(eq(ORDER), eq(PromoCodeUsage.Status.REDEEMED));
    }
}
