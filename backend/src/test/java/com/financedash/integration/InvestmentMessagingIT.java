package com.financedash.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.financedash.domain.InvestmentValuation;
import com.financedash.messaging.InvestmentsMessaging;
import com.financedash.repository.InvestmentCashFlowRepository;
import com.financedash.repository.InvestmentValuationRepository;
import com.financedash.support.AbstractMessagingContainerTest;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end backend receive path: a raw JSON message on the exchange (as the service's relay sends
 * it — no type header) is converted to the mirror record and persisted. Exercises the exact
 * conversion the default converter would have failed at. This is the only backend IT with listeners
 * on, so it doesn't compete with other contexts on the shared broker.
 */
@SpringBootTest
class InvestmentMessagingIT extends AbstractMessagingContainerTest {

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private InvestmentCashFlowRepository cashFlowRepository;
    @Autowired private InvestmentValuationRepository valuationRepository;

    @BeforeEach
    void clean() {
        cashFlowRepository.deleteAll();
        valuationRepository.deleteAll();
    }

    private void publish(String routingKey, String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(InvestmentsMessaging.INVESTMENTS_EXCHANGE, routingKey, message);
    }

    @Test
    void cashLegMessageIsConvertedAndPersisted() {
        publish(InvestmentsMessaging.CASH_LEG_ROUTING_KEY, """
                {"schemaVersion":1,"type":"CASH_LEG","eventId":"evt-e2e","legType":"FUND",
                 "account":"CHECKING","amount":250.00,"date":"2026-07-24"}
                """);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> cashFlowRepository.existsById("evt-e2e"));
        assertThat(cashFlowRepository.findById("evt-e2e").orElseThrow().getAmount())
                .isEqualByComparingTo("250.00");
    }

    @Test
    void valueSnapshotMessageIsConvertedAndPersisted() {
        publish(InvestmentsMessaging.VALUE_ROUTING_KEY, """
                {"schemaVersion":1,"type":"VALUE_SNAPSHOT","netValue":999.99,"asOf":"2026-07-24T12:00:00Z"}
                """);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            InvestmentValuation v = valuationRepository.findById(InvestmentValuation.SINGLETON_ID).orElse(null);
            assertThat(v).isNotNull();
            assertThat(v.getNetValue()).isEqualByComparingTo("999.99");
        });
    }
}
