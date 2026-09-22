package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.buyology.ecommerce.quiqup.service.QuiqupCancelService.CancelResult;
import com.buyology.ecommerce.quiqup.service.QuiqupCancelService.Outcome;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Pins what we send to stop a Quiqup job, what we keep of Quiqup's answer, and how an admin gets a
 * stuck cancel moving again.
 *
 * <p>A cancel from the admin once left the job running at Quiqup, and the order could not say why:
 * Quiqup's own answer was thrown away, and a cancel that had given up could never be tried again.
 */
class QuiqupCancelRequestTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID ORDER_ID = UUID.fromString("c4a1ce11-0000-4000-8000-000000000001");

    private static QuiqupApiResult answered(int status, String body) throws Exception {
        return new QuiqupApiResult(status, status / 100 == 2, JSON.readTree(body));
    }

    // ── The request ──────────────────────────────────────────────────────────

    @Test
    void theJobIdGoesAsANumberLikeEveryQuiqupExample() {
        JsonNode body = QuiqupCancelService.cancelBody(JSON, "26012997");

        assertEquals("{\"order_ids\":[26012997]}", body.toString());
        assertTrue(body.get("order_ids").get(0).isNumber());
    }

    @Test
    void anIdThatIsNotANumberIsSentAsItIs() {
        assertEquals("{\"order_ids\":[\"a1b2-c3\"]}", QuiqupCancelService.cancelBody(JSON, "a1b2-c3").toString());
    }

    // ── What Quiqup said ─────────────────────────────────────────────────────

    @Test
    void aRefusalKeepsQuiqupsStatusAndReason() throws Exception {
        CancelResult result = QuiqupCancelService.withQuiqupsAnswer(
                new CancelResult(Outcome.NEEDS_HUMAN, "cancel refused; job reads as 'ready_for_collection'"),
                answered(422, "{\"error\": \"order is not pending\"}"), "26012997");

        assertEquals(Outcome.NEEDS_HUMAN, result.outcome());
        assertTrue(result.detail().contains("Quiqup answered the cancel with 422"), result.detail());
        assertTrue(result.detail().contains("order is not pending"), result.detail());
    }

    @Test
    void anAcceptedCancelThatSkippedOurJobSaysSo() throws Exception {
        // Quiqup document the 2xx reply as the list of orders they cancelled.
        CancelResult result = QuiqupCancelService.withQuiqupsAnswer(
                new CancelResult(Outcome.UNCONFIRMED, "job still reads as 'pending' after the cancel"),
                answered(200, "[]"), "26012997");

        assertTrue(result.detail().contains("left this job out of the orders it cancelled"), result.detail());
    }

    @Test
    void aReplyListingOurJobIsNotCalledASkip() throws Exception {
        CancelResult result = QuiqupCancelService.withQuiqupsAnswer(
                new CancelResult(Outcome.UNCONFIRMED, "job still reads as 'pending' after the cancel"),
                answered(200, "[{\"id\": 26012997, \"state\": \"pending\"}]"), "26012997");

        assertFalse(result.detail().contains("left this job out"), result.detail());
    }

    @Test
    void aConfirmedStopIsLeftAsItIs() throws Exception {
        CancelResult confirmed = new CancelResult(Outcome.CONFIRMED, "job reads as 'cancelled'");

        assertSame(confirmed, QuiqupCancelService.withQuiqupsAnswer(confirmed, answered(200, "[]"), "26012997"));
    }

    // ── Getting a stuck cancel moving again ──────────────────────────────────

    private final QuiqupProperties props = new QuiqupProperties();
    private final QuiqupClient client = mock(QuiqupClient.class);
    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private Order order;
    private QuiqupCancelService service;

    @BeforeEach
    void setUp() {
        props.setEnabled(true);
        props.setBaseUrl("https://api-ae.quiqup.com");
        props.setAllowProductionWrites(true);

        order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(OrderStatus.CANCELLED);
        order.setQuiqupOrderId("26012997");
        order.setQuiqupCancelStatus("NEEDS_HUMAN");
        order.setQuiqupCancelAttempts(5);
        order.setQuiqupCancelError("gave up after 5 unconfirmed attempts");
        when(orderRepo.findById(ORDER_ID)).thenAnswer(inv -> Optional.of(order));
        when(orderRepo.claimForQuiqupCancel(eq(ORDER_ID), any(), any())).thenReturn(1);

        service = new QuiqupCancelService(props, client, orderRepo, JSON,
                mock(UserRoleRepository.class), mock(PushNotificationService.class),
                mock(PlatformTransactionManager.class));
    }

    @Test
    void withoutRearmingAnEscalatedCancelNeverAsksQuiqupAgain() {
        CancelResult result = service.cancelForOrder(ORDER_ID, "test");

        assertEquals(Outcome.NEEDS_HUMAN, result.outcome());
        verifyNoInteractions(client);
    }

    @Test
    void rearmingAsksQuiqupAgainAndConfirmsAJobCancelledByHand() throws Exception {
        // The admin cancelled the job in Quiqup's dashboard, then pressed retry.
        when(client.request(eq("PUT"), eq("/orders/batch/set_cancelled"), any()))
                .thenReturn(answered(422, "{\"error\": \"order is not pending\"}"));
        when(client.request(eq("GET"), eq("/orders/26012997"), isNull()))
                .thenReturn(answered(200, "{\"order\": {\"id\": 26012997, \"state\": \"cancelled\"}}"));

        assertTrue(service.rearm(ORDER_ID));
        CancelResult result = service.cancelForOrder(ORDER_ID, "test");

        assertEquals(Outcome.CONFIRMED, result.outcome());
        assertTrue(result.refundAllowed(), "a confirmed stop releases what the cancel held");
        assertEquals("CONFIRMED", order.getQuiqupCancelStatus());
        assertEquals(1, order.getQuiqupCancelAttempts(), "the count starts again");
        assertNull(order.getQuiqupCancelError());

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).request(eq("PUT"), eq("/orders/batch/set_cancelled"), body.capture());
        assertEquals("{\"order_ids\":[26012997]}", body.getValue().toString());
    }

    @Test
    void aRetryThatStillFailsShowsQuiqupsReasonOnTheOrder() throws Exception {
        when(client.request(eq("PUT"), eq("/orders/batch/set_cancelled"), any()))
                .thenReturn(answered(422, "{\"error\": \"order is not pending\"}"));
        when(client.request(eq("GET"), eq("/orders/26012997"), isNull()))
                .thenReturn(answered(200, "{\"order\": {\"id\": 26012997, \"state\": \"ready_for_collection\"}}"));

        service.rearm(ORDER_ID);
        service.cancelForOrder(ORDER_ID, "test");

        assertEquals("NEEDS_HUMAN", order.getQuiqupCancelStatus());
        assertTrue(order.getQuiqupCancelError().contains("order is not pending"), order.getQuiqupCancelError());
    }

    @Test
    void rearmingLeavesACallInFlightItsClaim() {
        // The retry job may be talking to Quiqup about this order right now. Clearing its claim
        // would let a second call run alongside it and release the money twice.
        java.time.Instant inFlight = java.time.Instant.now();
        order.setQuiqupCancelClaimedAt(inFlight);

        service.rearm(ORDER_ID);

        assertEquals(inFlight, order.getQuiqupCancelClaimedAt());
        assertEquals("PENDING", order.getQuiqupCancelStatus());
    }

    @Test
    void aJobCreatedAfterItsOrderWasCancelledIsRecognised() {
        java.time.Instant cancelledAt = java.time.Instant.parse("2026-09-22T09:00:00Z");
        order.setCancelledAt(cancelledAt);

        order.setQuiqupDispatchedAt(cancelledAt.plusSeconds(2));
        assertTrue(QuiqupCancelService.jobCreatedAfterCancel(order));

        order.setQuiqupDispatchedAt(cancelledAt.minusSeconds(3600));
        assertFalse(QuiqupCancelService.jobCreatedAfterCancel(order), "the usual case: dispatched, then cancelled");
    }

    @Test
    void aConfirmedStopIsNeverRearmed() {
        order.setQuiqupCancelStatus("CONFIRMED");

        assertFalse(service.rearm(ORDER_ID));
        assertEquals("CONFIRMED", order.getQuiqupCancelStatus());
    }

    @Test
    void anOrderWithNoJobHasNothingToRearm() {
        order.setQuiqupOrderId(null);

        assertFalse(service.rearm(ORDER_ID));
    }
}
