package com.financedash.investments.service;

import com.financedash.investments.domain.OutboxMessage;
import com.financedash.investments.repository.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes unsent outbox rows to RabbitMQ on a fixed interval, then marks them published.
 * At-least-once: a crash between publish and mark re-sends on the next sweep, which the backend
 * de-dupes (cash legs by eventId) or absorbs (snapshots overwrite). Payloads are already JSON;
 * they go out as raw {@code application/json} bodies so the relay stays contract-agnostic.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:2000}")
    public void publishPending() {
        List<OutboxMessage> pending = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxMessage m : pending) {
            try {
                Message message = MessageBuilder
                        .withBody(m.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build();
                rabbitTemplate.send(m.getExchange(), m.getRoutingKey(), message);
                m.markPublished(clock.instant());
                outboxRepository.save(m);
            } catch (RuntimeException ex) {
                // Leave unpublished; the next sweep retries. Don't let one bad row block the rest.
                log.warn("Outbox publish failed for {} (will retry): {}", m.getId(), ex.getMessage());
            }
        }
    }
}
