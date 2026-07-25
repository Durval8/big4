package com.financedash.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.messaging.InvestmentsMessaging;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Backend consumer topology. Declarations mirror the investments service's <b>exactly</b> (durable
 * topic exchange, durable queues, same bindings) so whichever process declares first, the other's
 * redeclaration is a no-op instead of a {@code PRECONDITION_FAILED}. The backend only ever consumes.
 *
 * <p>The relay sends raw JSON with no type header, so the Jackson converter infers the target record
 * from each listener method's parameter type.
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
    public Binding backendCashLegBinding() {
        return BindingBuilder.bind(backendCashLegQueue()).to(investmentsExchange())
                .with(InvestmentsMessaging.CASH_LEG_ROUTING_KEY);
    }

    @Bean
    public Binding backendValueBinding() {
        return BindingBuilder.bind(backendValueQueue()).to(investmentsExchange())
                .with(InvestmentsMessaging.VALUE_ROUTING_KEY);
    }

    /** Uses the Spring-managed ObjectMapper (jsr310 registered) so Instant/LocalDate bind. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
