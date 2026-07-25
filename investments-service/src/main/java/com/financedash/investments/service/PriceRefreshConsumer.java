package com.financedash.investments.service;

import com.financedash.investments.messaging.InvestmentsMessaging;
import com.financedash.investments.provider.Quote;
import com.financedash.investments.provider.StockPriceProvider;
import com.financedash.investments.provider.SymbolNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer half of the price job. Fetches one symbol's quote (through the provider's shared rate
 * limiter) and applies it. An unrecognized symbol is flagged UNRESOLVED and acked (never retried).
 * A transient failure is rethrown so the container's retry/back-off runs and, on exhaustion,
 * dead-letters the message — where {@link StalePriceHandler} keeps the last price and marks it STALE.
 */
@Component
public class PriceRefreshConsumer {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshConsumer.class);

    private final StockPriceProvider priceProvider;
    private final HoldingService holdingService;

    public PriceRefreshConsumer(StockPriceProvider priceProvider, HoldingService holdingService) {
        this.priceProvider = priceProvider;
        this.holdingService = holdingService;
    }

    @RabbitListener(queues = InvestmentsMessaging.PRICE_REFRESH_QUEUE)
    public void handle(String symbol) {
        try {
            Quote quote = priceProvider.quote(symbol);
            holdingService.applyPrice(symbol, quote.price(), quote.asOf());
            log.info("Refreshed {} @ {} (asOf {})", symbol, quote.price(), quote.asOf());
        } catch (SymbolNotFoundException notFound) {
            log.info("Symbol {} not recognized by provider; marking UNRESOLVED", symbol);
            holdingService.markSymbolUnresolved(symbol);
        }
        // TransientProviderException (and other ProviderExceptions) propagate → retry → DLQ.
    }
}
