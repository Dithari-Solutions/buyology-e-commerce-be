package com.buyology.ecommerce.cart.reminder;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins what a cart reminder may and may not do to a customer.
 *
 * <p>The failures worth guarding here are the quiet ones: a cart marked as reminded after a send
 * that never happened, a second email to someone who already got one, and a sweep that gives up on
 * everyone behind one broken cart.
 */
class CartReminderServiceTest {

    private CartReminderRepository repository;
    private EmailService emailService;
    private PushNotificationService notifications;

    private static final UUID CART = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(CartReminderRepository.class);
        emailService = mock(EmailService.class);
        notifications = mock(PushNotificationService.class);
    }

    private CartReminderService service() {
        return service(true);
    }

    private CartReminderService service(boolean enabled) {
        return new CartReminderService(repository, emailService, notifications,
                enabled, 4, 7, 100, "You left something in your cart", "https://buyology.online");
    }

    private static CartReminderRepository.Candidate candidate() {
        return new CartReminderRepository.Candidate(
                CART, USER, "shopper@example.com", "Firdovsi", "AED", "opt-out-token");
    }

    private static CartReminderRepository.Line line(String title, int qty, String total) {
        return new CartReminderRepository.Line(title, qty, new BigDecimal(total));
    }

    private void oneDueCart(CartReminderRepository.Line... lines) {
        when(repository.findCandidates(any(), any(), anyInt())).thenReturn(List.of(candidate()));
        when(repository.findLines(CART)).thenReturn(List.of(lines));
    }

    // ── The stamp must record what happened ──────────────────────────────────

    @Test
    void aSentReminderStampsTheCart() {
        oneDueCart(line("Lenovo ThinkPad T490", 1, "899.00"));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        assertEquals(1, service().sendDueReminders());
        verify(repository).markReminded(eq(CART), any(Instant.class));
    }

    @Test
    void aFailedSendLeavesTheCartArmedForTheNextSweep() {
        oneDueCart(line("Lenovo ThinkPad T490", 1, "899.00"));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);

        assertEquals(0, service().sendDueReminders());
        verify(repository, never()).markReminded(any(), any());
        // No bell either: a customer who got no email must not see a notification about one.
        verify(notifications, never()).sendToUser(any(), any(), any(), any(), any());
    }

    @Test
    void aCartEmptiedSinceTheSweepIsNeitherMailedNorStamped() {
        when(repository.findCandidates(any(), any(), anyInt())).thenReturn(List.of(candidate()));
        when(repository.findLines(CART)).thenReturn(List.of());

        assertEquals(0, service().sendDueReminders());
        verifyNoInteractions(emailService);
        verify(repository, never()).markReminded(any(), any());
    }

    @Test
    void aFailedBellDoesNotUndoASentEmail() {
        oneDueCart(line("Lenovo ThinkPad T490", 1, "899.00"));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        doThrow(new RuntimeException("push is down"))
                .when(notifications).sendToUser(any(), any(), any(), any(), any());

        assertEquals(1, service().sendDueReminders());
        verify(repository).markReminded(eq(CART), any(Instant.class));
    }

    // ── What the customer is told ────────────────────────────────────────────

    @Test
    void theTotalIsTheSumOfTheSelectedLinesAndTheCartsOwnCurrency() {
        oneDueCart(line("Lenovo ThinkPad T490", 1, "899.00"), line("Dell Latitude 5400", 2, "799.50"));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        service().sendDueReminders();

        ArgumentCaptor<String> total = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAbandonedCartEmail(
                eq("shopper@example.com"), eq("You left something in your cart"), eq("Firdovsi"),
                anyList(), total.capture(), any(), any());
        assertEquals("AED 1698.50", total.getValue());
    }

    @Test
    void theLinkGoesThroughSignInSoASignedOutReaderDoesNotLandOnAnEmptyCart() {
        oneDueCart(line("Lenovo ThinkPad T490", 1, "899.00"));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        service().sendDueReminders();

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> unsubscribe = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAbandonedCartEmail(any(), any(), any(), anyList(), any(),
                url.capture(), unsubscribe.capture());
        assertTrue(url.getValue().contains("/login?next=%2Fcart"), url.getValue());
        assertTrue(unsubscribe.getValue().endsWith("/api/email/opt-out?token=opt-out-token"),
                "every marketing email must carry a working opt-out: " + unsubscribe.getValue());
    }

    @Test
    void theBellSaysHowManyItemsAreWaitingAndCarriesTheCart() {
        oneDueCart(line("Lenovo ThinkPad T490", 1, "899.00"), line("Dell Latitude 5400", 1, "799.00"));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        service().sendDueReminders();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(notifications).sendToUser(eq(USER), any(), body.capture(), eq("CART_REMINDER"), data.capture());
        assertEquals("2 items are still in your cart.", body.getValue());
        assertEquals(CART.toString(), data.getValue().get("cartId"));
    }

    @Test
    void oneItemReadsAsOneItem() {
        oneDueCart(line("Lenovo ThinkPad T490", 1, "899.00"));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        service().sendDueReminders();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendToUser(any(), any(), body.capture(), any(), any());
        assertEquals("1 item is still in your cart.", body.getValue());
    }

    // ── The sweep as a whole ─────────────────────────────────────────────────

    @Test
    void oneBrokenCartDoesNotEndTheRoundForTheCartsBehindIt() {
        UUID brokenCart = UUID.randomUUID();
        CartReminderRepository.Candidate broken = new CartReminderRepository.Candidate(
                brokenCart, UUID.randomUUID(), "other@example.com", "Ali", "AED", "token-2");
        when(repository.findCandidates(any(), any(), anyInt())).thenReturn(List.of(broken, candidate()));
        when(repository.findLines(brokenCart)).thenThrow(new RuntimeException("bad row"));
        when(repository.findLines(CART)).thenReturn(List.of(line("Lenovo ThinkPad T490", 1, "899.00")));
        when(emailService.sendAbandonedCartEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        assertEquals(1, service().sendDueReminders());
        verify(repository).markReminded(eq(CART), any(Instant.class));
        verify(repository, never()).markReminded(eq(brokenCart), any());
    }

    @Test
    void theSwitchOffSendsNothingAndAsksTheDatabaseNothing() {
        assertEquals(0, service(false).sendDueReminders());
        verifyNoInteractions(repository, emailService, notifications);
    }

    @Test
    void anEmptySweepIsSilent() {
        when(repository.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

        assertEquals(0, service().sendDueReminders());
        verifyNoInteractions(emailService, notifications);
    }

    @Test
    void theSweepAsksForAWindow_quietEnoughToBeAbandonedAndRecentEnoughToMatter() {
        when(repository.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

        service().sendDueReminders();

        ArgumentCaptor<Instant> quietSince = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> notOlderThan = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findCandidates(quietSince.capture(), notOlderThan.capture(), eq(100));

        // Minutes, not hours: the cutoff is computed a few microseconds after any "now" this test
        // can take, and truncating 3h59m59s to hours reads as 3.
        long quietMinutes = java.time.Duration.between(quietSince.getValue(), Instant.now()).toMinutes();
        assertTrue(quietMinutes >= 239 && quietMinutes <= 241,
                "a cart is only due once it has been quiet for the configured hours, was " + quietMinutes + "m");

        // The floor is what stops the first production run mailing years of cold carts at once.
        long ageDays = java.time.Duration.between(notOlderThan.getValue(), Instant.now()).toDays();
        assertEquals(7, ageDays, "carts colder than the window are over, not forgotten");
        assertTrue(notOlderThan.getValue().isBefore(quietSince.getValue()),
                "the floor must be older than the quiet cutoff, or the window is empty");
    }
}
