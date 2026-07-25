package com.financedash.service;

import com.financedash.domain.AccountType;
import com.financedash.domain.CashLegType;
import com.financedash.domain.InvestmentCashFlow;
import com.financedash.domain.InvestmentValuation;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.AccountBalances;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.repository.InvestmentCashFlowRepository;
import com.financedash.repository.InvestmentValuationRepository;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Implements the dashboard metrics.
 *
 * <p>Investing data now lives in the investments service and reaches the backend only as messages,
 * projected into two local tables: {@code investment_cash_flow} (buys/cash-outs) and a singleton
 * {@code investment_valuation} (the current holdings value). This service folds the cash flows into
 * the CHECKING/SAVINGS balances (no rows exist in {@code transactions} for investing) and reads the
 * INVESTING balance straight from the valuation snapshot — a shallow copy that is <b>always
 * current</b> regardless of the selected period (documented v1 simplification). Cash balances and
 * flows stay as-of the period.
 */
@Service
public class BalanceService {

    private final TransactionRepository transactionRepository;
    private final InvestmentCashFlowRepository cashFlowRepository;
    private final InvestmentValuationRepository valuationRepository;

    public BalanceService(TransactionRepository transactionRepository,
                          InvestmentCashFlowRepository cashFlowRepository,
                          InvestmentValuationRepository valuationRepository) {
        this.transactionRepository = transactionRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.valuationRepository = valuationRepository;
    }

    public BalanceSummaryResponse summarize(LocalDate from, LocalDate to) {
        List<Transaction> upToDate = transactionRepository.findByTransactionDateLessThanEqual(to);
        List<Transaction> inPeriod = transactionRepository
                .findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, to);
        List<InvestmentCashFlow> flowsUpToDate = cashFlowRepository.findByFlowDateLessThanEqual(to);
        List<InvestmentCashFlow> flowsInPeriod = cashFlowRepository.findByFlowDateBetween(from, to);

        // Cash accounts: transactions minus funds sourced from them, plus cash-outs (which land in SAVINGS).
        BigDecimal checking = cashBalance(upToDate, flowsUpToDate, AccountType.CHECKING);
        BigDecimal savings = cashBalance(upToDate, flowsUpToDate, AccountType.SAVINGS);
        // INVESTING = shallow copy of the investments service's current net value (ZERO until first snapshot).
        BigDecimal investing = valuationRepository.findById(InvestmentValuation.SINGLETON_ID)
                .map(InvestmentValuation::getNetValue)
                .orElse(BigDecimal.ZERO);
        BigDecimal netWorth = checking.add(savings).add(investing);

        BigDecimal expenses = sumWhere(inPeriod, t -> t.getTransactionType() == TransactionType.EXPENSE);
        BigDecimal transfersToSavings = sumWhere(inPeriod, t ->
                t.getTransactionType() == TransactionType.TRANSFER
                        && t.getLinkedAccountType() == AccountType.SAVINGS);

        BigDecimal spending = expenses.add(transfersToSavings);
        BigDecimal netSpending = expenses;
        // Net new money into investments over the period.
        BigDecimal netInvestment = sumFlows(flowsInPeriod, CashLegType.FUND)
                .subtract(sumFlows(flowsInPeriod, CashLegType.CASH_OUT));

        return new BalanceSummaryResponse(
                from, to, netWorth, spending, netSpending, netInvestment,
                new AccountBalances(checking, savings, investing));
    }

    /**
     * Balance of a cash account = its transactions ± transfers, minus FUND flows sourced from it,
     * plus CASH_OUT flows (which always credit SAVINGS).
     */
    private BigDecimal cashBalance(List<Transaction> transactions, List<InvestmentCashFlow> flows, AccountType account) {
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
        for (InvestmentCashFlow f : flows) {
            if (f.getType() == CashLegType.FUND && f.getAccountType() == account) {
                balance = balance.subtract(f.getAmount());
            } else if (f.getType() == CashLegType.CASH_OUT && account == AccountType.SAVINGS) {
                balance = balance.add(f.getAmount());
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

    private BigDecimal sumFlows(List<InvestmentCashFlow> flows, CashLegType type) {
        return flows.stream()
                .filter(f -> f.getType() == type)
                .map(InvestmentCashFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
