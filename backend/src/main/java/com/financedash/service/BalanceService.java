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
 * {@code investment_valuation} (the current holdings value). The INVESTING balance is read straight
 * from the valuation snapshot — a shallow copy that is <b>always current</b> regardless of the
 * selected period (documented v1 simplification). Cash balances stay as-of the period.
 *
 * <p><b>Cash movements for investing come from {@code transactions}, not from the cash-flow
 * projection.</b> {@code InvestmentCashLegConsumer} writes a TRANSFER row for every cash leg (buy:
 * funding account → INVESTING; cash-out: INVESTING → SAVINGS), so the ordinary transaction
 * arithmetic below already accounts for them. Re-applying the projection here would double-count.
 * The projection survives solely to drive {@code netInvestment}, which needs the FUND/CASH_OUT
 * distinction directly.
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
        List<InvestmentCashFlow> flowsInPeriod = cashFlowRepository.findByFlowDateBetween(from, to);

        // Cash accounts: transactions only. Investing legs are TRANSFER rows in the ledger, so the
        // transfer arithmetic already debits/credits them — see the class Javadoc.
        BigDecimal checking = cashBalance(upToDate, AccountType.CHECKING);
        BigDecimal savings = cashBalance(upToDate, AccountType.SAVINGS);
        // INVESTING = shallow copy of the investments service's current net value (ZERO until first snapshot).
        BigDecimal investing = valuationRepository.findById(InvestmentValuation.SINGLETON_ID)
                .map(InvestmentValuation::getNetValue)
                .orElse(BigDecimal.ZERO);
        BigDecimal netWorth = checking.add(savings).add(investing);

        BigDecimal expenses = sumWhere(inPeriod, t -> t.getTransactionType() == TransactionType.EXPENSE);
        // Deliberately excludes system-generated rows: an investment cash-out is a TRANSFER into
        // SAVINGS and would otherwise match this predicate, inflating "spending" with money the
        // user took *out* of investments.
        BigDecimal transfersToSavings = sumWhere(inPeriod, t ->
                t.getTransactionType() == TransactionType.TRANSFER
                        && t.getLinkedAccountType() == AccountType.SAVINGS
                        && !t.isSystemGenerated());

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
     * Balance of a cash account = its transactions ± transfers. Investing legs need no special
     * handling: a buy is a TRANSFER out of this account (debited below) and a cash-out is a
     * TRANSFER into SAVINGS (credited below). Only ever called for CHECKING and SAVINGS, so the
     * INVESTING side of those transfers is never credited here — the INVESTING balance comes from
     * the valuation snapshot instead.
     */
    private BigDecimal cashBalance(List<Transaction> transactions, AccountType account) {
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

    private BigDecimal sumFlows(List<InvestmentCashFlow> flows, CashLegType type) {
        return flows.stream()
                .filter(f -> f.getType() == type)
                .map(InvestmentCashFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
