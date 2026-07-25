package com.financedash.messaging;

import com.financedash.domain.AccountType;
import com.financedash.domain.CashLegType;
import com.financedash.domain.InvestmentCashFlow;
import com.financedash.repository.InvestmentCashFlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Records investing cash movements from the service into the {@code investment_cash_flow} projection.
 * Idempotent: the row's primary key is the message {@code eventId}, so a redelivered command yields
 * exactly one row (the listener runs single-threaded, and a same-id write merges rather than
 * duplicates). {@code BalanceService} folds these into the cash-account balances and netInvestment.
 */
@Component
public class InvestmentCashLegConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvestmentCashLegConsumer.class);

    private final InvestmentCashFlowRepository repository;

    public InvestmentCashLegConsumer(InvestmentCashFlowRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = InvestmentsMessaging.BACKEND_CASH_LEG_QUEUE)
    public void handle(CashLegCommand command) {
        if (repository.existsById(command.eventId())) {
            log.debug("Cash leg {} already applied; skipping (idempotent)", command.eventId());
            return;
        }
        repository.save(new InvestmentCashFlow(
                command.eventId(),
                CashLegType.valueOf(command.legType()),
                command.amount(),
                AccountType.valueOf(command.account()),
                command.date()));
        log.debug("Applied cash leg {} ({} {})", command.eventId(), command.legType(), command.amount());
    }
}
