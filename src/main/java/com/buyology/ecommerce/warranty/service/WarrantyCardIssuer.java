package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.common.scheduling.SchedulerLock;
import com.buyology.ecommerce.order.event.OrderDeliveredEvent;
import com.buyology.ecommerce.warranty.repository.WarrantyCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * When warranty cards get issued: as soon as an order is delivered, and again by an hourly sweep
 * for any delivery that slipped through.
 *
 * <p>The listener runs after the delivery commits and off the request thread, so a problem with
 * warranties can never fail or slow the status change that a courier or webhook is waiting on. The
 * price is that an event can be lost — the host restarts between commit and listener, or the insert
 * fails. The sweep covers that: it re-runs issuing for recent deliveries still short of cards, which
 * is safe because issuing is idempotent.
 */
@Component
public class WarrantyCardIssuer {

    private static final Logger log = LoggerFactory.getLogger(WarrantyCardIssuer.class);

    /**
     * How far back the sweep looks. Long enough to outlast an outage; short enough that setting a
     * warranty on a product today does not issue cards for every unit it ever sold.
     */
    static final Duration SWEEP_WINDOW = Duration.ofDays(7);

    private final WarrantyCardService cards;
    private final WarrantyCardRepository repository;
    private final SchedulerLock lock;

    public WarrantyCardIssuer(WarrantyCardService cards, WarrantyCardRepository repository, SchedulerLock lock) {
        this.cards = cards;
        this.repository = repository;
        this.lock = lock;
    }

    // fallbackExecution: a path that delivers outside a transaction still gets its cards here
    // rather than waiting for the sweep.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        try {
            cards.issueForOrder(event.getOrderId());
        } catch (Exception e) {
            log.warn("[WARRANTY] Issuing cards for delivered order {} failed; the hourly sweep will retry: {}",
                    event.getOrderId(), e.toString());
        }
    }

    /**
     * Once an hour, once across both hosts — the hour goes into the task name, as in
     * CartReminderScheduler, because SchedulerLock is day-granular.
     */
    @Scheduled(cron = "0 35 * * * *", zone = "UTC")
    public void sweep() {
        Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
        String task = "warranty-card-sweep-" + hour.atZone(ZoneOffset.UTC).getHour();
        try {
            if (!lock.claim(task, hour.atZone(ZoneOffset.UTC).toLocalDate())) return;
        } catch (Exception losingTheRace) {
            log.debug("[WARRANTY] sweep {} is claimed by the other host", task);
            return;
        }

        List<UUID> owed;
        try {
            owed = repository.findDeliveredOrderIdsMissingCards(Instant.now().minus(SWEEP_WINDOW));
        } catch (Exception e) {
            log.error("[WARRANTY] sweep query failed: {}", e.toString(), e);
            return;
        }
        int issued = 0;
        for (UUID orderId : owed) {
            try {
                issued += cards.issueForOrder(orderId);
            } catch (Exception e) {
                log.warn("[WARRANTY] sweep could not issue cards for order {}: {}", orderId, e.toString());
            }
        }
        if (issued > 0) {
            log.info("[WARRANTY] sweep issued {} card(s) across {} order(s)", issued, owed.size());
        }
    }
}
