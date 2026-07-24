package com.financedash.service;

import com.financedash.domain.AccountType;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.AccountBalances;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Implements the metric formulas from the project spec:
 *
 * <p>netWorth is a stock (as-of {@code to}); spending/netSpending/netInvestment are
 * flows summed over [{@code from}, {@code to}].
 */
@Service
public class BalanceService {

    private final TransactionRepository transactionRepository;

    public BalanceService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public BalanceSummaryResponse summarize(LocalDate from, LocalDate to) {
        List<Transaction> upToDate = transactionRepository.findByTransactionDateLessThanEqual(to);
        List<Transaction> inPeriod = transactionRepository
                .findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, to);

        BigDecimal checking = balanceAsOf(upToDate, AccountType.CHECKING);
        BigDecimal savings = balanceAsOf(upToDate, AccountType.SAVINGS);
        BigDecimal investing = balanceAsOf(upToDate, AccountType.INVESTING);
        BigDecimal netWorth = checking.add(savings).add(investing);

        BigDecimal expenses = sumWhere(inPeriod, t -> t.getTransactionType() == TransactionType.EXPENSE);
        BigDecimal transfersToSavings = sumWhere(inPeriod, t ->
                t.getTransactionType() == TransactionType.TRANSFER
                        && t.getLinkedAccountType() == AccountType.SAVINGS);
        BigDecimal transfersToInvesting = sumWhere(inPeriod, t ->
                t.getTransactionType() == TransactionType.TRANSFER
                        && t.getLinkedAccountType() == AccountType.INVESTING);
        BigDecimal transfersOutOfInvesting = sumWhere(inPeriod, t ->
                t.getTransactionType() == TransactionType.TRANSFER
                        && t.getAccountType() == AccountType.INVESTING);

        BigDecimal spending = expenses.add(transfersToSavings);
        BigDecimal netSpending = expenses;
        BigDecimal netInvestment = transfersToInvesting.subtract(transfersOutOfInvesting);

        return new BalanceSummaryResponse(
                from, to, netWorth, spending, netSpending, netInvestment,
                new AccountBalances(checking, savings, investing)
        );
    }

    private BigDecimal balanceAsOf(List<Transaction> transactions, AccountType account) {
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
        return balance;
    }

    private BigDecimal sumWhere(List<Transaction> transactions, java.util.function.Predicate<Transaction> predicate) {
        return transactions.stream()
                .filter(predicate)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
