package com.financedash.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests that exercise the RabbitMQ consumers: reuses the shared Postgres from
 * {@link AbstractPostgresContainerTest} and adds a RabbitMQ container.
 */
public abstract class AbstractMessagingContainerTest extends AbstractPostgresContainerTest {

    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
}
