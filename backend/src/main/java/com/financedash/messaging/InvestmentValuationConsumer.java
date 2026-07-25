package com.financedash.messaging;

import com.financedash.domain.InvestmentValuation;
import com.financedash.repository.InvestmentValuationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Overwrites the backend's shallow copy of the investing net value from service snapshots.
 * Last-write-wins by {@code asOf}: a strictly-older snapshot is ignored (self-healing — a lost
 * intermediate is harmless), while ties are accepted (a buy and an immediate refresh can share a
 * millisecond, and the later-arriving value should win).
 */
@Component
public class InvestmentValuationConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvestmentValuationConsumer.class);

    private final InvestmentValuationRepository repository;

    public InvestmentValuationConsumer(InvestmentValuationRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = InvestmentsMessaging.BACKEND_VALUE_QUEUE)
    public void handle(ValueSnapshot snapshot) {
        InvestmentValuation current = repository.findById(InvestmentValuation.SINGLETON_ID).orElse(null);
        if (current == null) {
            repository.save(new InvestmentValuation(snapshot.netValue(), snapshot.asOf()));
            return;
        }
        if (current.getAsOf().isAfter(snapshot.asOf())) {
            log.debug("Ignoring stale valuation snapshot asOf={} (have {})", snapshot.asOf(), current.getAsOf());
            return;
        }
        current.update(snapshot.netValue(), snapshot.asOf());
        repository.save(current);
    }
}
