package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderPaymentMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.event.OrderPackagingEvent;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.order.service.QuiqupCoverage;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Pins when a Quiqup job is released for collection — the call that actually summons a courier.
 *
 * <p>Creating a job only books it: Quiqup's dispatching cannot see a pending job, so until it is
 * released no courier comes. It is released when the order moves to PACKAGING, because that is when
 * the shop has the order in hand. Too early is a van at a shop that has not found the parcel; never
 * is an order that looks dispatched while nobody is coming for it.
 */
class QuiqupReleaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID ORDER_ID = UUID.fromString("7a1c0de0-0000-4000-8000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("5f0e0000-0000-4000-8000-000000000002");
    private static final String READY_PATH = "/orders/998877/ready_for_collection";

    private final QuiqupProperties props = new QuiqupProperties();
    private final QuiqupClient client = mock(QuiqupClient.class);
    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private final QuiqupCoverage coverage = mock(QuiqupCoverage.class);
    private Order order;
    private QuiqupDispatchService service;

    private static QuiqupApiResult answered(int status, String body) throws Exception {
        return new QuiqupApiResult(status, status / 100 == 2, JSON.readTree(body));
    }

    @BeforeEach
    void setUp() {
        props.setEnabled(true);
        props.getDispatch().setEnabled(true);

        order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(OrderStatus.PACKAGING);
        order.setDeliveryMethod(DeliveryMethod.REGULAR);
        order.setCountry("UAE");
        order.setCurrency("AED");
        order.setTotalAmount(new BigDecimal("105.50"));
        order.setDeliveryLatitude(25.0805);
        order.setDeliveryLongitude(55.1403);
        order.setRecipientPhone("+971500000002");
        when(orderRepo.findById(ORDER_ID)).thenAnswer(inv -> Optional.of(order));
        when(orderRepo.claimForQuiqupDispatch(eq(ORDER_ID), any(), any())).thenReturn(1);
        // The release claim is a compare-and-set on the counter; mirror it on the one instance.
        when(orderRepo.claimQuiqupRelease(eq(ORDER_ID), anyInt(), anyBoolean())).thenAnswer(inv -> {
            int expected = inv.getArgument(1);
            boolean evenWhenPaid = inv.getArgument(2);
            int current = order.getQuiqupReleaseAttempts() == null ? 0 : order.getQuiqupReleaseAttempts();
            boolean statusOk = order.getStatus() == OrderStatus.PACKAGING
                    || (evenWhenPaid && order.getStatus() == OrderStatus.PAID);
            if (order.getQuiqupReleasedAt() != null || order.getQuiqupOrderId() == null
                    || current != expected || !statusOk) {
                return 0;
            }
            order.setQuiqupReleaseAttempts(expected + 1);
            return 1;
        });

        OrderItem item = new OrderItem();
        item.setStoreId(STORE_ID);
        OrderItemRepository itemRepo = mock(OrderItemRepository.class);
        when(itemRepo.findAllByOrderId(ORDER_ID)).thenReturn(List.of(item));

        StoreLocation location = new StoreLocation();
        location.setLatitude(25.1972);
        location.setLongitude(55.2744);
        location.setIsPrimary(true);
        StoreLocationRepository locationRepo = mock(StoreLocationRepository.class);
        when(locationRepo.findAllByStoreIdAndIsActive(STORE_ID, true)).thenReturn(List.of(location));

        StoreRepository storeRepo = mock(StoreRepository.class);
        when(storeRepo.findById(STORE_ID)).thenReturn(Optional.empty());

        when(coverage.covers(any(), any())).thenReturn(true);

        service = new QuiqupDispatchService(props, client, new QuiqupOrderMapper(JSON, props),
                coverage, orderRepo, itemRepo, storeRepo, locationRepo,
                mock(PlatformTransactionManager.class));
    }

    private void quiqupCreates() throws Exception {
        when(client.request(eq("POST"), eq("/orders"), any())).thenReturn(answered(201, "{\"order\": {\"id\": 998877}}"));
    }

    private void quiqupReleases(QuiqupApiResult result) {
        when(client.request(eq("PUT"), eq(READY_PATH), isNull())).thenReturn(result);
    }

    private void quiqupJobReads(String state) throws Exception {
        when(client.request(eq("GET"), eq("/orders/998877"), isNull()))
                .thenReturn(answered(200, "{\"order\": {\"id\": 998877, \"state\": \"" + state + "\"}}"));
    }

    private void dispatched() {
        order.setQuiqupOrderId("998877");
        order.setQuiqupDispatchedAt(Instant.now());
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @Test
    void aCashOrderIsBookedAndReleasedTheMomentItIsPacked() throws Exception {
        // A cash order is not dispatchable before PACKAGING, so moving it there creates the job and
        // summons the courier in one go.
        order.setPaymentMethod(OrderPaymentMethod.CASH_ON_DELIVERY);
        quiqupCreates();
        quiqupReleases(answered(200, "{}"));

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        assertEquals("998877", order.getQuiqupOrderId());
        assertNotNull(order.getQuiqupReleasedAt());
        verify(client, times(1)).request("PUT", READY_PATH, null);
    }

    @Test
    void aCardOrderIsBookedOnPaymentButNotReleased() throws Exception {
        order.setStatus(OrderStatus.PAID);
        quiqupCreates();

        String outcome = service.dispatch(ORDER_ID);

        assertEquals("998877", order.getQuiqupOrderId());
        assertNull(order.getQuiqupReleasedAt(), "nobody has packed it yet: " + outcome);
        verify(client, never()).request(eq("PUT"), any(), any());
    }

    @Test
    void aBookedCardOrderIsReleasedWhenItIsPacked() throws Exception {
        dispatched();
        quiqupReleases(answered(200, "{}"));

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        assertNotNull(order.getQuiqupReleasedAt());
        verify(client, never()).request(eq("POST"), any(), any());
    }

    @Test
    void theAutoReadySettingStillReleasesAtCreation() throws Exception {
        props.getDispatch().setAutoReadyForCollection(true);
        order.setStatus(OrderStatus.PAID);
        quiqupCreates();
        quiqupReleases(answered(200, "{}"));

        service.dispatch(ORDER_ID);

        assertNotNull(order.getQuiqupReleasedAt());
    }

    @Test
    void releaseOnPackagingOffLeavesTheJobForAHuman() throws Exception {
        props.getDispatch().setReleaseOnPackaging(false);
        dispatched();

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        verify(client, never()).request(eq("PUT"), any(), any());
    }

    @Test
    void nothingHappensWhileAutomaticDispatchIsOff() {
        props.getDispatch().setEnabled(false);

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        verifyNoInteractions(client);
    }

    @Test
    void anOrderQuiqupDoNotCarryIsLeftAloneWhenPacked() {
        // A pickup order, say. Dispatching it would stamp "Not a Quiqup delivery" on it in red.
        order.setDeliveryMethod(DeliveryMethod.PICKUP);
        when(coverage.covers(eq(DeliveryMethod.PICKUP), any())).thenReturn(false);

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        verifyNoInteractions(client);
        assertNull(order.getQuiqupDispatchError());
    }

    @Test
    void aCashOrderWhoseFirstReleaseFailsSpendsOneAttemptNotTwo() throws Exception {
        order.setPaymentMethod(OrderPaymentMethod.CASH_ON_DELIVERY);
        quiqupCreates();
        quiqupReleases(answered(503, "{}"));
        quiqupJobReads("pending");

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        assertEquals(1, order.getQuiqupReleaseAttempts());
        verify(client, times(1)).request("PUT", READY_PATH, null);
    }

    @Test
    void aJobCreatedInParallelByThePaymentDispatchIsStillReleased() throws Exception {
        // The payment-time dispatch finished between our read and our dispatch: it read the status
        // before PACKAGING and left the job unreleased, and ours finds it "already dispatched".
        when(orderRepo.findById(ORDER_ID)).thenAnswer(new org.mockito.stubbing.Answer<Optional<Order>>() {
            private boolean first = true;
            @Override
            public Optional<Order> answer(org.mockito.invocation.InvocationOnMock inv) {
                if (first) {
                    first = false;
                    Order before = new Order();
                    before.setId(ORDER_ID);
                    before.setStatus(OrderStatus.PACKAGING);
                    before.setDeliveryMethod(DeliveryMethod.REGULAR);
                    before.setCountry("UAE");
                    return Optional.of(before);
                }
                return Optional.of(order);
            }
        });
        dispatched();
        quiqupReleases(answered(200, "{}"));

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        verify(client, never()).request(eq("POST"), any(), any());
        assertNotNull(order.getQuiqupReleasedAt());
    }

    @Test
    void aStoppedDispatchIsNotRetriedByPacking() {
        // Stopped means Quiqup refused the job or may already hold one; packing it must not quietly
        // create another.
        order.setQuiqupDispatchStoppedAt(Instant.now());

        service.onOrderPackaging(new OrderPackagingEvent(ORDER_ID));

        verifyNoInteractions(client);
    }

    // ── Only once, and only for the right order ──────────────────────────────

    @Test
    void anOrderAlreadyReleasedIsNotReleasedAgain() {
        dispatched();
        order.setQuiqupReleasedAt(Instant.now());

        assertEquals("Already released for collection", service.release(ORDER_ID, false));
        verifyNoInteractions(client);
    }

    @Test
    void aCancelledOrderIsNeverReleased() {
        dispatched();
        order.setStatus(OrderStatus.CANCELLED);

        service.release(ORDER_ID, false);

        verifyNoInteractions(client);
    }

    @Test
    void theOtherReplicaReleasingItMeansStandingDown() {
        dispatched();
        when(orderRepo.claimQuiqupRelease(eq(ORDER_ID), anyInt(), anyBoolean())).thenReturn(0);

        service.release(ORDER_ID, false);

        verifyNoInteractions(client);
    }

    // ── When Quiqup says no ──────────────────────────────────────────────────

    @Test
    void aFailedReleaseIsRecordedAndLeftForTheSweep() throws Exception {
        dispatched();
        quiqupReleases(answered(503, "{}"));
        quiqupJobReads("pending");

        service.release(ORDER_ID, false);

        assertNull(order.getQuiqupReleasedAt());
        assertEquals(1, order.getQuiqupReleaseAttempts());
        assertTrue(order.getQuiqupDispatchError().startsWith("Courier not summoned yet: release attempt 1 of 5"),
                order.getQuiqupDispatchError());
    }

    @Test
    void aJobAlreadyReleasedByHandCountsAsReleased() throws Exception {
        // Someone pressed ready in Quiqup's dashboard first, so our call is refused. The courier is
        // coming all the same; retrying five times and then shouting would be wrong.
        dispatched();
        quiqupReleases(answered(422, "{\"error\": \"invalid transition\"}"));
        quiqupJobReads("ready_for_collection");

        service.release(ORDER_ID, false);

        assertNotNull(order.getQuiqupReleasedAt());
        assertNull(order.getQuiqupDispatchError());
    }

    @Test
    void aJobCancelledAtQuiqupStopsTheRetriesAtOnce() throws Exception {
        dispatched();
        quiqupReleases(answered(422, "{}"));
        quiqupJobReads("cancelled");

        service.release(ORDER_ID, false);

        assertNull(order.getQuiqupReleasedAt());
        assertEquals(props.getDispatch().getMaxAttempts(), order.getQuiqupReleaseAttempts(),
                "the sweep skips an order at the cap");
        assertTrue(order.getQuiqupDispatchError().contains("is cancelled at Quiqup"));
    }

    @Test
    void theLastAttemptSaysWhatToDoByHand() throws Exception {
        dispatched();
        order.setQuiqupReleaseAttempts(props.getDispatch().getMaxAttempts() - 1);
        quiqupReleases(answered(503, "{}"));
        quiqupJobReads("pending");

        service.release(ORDER_ID, false);

        assertTrue(order.getQuiqupDispatchError().contains("Release Quiqup job 998877 from Quiqup's dashboard"),
                order.getQuiqupDispatchError());
    }

    @Test
    void releasingAgainAfterAFailureClearsTheError() throws Exception {
        dispatched();
        order.setQuiqupReleaseAttempts(1);
        order.setQuiqupDispatchError("Courier not summoned yet: release attempt 1 of 5 failed");
        quiqupReleases(answered(200, "{}"));

        service.release(ORDER_ID, false);

        assertNotNull(order.getQuiqupReleasedAt());
        assertNull(order.getQuiqupDispatchError());
    }

    @Test
    void whatCountsAsAlreadyReleased() {
        assertTrue(QuiqupDispatchService.isPastPending("ready_for_collection"));
        assertTrue(QuiqupDispatchService.isPastPending("collected"));
        assertTrue(QuiqupDispatchService.isPastPending("delivered"));
        assertFalse(QuiqupDispatchService.isPastPending("pending"));
        assertFalse(QuiqupDispatchService.isPastPending("cancelled"));
        assertFalse(QuiqupDispatchService.isPastPending(null));
    }

    // ── A cancel that raced the create ───────────────────────────────────────

    @Test
    void aJobCreatedForAnOrderCancelledMidFlightIsQueuedForCancelling() throws Exception {
        // The admin cancelled while the POST was in flight: their cancel found no job to stop. The
        // job that then arrives must be stopped too, not left running.
        when(client.request(eq("POST"), eq("/orders"), any())).thenAnswer(inv -> {
            order.setStatus(OrderStatus.CANCELLED);
            return answered(201, "{\"order\": {\"id\": 998877}}");
        });

        service.dispatch(ORDER_ID);

        assertEquals("998877", order.getQuiqupOrderId());
        assertEquals("PENDING", order.getQuiqupCancelStatus(), "the cancel retry job picks PENDING up");
        assertNotNull(order.getQuiqupCancelRequestedAt());
        verify(client, never()).request(eq("PUT"), eq(READY_PATH), any());
    }
}
