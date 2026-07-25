package com.financedash.investments.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Transactional outbox row. A holding change and its outgoing messages are written to Mongo in the
 * same operation; a relay later publishes unsent rows to RabbitMQ and marks them sent. Guarantees
 * at-least-once delivery across broker/restart hiccups (the backend consumer makes it effectively
 * once via {@code eventId} dedupe / snapshot overwrite).
 */
@Document(collection = "outbox")
public class OutboxMessage {

    @Id
    private String id;
    private String exchange;
    private String routingKey;
    private String payloadJson;
    private Instant createdAt;
    private boolean published;
    private Instant publishedAt;

    protected OutboxMessage() {}

    public OutboxMessage(String exchange, String routingKey, String payloadJson, Instant createdAt) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.published = false;
    }

    public String getId() { return id; }
    public String getExchange() { return exchange; }
    public String getRoutingKey() { return routingKey; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isPublished() { return published; }
    public Instant getPublishedAt() { return publishedAt; }

    public void markPublished(Instant when) {
        this.published = true;
        this.publishedAt = when;
    }
}
