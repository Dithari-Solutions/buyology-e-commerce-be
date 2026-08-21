package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.repository.CreditUsageRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the money that goes back to a B2B member when their order is cancelled.
 *
 * <p>Everything here is a number that ends up in someone's wallet, and each test is a way of
 * getting it wrong that costs real money in one direction or the other: not returning it at all
 * (the member is chased for goods they never received), returning it twice, or returning more than
 * was ever taken.
 */
class CreditReturnServiceTest {

    private static final UUID ORDER = UUID.fromString("3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607");
    private static final UUID MEMBER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final CreditUsageRepository usageRepo = mock(CreditUsageRepository.class);
    private final WalletService wallet = mock(WalletService.class);
    private final CreditReturnService service = new CreditReturnService(usageRepo, wallet);

    private CreditUsage usage(String amount, String paid, CreditUsage.Status status) {
        CreditUsage u = new CreditUsage();  // id is DB-generated, and nothing here reads it
        u.setUserId(MEMBER);
        u.setOrderId(ORDER);
        u.setAmount(new BigDecimal(amount));
        u.setPaidAmount(paid == null ? null : new BigDecimal(paid));
        u.setCurrency("AED");
        u.setStatus(status);
        when(usageRepo.findByOrderId(ORDER)).thenReturn(Optional.of(u));
        return u;
    }

    // ── The bug this exists to fix ───────────────────────────────────────────

    @Test
    void returnsTheWholeBalanceWhenNothingHasBeenRepaid() {
        CreditUsage u = usage("20000.00", "0.00", CreditUsage.Status.OUTSTANDING);

        assertTrue(service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00")));

        verify(wallet).addCredit(eq(MEMBER), eq(new BigDecimal("20000.00")), any(), eq("SYSTEM"));
        assertEquals(CreditUsage.Status.REVERSED, u.getStatus());
        verify(usageRepo).save(u);
    }

    @Test
    void marksTheUsageReversedRatherThanPaid() {
        // PAID would say the member settled this. They did not — the goods never left the shop, and
        // every repayment report reading that status would be counting money nobody ever collected.
        CreditUsage u = usage("20000.00", "0.00", CreditUsage.Status.OUTSTANDING);

        service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00"));

        assertEquals(CreditUsage.Status.REVERSED, u.getStatus());
        assertNotEquals(CreditUsage.Status.PAID, u.getStatus());
    }

    // ── Returning too much ───────────────────────────────────────────────────

    @Test
    void returnsOnlyWhatIsStillOwedWhenSomeWasAlreadyRepaid() {
        // The member already paid 8,000 of the 20,000 back in cash. Restoring the full 20,000 of
        // credit would hand them that 8,000 for free.
        usage("20000.00", "8000.00", CreditUsage.Status.PARTIAL);

        assertTrue(service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00")));

        verify(wallet).addCredit(eq(MEMBER), eq(new BigDecimal("12000.00")), any(), eq("SYSTEM"));
    }

    @Test
    void creditsNothingWhenTheMemberAlreadyRepaidInFull() {
        // Nothing is owed on the credit line any more, so there is no credit to give back. The cash
        // they paid is a refund question for a human, not a balance to invent here.
        CreditUsage u = usage("20000.00", "20000.00", CreditUsage.Status.PAID);

        assertFalse(service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00")));

        verify(wallet, never()).addCredit(any(), any(), any(), any());
        assertEquals(CreditUsage.Status.REVERSED, u.getStatus(), "the line is still closed out");
    }

    @Test
    void neverCreditsANegativeAmountIfMoreWasRepaidThanOwed() {
        usage("20000.00", "20500.00", CreditUsage.Status.PAID);

        service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00"));

        verify(wallet, never()).addCredit(any(), any(), any(), any());
    }

    // ── Returning it twice ───────────────────────────────────────────────────

    @Test
    void isIdempotentOnASecondCancellation() {
        // Two cancellation events for one order — a retry, a duplicate webhook, an admin cancelling
        // something already cancelled — must not credit the wallet twice.
        usage("20000.00", "0.00", CreditUsage.Status.REVERSED);

        assertFalse(service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00")));

        verify(wallet, never()).addCredit(any(), any(), any(), any());
        verify(usageRepo, never()).save(any());
    }

    // ── Orders with no credit leg at all ─────────────────────────────────────

    @Test
    void doesNothingForAnOrderPaidEntirelyByCard() {
        assertFalse(service.returnForCancelledOrder(ORDER, null));
        assertFalse(service.returnForCancelledOrder(ORDER, BigDecimal.ZERO));

        verifyNoInteractions(usageRepo);
        verifyNoInteractions(wallet);
    }

    @Test
    void doesNotInventCreditWhenTheUsageRowIsMissing() {
        // The order claims credit was applied but no usage row backs it. That is a data problem to
        // shout about, not a licence to credit a wallet from a number on the order alone.
        when(usageRepo.findByOrderId(ORDER)).thenReturn(Optional.empty());

        assertFalse(service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00")));

        verify(wallet, never()).addCredit(any(), any(), any(), any());
    }

    @Test
    void toleratesAUsageRowWithNoRecordedRepayment() {
        // paid_amount defaults to zero but is nullable in the schema; a null there must read as
        // "nothing repaid", not throw and abandon the return.
        usage("20000.00", null, CreditUsage.Status.OUTSTANDING);

        assertTrue(service.returnForCancelledOrder(ORDER, new BigDecimal("20000.00")));

        verify(wallet).addCredit(eq(MEMBER), eq(new BigDecimal("20000.00")), any(), eq("SYSTEM"));
    }
}
