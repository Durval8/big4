package com.financedash.investments;

import static org.assertj.core.api.Assertions.assertThat;

import com.financedash.investments.support.AbstractContainersTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** De-risk: proves the Spring context loads with real Mongo + RabbitMQ containers wired in. */
@SpringBootTest
// Keep this context's consumers off so they don't compete on the shared broker (context caching).
@org.springframework.test.context.TestPropertySource(
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class SmokeIT extends AbstractContainersTest {

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void mongoIsReachable() {
        // A no-op query round-trips to the container.
        long count = mongoTemplate.count(new Query(), "smoke");
        assertThat(count).isEqualTo(0);
    }

    @Test
    void rabbitIsReachable() {
        // Opening a live connection to the broker container is enough to prove wiring.
        assertThat(rabbitTemplate.getConnectionFactory().createConnection().isOpen()).isTrue();
    }
}
