package com.buyology.ecommerce.common.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);
    private static final int BATCH_SIZE  = 50;
    private static final int MAX_RETRIES = 5;
    private static final long CONFIRM_TIMEOUT_MS = 5000;
    private static final String FAILED_COUNTER = "outbox.events.failed";

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public OutboxPublisherJob(OutboxEventRepository outboxEventRepository,
                              RabbitTemplate rabbitTemplate,
                              ApplicationEventPublisher applicationEventPublisher,
                              ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.applicationEventPublisher = applicationEventPublisher;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findAndLockPendingBatch(BATCH_SIZE);
        if (pending.isEmpty()) return;

        log.info("[Outbox] Processing {} pending event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setHeader("eventVersion", event.getEventVersion())
                        .build();

                publishWithConfirm(event, message);

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                log.info("[Outbox] Published [{}] id={}", event.getRoutingKey(), event.getId());

            } catch (Exception ex) {
                handlePublishFailure(event, ex);
            }
        }

        outboxEventRepository.saveAll(pending);
    }

    /**
     * Publishes the message and blocks until the broker confirms receipt. If the
     * broker never ACKs (e.g. it accepted then dropped the message, or a nack is
     * returned) this throws, so the event is NOT marked PUBLISHED on a silent drop.
     *
     * <p>Requires correlated publisher confirms on the connection factory
     * (spring.rabbitmq.publisher-confirm-type=correlated). If confirms are not
     * enabled, {@code waitForConfirmsOrDie} returns immediately and behaviour
     * degrades gracefully to the previous fire-and-forget semantics.</p>
     */
    private void publishWithConfirm(OutboxEvent event, Message message) {
        rabbitTemplate.invoke(operations -> {
            operations.send(event.getExchange(), event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }

    private void handlePublishFailure(OutboxEvent event, Exception ex) {
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);

        if (newRetryCount >= MAX_RETRIES) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("[Outbox] DEAD-LETTER: event permanently FAILED — manual replay required. "
                            + "eventId={} eventType={} exchange={} retryCount={} error={}",
                    event.getId(), event.getRoutingKey(), event.getExchange(),
                    newRetryCount, ex.getMessage(), ex);

            recordFailureMetric(event);
            publishFailedApplicationEvent(event, ex);
        } else {
            log.warn("[Outbox] Publish failed for event {} [{}], retry {}/{}. Error: {}",
                    event.getId(), event.getRoutingKey(), newRetryCount, MAX_RETRIES, ex.getMessage());
        }
    }

    private void recordFailureMetric(OutboxEvent event) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(FAILED_COUNTER, "eventType", event.getRoutingKey()).increment();
        }
    }

    private void publishFailedApplicationEvent(OutboxEvent event, Exception ex) {
        applicationEventPublisher.publishEvent(new OutboxEventFailedEvent(
                event.getId(),
                event.getRoutingKey(),
                event.getExchange(),
                event.getRetryCount(),
                ex.getMessage()));
    }
}
