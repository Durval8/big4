package com.financedash.investments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.investments.domain.OutboxMessage;
import com.financedash.investments.messaging.InvestmentsMessaging;
import com.financedash.investments.messaging.contract.CashLegCommand;
import com.financedash.investments.messaging.contract.ValueSnapshot;
import com.financedash.investments.repository.OutboxRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Enqueues outgoing messages into the Mongo outbox in the same call as the holding change. A
 * separate relay publishes them to RabbitMQ. Payloads are serialized to JSON here so the relay is
 * a dumb byte-pusher and the backend deserializes against its own mirror of the contract.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueueCashLeg(CashLegCommand command) {
        persist(InvestmentsMessaging.INVESTMENTS_EXCHANGE,
                InvestmentsMessaging.CASH_LEG_ROUTING_KEY, command);
    }

    public void enqueueValueSnapshot(ValueSnapshot snapshot) {
        persist(InvestmentsMessaging.INVESTMENTS_EXCHANGE,
                InvestmentsMessaging.VALUE_ROUTING_KEY, snapshot);
    }

    private void persist(String exchange, String routingKey, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
        outboxRepository.save(new OutboxMessage(exchange, routingKey, json, clock.instant()));
    }
}
