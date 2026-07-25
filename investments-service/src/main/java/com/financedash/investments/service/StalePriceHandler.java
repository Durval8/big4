package com.financedash.investments.service;

import com.financedash.investments.messaging.InvestmentsMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Dead-letter consumer for the price job. A message lands here after the main consumer exhausted its
 * retries for a symbol — meaning the provider is persistently failing. We keep the last-known price
 * and flag the holding STALE (surfaced with a warning) rather than zeroing its value.
 */
@Component
public class StalePriceHandler {

    private static final Logger log = LoggerFactory.getLogger(StalePriceHandler.class);

    private final HoldingService holdingService;

    public StalePriceHandler(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @RabbitListener(queues = InvestmentsMessaging.PRICE_REFRESH_DLQ)
    public void handleDeadLettered(String symbol) {
        log.warn("Price refresh for {} exhausted retries; keeping last price and marking STALE", symbol);
        holdingService.markSymbolStale(symbol);
    }
}
