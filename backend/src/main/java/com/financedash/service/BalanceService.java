package com.financedash.service;

import com.financedash.domain.AccountType;
import com.financedash.domain.Investment;
import com.financedash.domain.InvestmentEvent;
import com.financedash.domain.InvestmentEventType;
import com.financedash.domain.InvestmentStatus;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.AccountBalances;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.repository.InvestmentEventRepository;
import com.financedash.repository.InvestmentRepository;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Implements the dashboard metrics.
 *
 * <p>Investments are their own ledger: buys/cash-outs are {@link InvestmentEvent}s that
 * this service folds into the cash-account balances (no rows exist in {@code transactions}
 * for investing). The INVESTING balance is a reflection of the investment dashboard —
 * the sum of open holdings' current value — and, being a mark-to-market figure with no
 * history, is <b>always current</b> regardless of the selected period (documented v1
 * simplification). Cash balances and flows stay as-of the period.
 */
@Service
public class BalanceService {

    private final TransactionRepository transactionRepository;
    private final InvestmentRepository investmentRepository;
    private final InvestmentEventRepository investmentEventRepository;

    public BalanceService(TransactionRepository transactionRepository,
                          InvestmentRepository investmentRepository,
                          InvestmentEventRepository investmentEventRepository) {
        this.transactionRepository = transactionRepository;
        this.investmentRepository = investmentRepository;
        this.investmentEventRepository = investmentEventRepository;
    }

    public BalanceSummaryResponse summarize(LocalDate from, LocalDate to) {
        List<Transaction> upToDate = transactionRepository.findByTransactionDateLessThanEqual(to);
        List<Transaction> inPeriod = transactionRepository
                .findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, to);
        List<InvestmentEvent> eventsUpToDate = investmentEventRepository.findByEventDateLessThanEqual(to);
        List<InvestmentEvent> eventsInPeriod = investmentEventRepository.findByEventDateBetween(from, to);

        // Cash accounts: transactions minus funds sourced from them, plus cash-outs (which land in SAVINGS).
        BigDecimal checking = cashBalance(upToDate, eventsUpToDate, AccountType.CHECKING);
        BigDecimal savings = cashBalance(upToDate, eventsUpToDate, AccountType.SAVINGS);
        // INVESTING = reflection of the investment dashboard (always current value).
        BigDecimal investing = investmentRepository.findByStatus(InvestmentStatus.OPEN).stream()
                .map(Investment::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netWorth = checking.add(savings).add(investing);

        BigDecimal expenses = sumWhere(inPeriod, t -> t.getTransactionType() == TransactionType.EXPENSE);
        BigDecimal transfersToSavings = sumWhere(inPeriod, t ->
                t.getTransactionType() == TransactionType.TRANSFER
                        && t.getLinkedAccountType() == AccountType.SAVINGS);

        BigDecimal spending = expenses.add(transfersToSavings);
        BigDecimal netSpending = expenses;
        // Net new money into investments over the period.
        BigDecimal netInvestment = sumEvents(eventsInPeriod, InvestmentEventType.FUND)
                .subtract(sumEvents(eventsInPeriod, InvestmentEventType.CASH_OUT));

        return new BalanceSummaryResponse(
                from, to, netWorth, spending, netSpending, netInvestment,
                new AccountBalances(checking, savings, investing));
    }

    /**
     * Balance of a cash account = its transactions ± transfers, minus FUND events sourced
     * from it, plus CASH_OUT events (which always credit SAVINGS).
     */
    private BigDecimal cashBalance(List<Transaction> transactions, List<InvestmentEvent> events, AccountType account) {
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction t : transactions) {
            switch (t.getTransactionType()) {
                case INCOME, ADJUSTMENT -> {
                    if (t.getAccountType() == account) {
                        balance = balance.add(t.getAmount());
                    }
                }
                case EXPENSE -> {
                    if (t.getAccountType() == account) {
                        balance = balance.subtract(t.getAmount());
                    }
                }
                case TRANSFER -> {
                    if (t.getAccountType() == account) {
                        balance = balance.subtract(t.getAmount());
                    }
                    if (t.getLinkedAccountType() == account) {
                        balance = balance.add(t.getAmount());
                    }
                }
            }
        }
        for (InvestmentEvent e : events) {
            if (e.getType() == InvestmentEventType.FUND && e.getAccountType() == account) {
                balance = balance.subtract(e.getAmount());
            } else if (e.getType() == InvestmentEventType.CASH_OUT && account == AccountType.SAVINGS) {
                balance = balance.add(e.getAmount());
            }
        }
        return balance;
    }

    private BigDecimal sumWhere(List<Transaction> transactions, java.util.function.Predicate<Transaction> predicate) {
        return transactions.stream()
                .filter(predicate)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumEvents(List<InvestmentEvent> events, InvestmentEventType type) {
        return events.stream()
                .filter(e -> e.getType() == type)
                .map(InvestmentEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
