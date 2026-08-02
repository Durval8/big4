package com.financedash.investments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.investments.domain.CashAccount;
import com.financedash.investments.domain.InvestmentEventType;
import com.financedash.investments.messaging.InvestmentsMessaging;
import com.financedash.investments.messaging.contract.CashLegCommand;
import com.financedash.investments.messaging.contract.ValueSnapshot;
import com.financedash.investments.repository.OutboxRepository;
import com.financedash.investments.support.AbstractContainersTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Outbox → relay → RabbitMQ: the two streams route to the backend's queues, rows mark published. */
@SpringBootTest
// Receives via polling, not listeners; keep this context's consumers off (shared-broker isolation).
@org.springframework.test.context.TestPropertySource(
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class OutboxRelayIT extends AbstractContainersTest {

    @Autowired private OutboxWriter outboxWriter;
    @Autowired private OutboxRelay relay;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
        amqpAdmin.purgeQueue(InvestmentsMessaging.BACKEND_CASH_LEG_QUEUE, false);
        amqpAdmin.purgeQueue(InvestmentsMessaging.BACKEND_VALUE_QUEUE, false);
    }

    @Test
    void relayDeliversBothStreamsAndMarksPublished() throws Exception {
        outboxWriter.enqueueCashLeg(CashLegCommand.of(
                "evt-1", InvestmentEventType.FUND.name(), CashAccount.CHECKING.name(),
                new BigDecimal("100.00"), LocalDate.of(2026, 7, 24), "AAPL"));
        outboxWriter.enqueueValueSnapshot(ValueSnapshot.of(new BigDecimal("100.00"), Instant.now()));

        relay.publishPending();

        Message cashLegMsg = rabbitTemplate.receive(InvestmentsMessaging.BACKEND_CASH_LEG_QUEUE, 5000);
        Message valueMsg = rabbitTemplate.receive(InvestmentsMessaging.BACKEND_VALUE_QUEUE, 5000);
        assertThat(cashLegMsg).isNotNull();
        assertThat(valueMsg).isNotNull();

        CashLegCommand cashLeg = objectMapper.readValue(cashLegMsg.getBody(), CashLegCommand.class);
        assertThat(cashLeg.eventId()).isEqualTo("evt-1");
        assertThat(cashLeg.legType()).isEqualTo("FUND");
        assertThat(cashLeg.account()).isEqualTo("CHECKING");
        assertThat(cashLeg.amount()).isEqualByComparingTo("100.00");
        assertThat(cashLeg.stockSymbol()).isEqualTo("AAPL");
        assertThat(cashLeg.schemaVersion()).isEqualTo(InvestmentsMessaging.SCHEMA_VERSION);

        ValueSnapshot snapshot = objectMapper.readValue(valueMsg.getBody(), ValueSnapshot.class);
        assertThat(snapshot.netValue()).isEqualByComparingTo("100.00");
        assertThat(snapshot.type()).isEqualTo(ValueSnapshot.TYPE);

        // All rows now marked published.
        assertThat(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).isEmpty();
    }

    @Test
    void publishedRowsAreNotResent() {
        outboxWriter.enqueueValueSnapshot(ValueSnapshot.of(new BigDecimal("42.00"), Instant.now()));
        relay.publishPending();
        assertThat(rabbitTemplate.receive(InvestmentsMessaging.BACKEND_VALUE_QUEUE, 5000)).isNotNull();

        // Second sweep has nothing to do → no duplicate on the queue.
        relay.publishPending();
        assertThat(rabbitTemplate.receive(InvestmentsMessaging.BACKEND_VALUE_QUEUE, 1000)).isNull();
    }
}
