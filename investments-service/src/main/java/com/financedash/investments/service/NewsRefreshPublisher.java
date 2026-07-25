package com.financedash.investments.service;

import com.financedash.investments.messaging.InvestmentsMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a news-refresh trigger. Direct, best-effort publish (not the outbox): news is
 * non-financial and a rebuild is idempotent, so a lost trigger self-heals at the next 4h tick.
 */
@Component
public class NewsRefreshPublisher {

    private static final Logger log = LoggerFactory.getLogger(NewsRefreshPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NewsRefreshPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void requestRebuild() {
        try {
            rabbitTemplate.convertAndSend(
                    InvestmentsMessaging.NEWS_EXCHANGE, InvestmentsMessaging.NEWS_REFRESH_ROUTING_KEY, "rebuild");
        } catch (RuntimeException ex) {
            // Best-effort: a broker hiccup here just means the next scheduled tick rebuilds.
            log.warn("Failed to publish news-refresh trigger (will self-heal on the next tick): {}",
                    ex.getMessage());
        }
    }
}
