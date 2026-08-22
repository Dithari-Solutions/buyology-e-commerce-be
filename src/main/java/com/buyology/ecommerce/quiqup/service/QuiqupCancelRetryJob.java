package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.order.service.OrderService;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Finishes courier cancellations that could not be confirmed in the moment.
 *
 * <p>An admin cancellation commits with the courier unconfirmed by design: the order is CANCELLED,
 * the refund, the B2B credit, the stock and the customer emails are all withheld, and the cancel
 * status sits at PENDING or UNCONFIRMED. Someone has to keep asking Quiqup until the answer is
 * definite — that is this job. The moment the job verifiably reads cancelled, the withheld side
 * effects are released through the same method every other path uses; a definite refusal or a dead
 * end has already been escalated to a superadmin by the cancel service.
 *
 * <p><strong>Cluster-safe by claim, not by luck.</strong> Two replicas both run this (no ShedLock),
 * but {@link OrderRepository#claimForQuiqupCancel} makes the check-and-claim one statement, so only
 * one of them talks to Quiqup about a given order at a time. The refund release behind it is
 * idempotent twice over: {@code cancelRefundInitiatedAt} short-circuits the email pair, and
 * RefundClaimStore counts PENDING refunds before any HTTP reaches Paymob.
 */
@Component
public class QuiqupCancelRetryJob {

    private static final Logger log = LoggerFactory.getLogger(QuiqupCancelRetryJob.class);

    /** Never work through more than this in one pass, so a backlog cannot monopolise the scheduler. */
    private static final int BATCH_LIMIT = 25;

    private final QuiqupProperties props;
    private final QuiqupCancelService cancelService;
    private final OrderService orderService;
    private final OrderRepository orderRepo;

    public QuiqupCancelRetryJob(QuiqupProperties props,
                                QuiqupCancelService cancelService,
                                OrderService orderService,
                                OrderRepository orderRepo) {
        this.props = props;
        this.cancelService = cancelService;
        this.orderService = orderService;
        this.orderRepo = orderRepo;
    }

    /**
     * Every minute by default — a customer whose refund is on hold is actively waiting, so this
     * runs an order of magnitude more often than the dispatch retry. Bounded by
     * quiqup.cancel.deadline-minutes: past that a courier is not waiting on us any more and the
     * case has already been escalated.
     */
    @Scheduled(fixedDelayString = "${quiqup.cancel.retry-interval-ms:60000}")
    public void finishUnconfirmedCancels() {
        if (!props.isEnabled() || !props.getCancel().isEnabled()) {
            return;
        }
        try {
            Instant horizon = Instant.now()
                    .minus(Duration.ofMinutes(props.getCancel().getDeadlineMinutes()));
            List<Order> pending = orderRepo.findOrdersNeedingQuiqupCancel(
                    horizon, PageRequest.of(0, BATCH_LIMIT));
            if (pending.isEmpty()) {
                return;
            }
            log.info("[QUIQUP] Retrying courier cancel for {} order(s)", pending.size());
            for (Order order : pending) {
                var result = cancelService.cancelForOrder(order.getId(), order.getCancellationReason());
                log.info("[QUIQUP] Cancel retry for order {}: {} ({})",
                        order.getId(), result.outcome(), result.detail());
                if (result.refundAllowed()) {
                    // The courier is now verifiably stopped: release everything the gate withheld —
                    // refund, B2B credit, stock, and the customer's emails. Through the injected
                    // proxy, so the method's REQUIRES_NEW actually applies.
                    orderService.applyCancellationSideEffects(
                            order, order.getCancellationReason(), true);
                }
            }
        } catch (Exception e) {
            // A scheduled job that throws is silently unscheduled in some setups; never let it.
            log.error("[QUIQUP] Cancel retry pass failed", e);
        }
    }
}
