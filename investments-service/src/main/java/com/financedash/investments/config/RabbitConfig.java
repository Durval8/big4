package com.financedash.investments.config;

import com.financedash.investments.messaging.InvestmentsMessaging;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Service→backend messaging topology, declared here because the investments service owns the
 * contract. One topic exchange carries both streams, routed by key to the backend's durable queues.
 * The backend declares the same (idempotent) so nothing is lost if the service publishes first.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange investmentsExchange() {
        return new TopicExchange(InvestmentsMessaging.INVESTMENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue backendCashLegQueue() {
        return QueueBuilder.durable(InvestmentsMessaging.BACKEND_CASH_LEG_QUEUE).build();
    }

    @Bean
    public Queue backendValueQueue() {
        return QueueBuilder.durable(InvestmentsMessaging.BACKEND_VALUE_QUEUE).build();
    }

    @Bean
    public Binding cashLegBinding() {
        return BindingBuilder.bind(backendCashLegQueue()).to(investmentsExchange())
                .with(InvestmentsMessaging.CASH_LEG_ROUTING_KEY);
    }

    @Bean
    public Binding valueBinding() {
        return BindingBuilder.bind(backendValueQueue()).to(investmentsExchange())
                .with(InvestmentsMessaging.VALUE_ROUTING_KEY);
    }

    // --- intra-service price-refresh work queue (+ dead-letter path) ---

    @Bean
    public DirectExchange priceExchange() {
        return new DirectExchange(InvestmentsMessaging.PRICE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange priceDlx() {
        return new DirectExchange(InvestmentsMessaging.PRICE_DLX, true, false);
    }

    /** Work queue; messages exhausted by listener retries dead-letter to the DLQ. */
    @Bean
    public Queue priceRefreshQueue() {
        return QueueBuilder.durable(InvestmentsMessaging.PRICE_REFRESH_QUEUE)
                .deadLetterExchange(InvestmentsMessaging.PRICE_DLX)
                .deadLetterRoutingKey(InvestmentsMessaging.PRICE_REFRESH_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue priceRefreshDlq() {
        return QueueBuilder.durable(InvestmentsMessaging.PRICE_REFRESH_DLQ).build();
    }

    @Bean
    public Binding priceRefreshBinding() {
        return BindingBuilder.bind(priceRefreshQueue()).to(priceExchange())
                .with(InvestmentsMessaging.PRICE_REFRESH_ROUTING_KEY);
    }

    @Bean
    public Binding priceDlqBinding() {
        return BindingBuilder.bind(priceRefreshDlq()).to(priceDlx())
                .with(InvestmentsMessaging.PRICE_REFRESH_ROUTING_KEY);
    }

    // --- intra-service news-refresh trigger (no DLQ; best-effort) ---

    @Bean
    public DirectExchange newsExchange() {
        return new DirectExchange(InvestmentsMessaging.NEWS_EXCHANGE, true, false);
    }

    @Bean
    public Queue newsRefreshQueue() {
        return QueueBuilder.durable(InvestmentsMessaging.NEWS_REFRESH_QUEUE).build();
    }

    @Bean
    public Binding newsRefreshBinding() {
        return BindingBuilder.bind(newsRefreshQueue()).to(newsExchange())
                .with(InvestmentsMessaging.NEWS_REFRESH_ROUTING_KEY);
    }
}
