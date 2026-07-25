package com.financedash.investments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.financedash.investments.domain.CashAccount;
import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.dto.BuyRequest;
import com.financedash.investments.messaging.InvestmentsMessaging;
import com.financedash.investments.provider.Quote;
import com.financedash.investments.provider.StockPriceProvider;
import com.financedash.investments.provider.SymbolNotFoundException;
import com.financedash.investments.provider.TransientProviderException;
import com.financedash.investments.repository.HoldingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Price job end-to-end: producer selection + consumer update + UNRESOLVED + DLQ→STALE. */
@SpringBootTest
class PriceRefreshIT extends com.financedash.investments.support.AbstractContainersTest {

    @Autowired private HoldingService service;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private PriceRefreshScheduler scheduler;
    @Autowired private RabbitTemplate rabbitTemplate;
    @MockBean private StockPriceProvider provider;

    private static final Instant T = Instant.parse("2026-07-24T12:00:00Z");

    @BeforeEach
    void clean() {
        holdingRepository.deleteAll();
    }

    private void publish(String symbol) {
        rabbitTemplate.convertAndSend(
                InvestmentsMessaging.PRICE_EXCHANGE, InvestmentsMessaging.PRICE_REFRESH_ROUTING_KEY, symbol);
    }

    private String buyAt(String symbol, String price) {
        when(provider.quote(any())).thenReturn(new Quote(new BigDecimal(price), T));
        return service.buy(new BuyRequest(symbol, new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();
    }

    private PriceStatus statusOf(String id) {
        return holdingRepository.findById(id).orElseThrow().getPriceStatus();
    }

    @Test
    void consumerAppliesFreshQuote() {
        String id = buyAt("AAPL", "10.00");
        // Reset so the refresh phase has one unambiguous stub (€20) regardless of prior invocations.
        org.mockito.Mockito.reset(provider);
        when(provider.quote(any())).thenReturn(new Quote(new BigDecimal("20.00"), T));

        publish("AAPL");

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            Holding h = holdingRepository.findById(id).orElseThrow();
            assertThat(h.getLatestPrice()).isEqualByComparingTo("20.0000");
            assertThat(h.getPriceStatus()).isEqualTo(PriceStatus.OK);
            assertThat(h.currentValue()).isEqualByComparingTo("200.00"); // 10 shares × 20
        });
    }

    @Test
    void unknownSymbolIsMarkedUnresolvedNotRetried() {
        String id = buyAt("AAPL", "10.00");
        when(provider.quote(any())).thenThrow(new SymbolNotFoundException("AAPL"));

        publish("AAPL");

        await().atMost(Duration.ofSeconds(8))
                .until(() -> statusOf(id) == PriceStatus.UNRESOLVED);
    }

    @Test
    void persistentFailureDeadLettersAndMarksStaleKeepingLastPrice() {
        String id = buyAt("AAPL", "10.00");
        when(provider.quote(any())).thenThrow(new TransientProviderException("429"));

        publish("AAPL");

        await().atMost(Duration.ofSeconds(12))
                .until(() -> statusOf(id) == PriceStatus.STALE);
        // Last-known price is preserved, not zeroed.
        assertThat(holdingRepository.findById(id).orElseThrow().getLatestPrice())
                .isEqualByComparingTo("10.0000");
    }

    @Test
    void dueSymbolsSkipsFreshAndUnresolved() {
        holdingRepository.save(open("FRESH", PriceStatus.OK, T));                       // priced now → skip
        holdingRepository.save(open("STALEISH", PriceStatus.OK, T.minus(Duration.ofHours(2)))); // old → due
        holdingRepository.save(open("NEVER", PriceStatus.OK, null));                    // no price → due
        holdingRepository.save(open("MANUAL", PriceStatus.UNRESOLVED, null));           // untracked → skip

        List<String> due = scheduler.dueSymbols();

        assertThat(due).containsExactlyInAnyOrder("STALEISH", "NEVER");
    }

    private Holding open(String symbol, PriceStatus priceStatus, Instant priceAsOf) {
        Holding h = new Holding(symbol);
        h.setQuantity(new BigDecimal("1.000000"));
        h.setStatus(HoldingStatus.OPEN);
        h.setPriceStatus(priceStatus);
        h.setPriceAsOf(priceAsOf);
        h.setLatestPrice(new BigDecimal("1.0000"));
        return h;
    }

    /** Fixed clock so the freshness-window test is deterministic. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(T, ZoneOffset.UTC);
        }
    }
}
