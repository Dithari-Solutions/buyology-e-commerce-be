package com.buyology.ecommerce.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherJob(OutboxEventRepository outboxEventRepository,
                              RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findAndLockPendingBatch(BATCH_SIZE);
        if (pending.isEmpty()) return;

        log.debug("[Outbox] Processing {} pending event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setHeader("eventVersion", event.getEventVersion())
                        .build();

                rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message);

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                log.debug("[Outbox] Published [{}] id={}", event.getRoutingKey(), event.getId());

            } catch (Exception ex) {
                int newRetryCount = event.getRetryCount() + 1;
                event.setRetryCount(newRetryCount);

                if (newRetryCount >= MAX_RETRIES) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("ALERT: Outbox event {} [{}] failed permanently after {} retries — manual replay required. Error: {}",
                            event.getId(), event.getRoutingKey(), newRetryCount, ex.getMessage());
                } else {
                    log.warn("[Outbox] Publish failed for event {} [{}], retry {}/{}. Error: {}",
                            event.getId(), event.getRoutingKey(), newRetryCount, MAX_RETRIES, ex.getMessage());
                }
            }
        }

        outboxEventRepository.saveAll(pending);
    }
}
