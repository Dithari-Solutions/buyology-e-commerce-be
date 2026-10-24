package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.common.scheduling.SchedulerLock;
import com.buyology.ecommerce.order.event.OrderDeliveredEvent;
import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import com.buyology.ecommerce.warranty.repository.WarrantyCardRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WarrantyCardIssuerTest {

    private final WarrantyCardService cards = mock(WarrantyCardService.class);
    private final WarrantyCardRepository repository = mock(WarrantyCardRepository.class);
    private final SchedulerLock lock = mock(SchedulerLock.class);
    private final WarrantyCardIssuer issuer = new WarrantyCardIssuer(cards, repository, lock);

    @Test
    void aDeliveryIssuesItsOrdersCards() {
        UUID orderId = UUID.randomUUID();
        issuer.onOrderDelivered(new OrderDeliveredEvent(orderId));
        verify(cards).issueForOrder(orderId);
    }

    @Test
    void aFailureIsLeftForTheSweepAndNeverEscapesTheListener() {
        when(cards.issueForOrder(any())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> issuer.onOrderDelivered(new OrderDeliveredEvent(UUID.randomUUID())));
    }

    @Test
    void theSweepIssuesForEveryOrderStillOwedCardsAndOneFailureDoesNotStopTheRest() {
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(lock.claim(any(), any())).thenReturn(true);
        when(repository.findDeliveredOrderIdsMissingCards(any())).thenReturn(List.of(broken, fine));
        when(cards.issueForOrder(broken)).thenThrow(new IllegalStateException("bad product"));

        issuer.sweep();

        verify(cards).issueForOrder(broken);
        verify(cards).issueForOrder(fine);
    }

    @Test
    void theSweepLooksBackAWeek() {
        when(lock.claim(any(), any())).thenReturn(true);
        Instant before = Instant.now();
        issuer.sweep();
        Instant after = Instant.now();
        verify(repository).findDeliveredOrderIdsMissingCards(argThat(since ->
                !since.isBefore(before.minus(WarrantyCardIssuer.SWEEP_WINDOW))
                        && !since.isAfter(after.minus(WarrantyCardIssuer.SWEEP_WINDOW))));
    }

    @Test
    void theHostThatLosesTheHourDoesNothing() {
        when(lock.claim(any(), any())).thenReturn(false);
        issuer.sweep();
        verifyNoInteractions(repository, cards);

        when(lock.claim(any(), any())).thenThrow(new RuntimeException("rolled back at commit"));
        assertDoesNotThrow(issuer::sweep);
        verifyNoInteractions(repository, cards);
    }

    @Test
    void theOrderPageReaderGroupsCardsByItemInUnitOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(repository.findByOrderIdOrderByUnitIndexAsc(orderId)).thenReturn(List.of(
                card(first, 1, "DTH-AAAA-AAAA-AAAA"), card(second, 1, "DTH-CCCC-CCCC-CCCC"),
                card(first, 2, "DTH-BBBB-BBBB-BBBB")));
        OrderWarrantyReader reader = new OrderWarrantyReader(repository,
                Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC));

        var byItem = reader.forOrder(orderId);

        assertEquals(List.of("DTH-AAAA-AAAA-AAAA", "DTH-BBBB-BBBB-BBBB"),
                byItem.get(first).stream().map(w -> w.serialNumber()).toList());
        assertEquals(1, byItem.get(second).size());
        assertEquals("ACTIVE", byItem.get(first).get(0).status());
    }

    private static WarrantyCard card(UUID itemId, int unit, String serial) {
        WarrantyCard c = new WarrantyCard();
        c.setOrderItemId(itemId);
        c.setUnitIndex(unit);
        c.setSerialNumber(serial);
        c.setStartsOn(LocalDate.of(2026, 6, 24));
        c.setEndsOn(LocalDate.of(2027, 6, 24));
        return c;
    }
}
