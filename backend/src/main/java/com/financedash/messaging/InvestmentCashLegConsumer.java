package com.financedash.messaging;

import com.financedash.domain.AccountType;
import com.financedash.domain.CashLegType;
import com.financedash.domain.InvestmentCashFlow;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.repository.InvestmentCashFlowRepository;
import com.financedash.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies an investing cash movement from the service, writing <b>two</b> rows:
 *
 * <ul>
 *   <li>a {@code transactions} row — the user-visible ledger entry, and the single source of truth
 *       for the cash movement. A buy is a TRANSFER from the funding account to INVESTING; a
 *       cash-out is a TRANSFER from INVESTING to SAVINGS. {@code BalanceService} derives cash
 *       balances from this row and no longer looks at the cash-flow projection for them.</li>
 *   <li>an {@code investment_cash_flow} row — retained because it still drives {@code
 *       netInvestment}, which needs the FUND/CASH_OUT distinction directly.</li>
 * </ul>
 *
 * <p>Note the ledger row references INVESTING, which {@code TransactionService} rejects on every
 * user-facing path. That validation is deliberately not relaxed: this consumer writes through the
 * repository, so the API-level rule ("users cannot post to INVESTING") still holds exactly.
 *
 * <p><b>{@code @Transactional} here is a deliberate exception</b> to this codebase's "no service is
 * transactional" convention. That convention exists because services aren't transactional and
 * {@code open-in-view} is off, so a lazy collection would fail during response mapping — a consumer
 * maps no response. It has a real two-write atomicity requirement instead: without it, a failure
 * between the two saves would strand the message permanently, since the idempotency guard would
 * see the first row on redelivery and skip.
 *
 * <p>Idempotent: a redelivered command is a no-op. The guard is a check-then-act that relies on the
 * listener running single-threaded, backed by a unique index on {@code source_event_id} so a race
 * fails loudly rather than double-writing.
 */
@Component
public class InvestmentCashLegConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvestmentCashLegConsumer.class);

    private final InvestmentCashFlowRepository repository;
    private final TransactionRepository transactionRepository;

    public InvestmentCashLegConsumer(InvestmentCashFlowRepository repository,
                                     TransactionRepository transactionRepository) {
        this.repository = repository;
        this.transactionRepository = transactionRepository;
    }

    @RabbitListener(queues = InvestmentsMessaging.BACKEND_CASH_LEG_QUEUE)
    @Transactional
    public void handle(CashLegCommand command) {
        if (repository.existsById(command.eventId())) {
            log.debug("Cash leg {} already applied; skipping (idempotent)", command.eventId());
            return;
        }
        CashLegType legType = CashLegType.valueOf(command.legType());
        AccountType cashAccount = AccountType.valueOf(command.account());

        repository.save(new InvestmentCashFlow(
                command.eventId(), legType, command.amount(), cashAccount, command.date()));
        transactionRepository.save(ledgerRow(command, legType, cashAccount));

        log.debug("Applied cash leg {} ({} {})", command.eventId(), command.legType(), command.amount());
    }

    /**
     * A FUND debits the funding account (TRANSFER account → INVESTING); a CASH_OUT credits savings
     * (TRANSFER INVESTING → SAVINGS). {@code category} is null because TRANSFER forbids it.
     */
    private static Transaction ledgerRow(CashLegCommand command, CashLegType legType, AccountType cashAccount) {
        boolean funding = legType == CashLegType.FUND;
        return new Transaction(
                description(legType, command.stockSymbol()),
                command.amount(),
                command.date(),
                funding ? cashAccount : AccountType.INVESTING,
                funding ? AccountType.INVESTING : AccountType.SAVINGS,
                null,
                TransactionType.TRANSFER,
                command.eventId());
    }

    /**
     * {@code stockSymbol} was added to the contract after the first release, so a message enqueued
     * by an older service build arrives without it — fall back to a generic label rather than
     * failing the leg over a cosmetic field.
     */
    private static String description(CashLegType legType, String stockSymbol) {
        boolean funding = legType == CashLegType.FUND;
        if (stockSymbol == null || stockSymbol.isBlank()) {
            return funding ? "Investment funding" : "Investment cash-out";
        }
        return (funding ? "Bought " : "Cashed out ") + stockSymbol;
    }
}
