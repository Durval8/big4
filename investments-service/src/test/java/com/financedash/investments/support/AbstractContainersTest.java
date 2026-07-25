package com.financedash.investments.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests: boots a real MongoDB and RabbitMQ in Docker (via Testcontainers)
 * and points Spring at them. Containers are static so they are shared across the test class.
 */
@Testcontainers
public abstract class AbstractContainersTest {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static {
        MONGO.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        // Keep the scheduled jobs quiet during tests; individual tests drive flows explicitly.
        registry.add("finnhub.base-url", () -> "http://localhost:0");
        registry.add("outbox.relay-interval-ms", () -> "3600000");   // effectively never auto-fires
        registry.add("pricing.refresh-cron", () -> "0 0 5 31 12 ?"); // once a year — never during a test
        // Fast retries so the DLQ path completes quickly under test.
        registry.add("spring.rabbitmq.listener.simple.retry.max-attempts", () -> "2");
        registry.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "150ms");
    }
}
