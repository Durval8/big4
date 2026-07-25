package com.financedash.investments.service;

import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.messaging.InvestmentsMessaging;
import com.financedash.investments.repository.HoldingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Producer half of the self-contained price job: on a cron tick, enqueue one refresh message per
 * distinct held symbol that is due (skipping symbols priced within the freshness window, and
 * UNRESOLVED symbols which are never fetched). The rate-limited consumer drains the queue. Doing no
 * fetching here keeps the scheduler cheap and overlapping ticks harmless.
 */
@Component
public class PriceRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshScheduler.class);

    private final HoldingRepository holdingRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final Duration freshnessWindow;

    public PriceRefreshScheduler(HoldingRepository holdingRepository,
                                 RabbitTemplate rabbitTemplate,
                                 Clock clock,
                                 @Value("${pricing.freshness-window-minutes:14}") long freshnessMinutes) {
        this.holdingRepository = holdingRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.freshnessWindow = Duration.ofMinutes(freshnessMinutes);
    }

    @Scheduled(cron = "${pricing.refresh-cron:0 */15 * * * *}")
    public void enqueueRefreshes() {
        List<String> symbols = dueSymbols();
        for (String symbol : symbols) {
            rabbitTemplate.convertAndSend(
                    InvestmentsMessaging.PRICE_EXCHANGE, InvestmentsMessaging.PRICE_REFRESH_ROUTING_KEY, symbol);
        }
        if (!symbols.isEmpty()) {
            log.debug("Enqueued price refresh for {} symbol(s)", symbols.size());
        }
    }

    /** Distinct OPEN, provider-tracked symbols whose price is missing or older than the window. */
    public List<String> dueSymbols() {
        Instant cutoff = clock.instant().minus(freshnessWindow);
        Set<String> symbols = new LinkedHashSet<>();
        for (Holding h : holdingRepository.findByStatusAndPriceStatusNot(
                HoldingStatus.OPEN, PriceStatus.UNRESOLVED)) {
            if (h.getPriceAsOf() == null || h.getPriceAsOf().isBefore(cutoff)) {
                symbols.add(h.getStockSymbol());
            }
        }
        return List.copyOf(symbols);
    }
}
