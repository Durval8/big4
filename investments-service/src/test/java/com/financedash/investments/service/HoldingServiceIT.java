package com.financedash.investments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.financedash.investments.domain.CashAccount;
import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.dto.BuyRequest;
import com.financedash.investments.dto.CashOutRequest;
import com.financedash.investments.dto.HoldingResponse;
import com.financedash.investments.dto.HoldingUpdateRequest;
import com.financedash.investments.dto.ManualPriceRequest;
import com.financedash.investments.dto.SummaryResponse;
import com.financedash.investments.exception.InvalidInvestmentException;
import com.financedash.investments.exception.ProviderUnavailableException;
import com.financedash.investments.messaging.InvestmentsMessaging;
import com.financedash.investments.provider.Quote;
import com.financedash.investments.provider.StockPriceProvider;
import com.financedash.investments.provider.SymbolNotFoundException;
import com.financedash.investments.provider.TransientProviderException;
import com.financedash.investments.repository.HoldingRepository;
import com.financedash.investments.repository.OutboxRepository;
import com.financedash.investments.support.AbstractContainersTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** End-to-end holding accounting against real Mongo; provider mocked, clock fixed. */
@SpringBootTest
// No message consumers needed here; keep this context's listeners off so they don't compete with
// PriceRefreshIT's consumer on the shared broker (Spring caches contexts across test classes).
@org.springframework.test.context.TestPropertySource(
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class HoldingServiceIT extends AbstractContainersTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private HoldingService service;
    @Autowired
    private HoldingRepository holdingRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @MockBean
    private StockPriceProvider provider;
    @MockBean
    private NewsRefreshPublisher newsRefreshPublisher;

    @BeforeEach
    void clean() {
        holdingRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    private void quoteReturns(String price) {
        when(provider.quote(any()))
                .thenReturn(new Quote(new BigDecimal(price), Instant.parse("2026-07-24T12:00:00Z")));
    }

    @Test
    void buyDerivesSharesAndWritesCashLegAndSnapshot() {
        quoteReturns("10.00");
        HoldingResponse r = service.buy(new BuyRequest("aapl", new BigDecimal("100.00"), CashAccount.CHECKING, null));

        assertThat(r.stockSymbol()).isEqualTo("AAPL");
        assertThat(r.quantity()).isEqualByComparingTo("10");        // 100 / 10
        assertThat(r.avgCost()).isEqualByComparingTo("10.0000");
        assertThat(r.currentValue()).isEqualByComparingTo("100.00");
        assertThat(r.netCashInvested()).isEqualByComparingTo("100.00");
        assertThat(r.positionChangePct()).isEqualByComparingTo("0.00"); // price == avgCost
        assertThat(r.priceStatus()).isEqualTo(PriceStatus.OK);

        // One FUND cash-leg + one value snapshot in the outbox.
        long cashLegs = outboxRepository.findAll().stream()
                .filter(m -> m.getRoutingKey().equals(InvestmentsMessaging.CASH_LEG_ROUTING_KEY)).count();
        long snapshots = outboxRepository.findAll().stream()
                .filter(m -> m.getRoutingKey().equals(InvestmentsMessaging.VALUE_ROUTING_KEY)).count();
        assertThat(cashLegs).isEqualTo(1);
        assertThat(snapshots).isEqualTo(1);
    }

    @Test
    void workedExample_buyRiseCashOut() {
        // Buy 10 shares @ €10.
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();

        // Price rises to €15 (as the refresh job would set it).
        Holding h = holdingRepository.findById(id).orElseThrow();
        h.setLatestPrice(new BigDecimal("15.0000"));
        holdingRepository.save(h);

        HoldingResponse afterRise = service.get(id);
        assertThat(afterRise.currentValue()).isEqualByComparingTo("150.00");
        assertThat(afterRise.positionChangePct()).isEqualByComparingTo("50.00");

        // Cash out 50% (5 of 10 shares) → realized gain €25, avg cost unchanged.
        HoldingResponse afterCashOut = service.cashOut(id, new CashOutRequest(new BigDecimal("50")));
        assertThat(afterCashOut.quantity()).isEqualByComparingTo("5");
        assertThat(afterCashOut.netCashInvested()).isEqualByComparingTo("25.00");
        assertThat(afterCashOut.realizedGain()).isEqualByComparingTo("25.00");
        assertThat(afterCashOut.avgCost()).isEqualByComparingTo("10.0000");
        assertThat(afterCashOut.positionChangePct()).isEqualByComparingTo("50.00"); // 15 vs 10
        assertThat(afterCashOut.status()).isEqualTo(HoldingStatus.OPEN);
    }

    @Test
    void fullCashOutClosesTheHolding() {
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("MSFT", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();

        HoldingResponse closed = service.cashOut(id, new CashOutRequest(new BigDecimal("100")));
        assertThat(closed.status()).isEqualTo(HoldingStatus.CASHED_OUT);
        assertThat(closed.quantity()).isEqualByComparingTo("0");
        assertThat(closed.currentValue()).isEqualByComparingTo("0.00");
        assertThat(closed.positionChangePct()).isNull();
        assertThat(closed.avgCost()).isNull();
    }

    @Test
    void fullCashOutRemovesTheHoldingFromList() {
        quoteReturns("10.00");
        String closedId = service.buy(new BuyRequest("MSFT", new BigDecimal("100.00"), CashAccount.CHECKING, null))
                .id();
        service.buy(new BuyRequest("AAPL", new BigDecimal("50.00"), CashAccount.CHECKING, null));

        service.cashOut(closedId, new CashOutRequest(new BigDecimal("100")));

        assertThat(service.list())
                .extracting(HoldingResponse::stockSymbol)
                .containsExactly("AAPL")
                .doesNotContain("MSFT");
        // The cashed-out holding still exists (kept as history), just excluded from the active list.
        assertThat(holdingRepository.findById(closedId)).isPresent();
    }

    @Test
    void buyingSameSymbolMergesAndAveragesCost() {
        when(provider.quote(any()))
                .thenReturn(new Quote(new BigDecimal("10.00"), Instant.parse("2026-07-24T12:00:00Z")))
                .thenReturn(new Quote(new BigDecimal("20.00"), Instant.parse("2026-07-24T12:00:00Z")));

        service.buy(new BuyRequest("NVDA", new BigDecimal("100.00"), CashAccount.CHECKING, null)); // 10 @ 10
        HoldingResponse merged = service.buy(
                new BuyRequest("nvda", new BigDecimal("100.00"), CashAccount.SAVINGS, null)); // 5 @ 20

        assertThat(merged.quantity()).isEqualByComparingTo("15");        // 10 + 5
        assertThat(merged.netCashInvested()).isEqualByComparingTo("200.00");
        assertThat(merged.avgCost()).isEqualByComparingTo("13.3333");    // 200 / 15
        assertThat(holdingRepository.findByStatus(HoldingStatus.OPEN)).hasSize(1);
    }

    @Test
    void unknownSymbolWithoutManualPriceIsRejected() {
        when(provider.quote(any())).thenThrow(new SymbolNotFoundException("ZZZZ"));
        assertThatThrownBy(() -> service.buy(new BuyRequest("ZZZZ", new BigDecimal("100.00"), CashAccount.CHECKING, null)))
                .isInstanceOf(InvalidInvestmentException.class);
    }

    @Test
    void unknownSymbolWithManualPriceIsUnresolvedAndPriceableByHand() {
        when(provider.quote(any())).thenThrow(new SymbolNotFoundException("ZZZZ"));
        HoldingResponse r = service.buy(
                new BuyRequest("ZZZZ", new BigDecimal("100.00"), CashAccount.CHECKING, new BigDecimal("25.00")));
        assertThat(r.priceStatus()).isEqualTo(PriceStatus.UNRESOLVED);
        assertThat(r.quantity()).isEqualByComparingTo("4"); // 100 / 25

        // A manual price update is allowed (and only) for UNRESOLVED holdings.
        HoldingResponse repriced = service.setManualPrice(r.id(), new ManualPriceRequest(new BigDecimal("30.00")));
        assertThat(repriced.currentValue()).isEqualByComparingTo("120.00"); // 4 * 30
    }

    @Test
    void manualPriceRejectedForResolvedHolding() {
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();
        assertThatThrownBy(() -> service.setManualPrice(id, new ManualPriceRequest(new BigDecimal("11.00"))))
                .isInstanceOf(InvalidInvestmentException.class);
    }

    @Test
    void percentageRoundingUpToTheFullQuantityStillClosesCleanly() {
        // A percentage just under 100, with more precision than the division's rounding scale can
        // hold, rounds up to exactly the held quantity. Without the clamp this would fall into the
        // "partial" branch, subtract to a computed zero, and leave the holding OPEN with a zeroed
        // quantity instead of properly CASHED_OUT.
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();

        HoldingResponse closed = service.cashOut(id, new CashOutRequest(new BigDecimal("99.999999999")));
        assertThat(closed.status()).isEqualTo(HoldingStatus.CASHED_OUT);
        assertThat(closed.quantity()).isEqualByComparingTo("0");
        assertThat(closed.avgCost()).isNull();
    }

    @Test
    void transientProviderFailureBlocksTheBuy() {
        when(provider.quote(any())).thenThrow(new TransientProviderException("429"));
        assertThatThrownBy(() -> service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(holdingRepository.findAll()).isEmpty(); // nothing persisted
    }

    @Test
    void nonTransientProviderFailureBlocksTheBuyCleanly() {
        // e.g. a rejected/missing API key (Finnhub 401) → base ProviderException, not transient.
        when(provider.quote(any())).thenThrow(new com.financedash.investments.provider.ProviderException("401"));
        assertThatThrownBy(() -> service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(holdingRepository.findAll()).isEmpty();
    }

    @Test
    void cashOutOverOneHundredPercentIsRejected() {
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("GOOG", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();
        assertThatThrownBy(() -> service.cashOut(id, new CashOutRequest(new BigDecimal("101"))))
                .isInstanceOf(InvalidInvestmentException.class);
    }

    @Test
    void cashOutOfZeroOrNegativePercentIsRejected() {
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("GOOG", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();
        assertThatThrownBy(() -> service.cashOut(id, new CashOutRequest(BigDecimal.ZERO)))
                .isInstanceOf(InvalidInvestmentException.class);
    }

    @Test
    void fullCashOutIsImmuneToAPriceChangeBetweenRequestAndExecution() {
        // Regression test for the bug this percentage-based design replaced: the old
        // amount-vs-currentValue equality check raced against the price-refresh job, so a price
        // update landing between a client reading currentValue and the cash-out request being
        // handled could silently leave a nonzero "dust" quantity instead of fully closing.
        // percentage=100 has no such race -- it reads straight from the live quantity.
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();

        // Simulate the periodic price-refresh job firing after the client last saw the position
        // but before the cash-out request is handled.
        Holding h = holdingRepository.findById(id).orElseThrow();
        h.setLatestPrice(new BigDecimal("16.0000"));
        holdingRepository.save(h);

        HoldingResponse closed = service.cashOut(id, new CashOutRequest(new BigDecimal("100")));
        assertThat(closed.status()).isEqualTo(HoldingStatus.CASHED_OUT);
        assertThat(closed.quantity()).isEqualByComparingTo("0");
        // Proceeds reflect the fresh price at execution time (10 shares x €16), not any value the
        // client might have seen before the refresh.
        assertThat(closed.netCashInvested()).isEqualByComparingTo("-60.00"); // 100 invested - 160 proceeds
        assertThat(closed.realizedGain()).isEqualByComparingTo("60.00");    // 160 proceeds - 100 cost basis
    }

    @Test
    void partialCashOutIsProportionalToLiveQuantityRegardlessOfPrice() {
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();

        Holding h = holdingRepository.findById(id).orElseThrow();
        h.setLatestPrice(new BigDecimal("20.0000"));
        holdingRepository.save(h);

        // 25% of 10 shares is exactly 2.5 shares, regardless of what the price did in between.
        HoldingResponse after = service.cashOut(id, new CashOutRequest(new BigDecimal("25")));
        assertThat(after.quantity()).isEqualByComparingTo("7.5");
        assertThat(after.status()).isEqualTo(HoldingStatus.OPEN);
    }

    @Test
    void summaryAggregatesOpenHoldings() {
        quoteReturns("10.00");
        service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null));
        service.buy(new BuyRequest("MSFT", new BigDecimal("50.00"), CashAccount.SAVINGS, null));

        SummaryResponse s = service.summary();
        assertThat(s.totalNetInvested()).isEqualByComparingTo("150.00");
        assertThat(s.totalCurrentValue()).isEqualByComparingTo("150.00");
        assertThat(s.positionChangePct()).isEqualByComparingTo("0.00");
    }

    // --- news-refresh triggers on held-set changes ---

    @Test
    void newSymbolBuyTriggersNewsRefresh() {
        quoteReturns("10.00");
        service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null));
        org.mockito.Mockito.verify(newsRefreshPublisher, org.mockito.Mockito.times(1)).requestRebuild();
    }

    @Test
    void mergingBuyDoesNotTriggerNewsRefresh() {
        quoteReturns("10.00");
        service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)); // new
        org.mockito.Mockito.clearInvocations(newsRefreshPublisher);
        service.buy(new BuyRequest("AAPL", new BigDecimal("50.00"), CashAccount.SAVINGS, null));    // merge
        org.mockito.Mockito.verify(newsRefreshPublisher, org.mockito.Mockito.never()).requestRebuild();
    }

    @Test
    void fullCashOutTriggersNewsRefresh() {
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();
        org.mockito.Mockito.clearInvocations(newsRefreshPublisher);
        service.cashOut(id, new CashOutRequest(new BigDecimal("100"))); // full close
        org.mockito.Mockito.verify(newsRefreshPublisher, org.mockito.Mockito.times(1)).requestRebuild();
    }

    @Test
    void partialCashOutDoesNotTriggerNewsRefresh() {
        quoteReturns("10.00");
        String id = service.buy(new BuyRequest("AAPL", new BigDecimal("100.00"), CashAccount.CHECKING, null)).id();
        org.mockito.Mockito.clearInvocations(newsRefreshPublisher);
        service.cashOut(id, new CashOutRequest(new BigDecimal("40"))); // partial
        org.mockito.Mockito.verify(newsRefreshPublisher, org.mockito.Mockito.never()).requestRebuild();
    }
}
