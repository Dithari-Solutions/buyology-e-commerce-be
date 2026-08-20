package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Catches paid orders that Quiqup never accepted.
 *
 * <p>The dispatch itself is fire-and-forget off an application event, which means a restart mid-call,
 * a network blip, or Quiqup being briefly down loses that attempt with nothing to notice it. Without
 * this job the failure mode is the worst one available: the customer has paid, the order looks
 * healthy, and no courier is ever coming.
 *
 * <p>Deliberately narrow about what it retries — an undispatched order is a real delivery that has
 * not started, so the bias is toward trying again, but not forever and not for orders whose refusal
 * is permanent (a multi-store order will be refused identically on every attempt until a human
 * splits it). {@code maxAttempts} is approximated by the presence of an error string rather than an
 * attempt counter: an order that keeps failing keeps its error, and the horizon stops it eventually.
 *
 * <p><strong>Not cluster-safe.</strong> This repo has no ShedLock, so with two app replicas both
 * will run this job. That is tolerable only because {@link QuiqupDispatchService#dispatch} skips an
 * order that already carries a Quiqup id — two replicas racing the same fresh order could still
 * both create a job. The window is small and the retry interval is minutes, but it is real: before
 * enabling dispatch on more than one replica, give this a lock (the outbox does it with
 * {@code SELECT … FOR UPDATE SKIP LOCKED}).
 */
@Component
public class QuiqupDispatchRetryJob {

    private static final Logger log = LoggerFactory.getLogger(QuiqupDispatchRetryJob.class);

    /** Never work through more than this in one pass, so a backlog cannot monopolise the scheduler. */
    private static final int BATCH_LIMIT = 25;

    private final QuiqupProperties props;
    private final QuiqupDispatchService dispatchService;
    private final OrderRepository orderRepo;

    public QuiqupDispatchRetryJob(QuiqupProperties props,
                                  QuiqupDispatchService dispatchService,
                                  OrderRepository orderRepo) {
        this.props = props;
        this.dispatchService = dispatchService;
        this.orderRepo = orderRepo;
    }

    /**
     * Every five minutes. Frequent enough that a transient outage costs one delivery slot rather
     * than a day, infrequent enough that a persistent Quiqup failure is not a self-inflicted flood.
     */
    @Scheduled(fixedDelayString = "${quiqup.dispatch.retry-interval-ms:300000}")
    public void retryUndispatched() {
        if (!dispatchService.enabled()) {
            return;
        }
        try {
            Instant now = Instant.now();
            Instant olderThan = now.minus(Duration.ofMinutes(props.getDispatch().getRetryAfterMinutes()));
            Instant horizon = now.minus(Duration.ofHours(props.getDispatch().getRetryHorizonHours()));

            List<Order> stuck = orderRepo.findUndispatchedQuiqupOrders(
                    olderThan, horizon, org.springframework.data.domain.PageRequest.of(0, BATCH_LIMIT));
            if (stuck.isEmpty()) {
                return;
            }
            log.info("[QUIQUP] Retrying dispatch for {} undispatched paid order(s)", stuck.size());
            for (Order order : stuck) {
                String outcome = dispatchService.dispatch(order.getId());
                log.info("[QUIQUP] Retry for order {}: {}", order.getId(), outcome);
            }
        } catch (Exception e) {
            // A scheduled job that throws is silently unscheduled in some setups; never let it.
            log.error("[QUIQUP] Dispatch retry pass failed", e);
        }
    }
}
