package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.order.service.QuiqupCoverage;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.buyology.ecommerce.quiqup.service.QuiqupDispatchService.Failure;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.buyology.ecommerce.quiqup.service.QuiqupDispatchService.classifyFailure;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins what a failed Quiqup create does to the order, and whether the retry job may send it again.
 *
 * <p>Two ways to get this wrong, and both have been reported by Quiqup:
 * <ul>
 *   <li>Retrying what cannot succeed. Every cash job was refused with a 422 on payment_mode, and the
 *       retry job sent the identical payload every five minutes from both replicas — about 425
 *       times in a day for one order.
 *   <li>Retrying what may already have succeeded. Quiqup do not deduplicate on partner_order_id, so
 *       a create repeated after a lost answer is a second courier for the same parcel.
 * </ul>
 */
class QuiqupDispatchFailureTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static QuiqupApiResult answered(int status, String body) throws Exception {
        return new QuiqupApiResult(status, status / 100 == 2, JSON.readTree(body));
    }

    // ── Classification ──────────────────────────────────────────────────────

    @Test
    void theRejectionThatStartedThisStopsRetriesAtOnce() throws Exception {
        // What Quiqup returned for payment_mode "cod": a 422 whose detail was empty.
        QuiqupApiResult invalidEnum = answered(422, "{\"attribute_errors\": {}}");

        assertEquals(Failure.REJECTED, classifyFailure(invalidEnum),
                "an unchanged payload fails the same way every time");
        assertEquals(Failure.REJECTED, classifyFailure(answered(400, "{}")));
        assertEquals(Failure.REJECTED, classifyFailure(answered(401, "{}")));
    }

    @Test
    void answersThatSayNothingWasProcessedAreRetried() throws Exception {
        assertEquals(Failure.RETRY, classifyFailure(answered(408, "{}")));
        assertEquals(Failure.RETRY, classifyFailure(answered(429, "{}")));
        assertEquals(Failure.RETRY, classifyFailure(answered(503, "{}")));
    }

    @Test
    void aServerErrorMayHaveCreatedTheJob() throws Exception {
        for (int status : new int[]{500, 502, 504}) {
            assertEquals(Failure.UNKNOWN, classifyFailure(answered(status, "{}")),
                    status + " can come after the job was written");
        }
        assertEquals(Failure.UNKNOWN, classifyFailure(null));
    }

    @Test
    void aTimeoutIsNotAConnectionFailure() {
        // The client reports both as 502. Only one of them can have reached Quiqup.
        QuiqupApiResult timedOut = new QuiqupApiResult(502, false,
                "Connected to https://api-ae.quiqup.com/orders but Quiqup sent no response", true);
        QuiqupApiResult neverConnected = new QuiqupApiResult(502, false,
                "Could not open a connection to https://api-ae.quiqup.com/orders", false);

        assertEquals(Failure.UNKNOWN, classifyFailure(timedOut));
        assertEquals(Failure.RETRY, classifyFailure(neverConnected));
    }

    @Test
    void ourOwnGuardIsConfigurationNotWeather() {
        QuiqupApiResult blocked = new QuiqupApiResult(409, false,
                "Blocked: POST is a write against a non-staging Quiqup base URL", false);

        assertEquals(Failure.REJECTED, classifyFailure(blocked));
    }

    @Test
    void onlyAFailureBeforeConnectingProvesNothingWasSent() {
        assertTrue(QuiqupClient.neverConnected(
                new RuntimeException(new java.net.ConnectException("Connection refused"))));
        assertTrue(QuiqupClient.neverConnected(new java.net.UnknownHostException("api-ae.quiqup.com")));
        assertTrue(QuiqupClient.neverConnected(new io.netty.channel.ConnectTimeoutException("connect timed out")));

        assertFalse(QuiqupClient.neverConnected(new java.util.concurrent.TimeoutException("no response")));
        assertFalse(QuiqupClient.neverConnected(new java.io.IOException("Connection reset by peer")));
    }

    // ── dispatch(), with Quiqup and the database mocked ───────────────────────

    private static final UUID ORDER_ID = UUID.fromString("33f3c5ca-0000-4000-8000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("5f0e0000-0000-4000-8000-000000000002");

    private final QuiqupProperties props = new QuiqupProperties();
    private final QuiqupClient client = mock(QuiqupClient.class);
    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private Order order;
    private QuiqupDispatchService service;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(OrderStatus.PACKAGING);
        order.setDeliveryMethod(DeliveryMethod.REGULAR);
        order.setCountry("UAE");
        order.setDeliveryLatitude(25.0805);
        order.setDeliveryLongitude(55.1403);
        order.setRecipientPhone("+971500000002");
        // One instance for every read, so the writes the service makes accumulate like rows would.
        when(orderRepo.findById(ORDER_ID)).thenAnswer(inv -> Optional.of(order));
        when(orderRepo.claimForQuiqupDispatch(eq(ORDER_ID), any(), any())).thenReturn(1);

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

        QuiqupCoverage coverage = mock(QuiqupCoverage.class);
        when(coverage.covers(any(), any())).thenReturn(true);

        service = new QuiqupDispatchService(props, client, new QuiqupOrderMapper(JSON, props),
                coverage, orderRepo, itemRepo, storeRepo, locationRepo,
                mock(PlatformTransactionManager.class));
    }

    private void quiqupAnswers(QuiqupApiResult result) {
        when(client.request(eq("POST"), any(), any())).thenReturn(result);
    }

    @Test
    void aRejectedJobIsSentOnceAndThenLeftForAnAdmin() throws Exception {
        quiqupAnswers(answered(422, "{\"attribute_errors\": {}}"));

        service.dispatch(ORDER_ID);

        verify(client, times(1)).request(eq("POST"), any(), any());
        assertEquals(1, order.getQuiqupDispatchAttempts());
        assertNotNull(order.getQuiqupDispatchStoppedAt(), "the retry job must not pick it up again");
        assertTrue(order.getQuiqupDispatchError().startsWith("Automatic retries stopped"));
        // Nothing was created, so an admin who fixes the order can dispatch it straight away.
        verify(orderRepo).releaseQuiqupDispatchClaim(ORDER_ID);
    }

    @Test
    void aTransientFailureIsRetriedUpToTheCapAndNoFurther() throws Exception {
        props.getDispatch().setMaxAttempts(3);
        quiqupAnswers(answered(503, "{}"));

        service.dispatch(ORDER_ID);
        service.dispatch(ORDER_ID);
        assertNull(order.getQuiqupDispatchStoppedAt(), "two of three attempts used; still retryable");

        service.dispatch(ORDER_ID);
        assertEquals(3, order.getQuiqupDispatchAttempts());
        assertNotNull(order.getQuiqupDispatchStoppedAt());
        assertTrue(order.getQuiqupDispatchError().contains("after 3 attempts"));
    }

    @Test
    void anUnansweredCreateStopsAndKeepsTheClaim() {
        quiqupAnswers(new QuiqupApiResult(502, false, "Connected but Quiqup sent no response", true));

        service.dispatch(ORDER_ID);

        assertNotNull(order.getQuiqupDispatchStoppedAt());
        assertTrue(order.getQuiqupDispatchError().contains("BUY-33F3C5CA"),
                "the admin needs the reference to search Quiqup for: " + order.getQuiqupDispatchError());
        verify(orderRepo, never()).releaseQuiqupDispatchClaim(any());
    }

    @Test
    void anAcceptedJobWithNoIdIsTreatedAsUnanswered() throws Exception {
        quiqupAnswers(answered(201, "{\"message\": \"created\"}"));

        service.dispatch(ORDER_ID);

        assertNull(order.getQuiqupOrderId());
        assertNotNull(order.getQuiqupDispatchStoppedAt());
        verify(orderRepo, never()).releaseQuiqupDispatchClaim(any());
    }

    @Test
    void aFailureAfterTheCreateWasSentNeverFreesTheOrder() throws Exception {
        quiqupAnswers(answered(201, "{\"id\": \"998877\"}"));
        // Recording Quiqup's id fails, as it would with the database briefly gone.
        when(orderRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getQuiqupOrderId() != null) throw new IllegalStateException("database unavailable");
            return o;
        });

        service.dispatch(ORDER_ID);

        verify(orderRepo, never()).releaseQuiqupDispatchClaim(any());
    }

    @Test
    void anAdminDispatchAfterAStopStartsAFreshAttemptAndSucceeds() throws Exception {
        order.setQuiqupDispatchAttempts(1);
        order.setQuiqupDispatchStoppedAt(Instant.now());
        order.setQuiqupDispatchError("Automatic retries stopped: Create call failed with 422: {}");
        quiqupAnswers(answered(201, "{\"id\": \"998877\"}"));

        service.dispatch(ORDER_ID);

        assertEquals("998877", order.getQuiqupOrderId());
        assertEquals(2, order.getQuiqupDispatchAttempts());
        assertNull(order.getQuiqupDispatchStoppedAt());
        assertNull(order.getQuiqupDispatchError());
    }

    @Test
    void anOrderAlreadyDispatchedIsNeverSentAgain() {
        order.setQuiqupOrderId("998877");

        service.dispatch(ORDER_ID);

        verifyNoInteractions(client);
        assertEquals(0, order.getQuiqupDispatchAttempts(), "no attempt is counted when nothing is sent");
    }
}
