package com.financedash.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.financedash.domain.AccountType;
import com.financedash.domain.CashLegType;
import com.financedash.domain.Category;
import com.financedash.domain.InvestmentCashFlow;
import com.financedash.domain.InvestmentValuation;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.repository.InvestmentCashFlowRepository;
import com.financedash.repository.InvestmentValuationRepository;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Repositories are mocked, so the transaction/cash-flow sets and the valuation snapshot are supplied
 * directly — this isolates the metric formulas (including the message-fed cash-flow fold-in) from
 * date filtering, which the repository integration test covers. Investing value now comes from the
 * valuation shallow copy, not from local holdings.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);
    private static final LocalDate D = LocalDate.of(2026, 6, 1);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private InvestmentCashFlowRepository cashFlowRepository;
    @Mock
    private InvestmentValuationRepository valuationRepository;

    @InjectMocks
    private BalanceService service;

    private int flowSeq = 0;

    private static Transaction tx(TransactionType type, AccountType account, AccountType linked, String amount) {
        Category category = (type == TransactionType.INCOME || type == TransactionType.EXPENSE)
                ? Category.OTHER_EXPENSE
                : null;
        return new Transaction("t", new BigDecimal(amount), D, account, linked, category, type);
    }

    private InvestmentCashFlow flow(CashLegType type, AccountType account, String amount) {
        return new InvestmentCashFlow("evt-" + (flowSeq++), type, new BigDecimal(amount), account, D);
    }

    private static Optional<InvestmentValuation> valued(String netValue) {
        return Optional.of(new InvestmentValuation(new BigDecimal(netValue), Instant.parse("2026-06-01T00:00:00Z")));
    }

    private BalanceSummaryResponse summarize(
            List<Transaction> txUpTo, List<Transaction> txInPeriod,
            List<InvestmentCashFlow> flowsUpTo, List<InvestmentCashFlow> flowsInPeriod,
            Optional<InvestmentValuation> valuation) {
        when(transactionRepository.findByTransactionDateLessThanEqual(TO)).thenReturn(txUpTo);
        when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(FROM, TO))
                .thenReturn(txInPeriod);
        when(cashFlowRepository.findByFlowDateLessThanEqual(TO)).thenReturn(flowsUpTo);
        when(cashFlowRepository.findByFlowDateBetween(FROM, TO)).thenReturn(flowsInPeriod);
        when(valuationRepository.findById(InvestmentValuation.SINGLETON_ID)).thenReturn(valuation);
        return service.summarize(FROM, TO);
    }

    @Test
    void emptyLedgerIsAllZero() {
        BalanceSummaryResponse s = summarize(List.of(), List.of(), List.of(), List.of(), Optional.empty());
        assertThat(s.netWorth()).isEqualByComparingTo("0");
        assertThat(s.spending()).isEqualByComparingTo("0");
        assertThat(s.netSpending()).isEqualByComparingTo("0");
        assertThat(s.netInvestment()).isEqualByComparingTo("0");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("0");
    }

    @Test
    void incomeAndExpenseAffectCheckingAndSpending() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.INCOME, AccountType.CHECKING, null, "2000.00"),
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "150.00"));
        BalanceSummaryResponse s = summarize(ledger, ledger, List.of(), List.of(), Optional.empty());

        assertThat(s.accountBalances().checking()).isEqualByComparingTo("1850.00");
        assertThat(s.netWorth()).isEqualByComparingTo("1850.00");
        assertThat(s.spending()).isEqualByComparingTo("150.00");
        assertThat(s.netSpending()).isEqualByComparingTo("150.00");
        assertThat(s.netInvestment()).isEqualByComparingTo("0");
    }

    @Test
    void transferToSavingsCountsAsSpendingButNotNetSpending() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.TRANSFER, AccountType.CHECKING, AccountType.SAVINGS, "300.00"));
        BalanceSummaryResponse s = summarize(ledger, ledger, List.of(), List.of(), Optional.empty());

        assertThat(s.netWorth()).isEqualByComparingTo("0");
        assertThat(s.accountBalances().checking()).isEqualByComparingTo("-300.00");
        assertThat(s.accountBalances().savings()).isEqualByComparingTo("300.00");
        assertThat(s.spending()).isEqualByComparingTo("300.00");
        assertThat(s.netSpending()).isEqualByComparingTo("0");
    }

    @Test
    void fundDebitsSourceAndInvestingReflectsValuation() {
        List<InvestmentCashFlow> flows = List.of(flow(CashLegType.FUND, AccountType.CHECKING, "500.00"));
        BalanceSummaryResponse s = summarize(
                List.of(), List.of(), flows, flows, valued("500.00"));

        assertThat(s.accountBalances().checking()).isEqualByComparingTo("-500.00"); // funded from checking
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("500.00"); // shallow-copy snapshot
        assertThat(s.netWorth()).isEqualByComparingTo("0");                          // cash → holding, flat
        assertThat(s.netInvestment()).isEqualByComparingTo("500.00");                // net new money in
        assertThat(s.spending()).isEqualByComparingTo("0");                          // a buy is not spending
    }

    @Test
    void cashOutCreditsSavingsAndReducesNetInvestment() {
        List<InvestmentCashFlow> flows = List.of(
                flow(CashLegType.FUND, AccountType.CHECKING, "500.00"),
                flow(CashLegType.CASH_OUT, AccountType.SAVINGS, "200.00"));
        BalanceSummaryResponse s = summarize(
                List.of(), List.of(), flows, flows, valued("300.00"));

        assertThat(s.accountBalances().checking()).isEqualByComparingTo("-500.00");
        assertThat(s.accountBalances().savings()).isEqualByComparingTo("200.00");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("300.00");
        assertThat(s.netWorth()).isEqualByComparingTo("0");
        assertThat(s.netInvestment()).isEqualByComparingTo("300.00"); // 500 in − 200 out
    }

    @Test
    void revaluationRaisesInvestingAndNetWorth() {
        // Funded 500, but the latest snapshot values holdings at 700 (a price rise — no cash flow).
        List<InvestmentCashFlow> flows = List.of(flow(CashLegType.FUND, AccountType.CHECKING, "500.00"));
        BalanceSummaryResponse s = summarize(
                List.of(), List.of(), flows, flows, valued("700.00"));

        assertThat(s.accountBalances().checking()).isEqualByComparingTo("-500.00");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("700.00");
        assertThat(s.netWorth()).isEqualByComparingTo("200.00"); // unrealized gain
        assertThat(s.netInvestment()).isEqualByComparingTo("500.00");
    }

    @Test
    void netWorthUsesUpToDateLedgerWhileFlowsUseInPeriodLedger() {
        List<Transaction> upToDate = List.of(
                tx(TransactionType.ADJUSTMENT, AccountType.CHECKING, null, "1000.00"),
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "40.00"));
        List<Transaction> inPeriod = List.of(
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "40.00"));
        BalanceSummaryResponse s = summarize(upToDate, inPeriod, List.of(), List.of(), Optional.empty());

        assertThat(s.netWorth()).isEqualByComparingTo("960.00");
        assertThat(s.netSpending()).isEqualByComparingTo("40.00");
        assertThat(s.spending()).isEqualByComparingTo("40.00");
    }

    @Test
    void comprehensiveScenarioMatchesAllFormulas() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.ADJUSTMENT, AccountType.CHECKING, null, "1000.00"),
                tx(TransactionType.INCOME, AccountType.CHECKING, null, "3000.00"),
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "200.00"),
                tx(TransactionType.TRANSFER, AccountType.CHECKING, AccountType.SAVINGS, "500.00"));
        List<InvestmentCashFlow> flows = List.of(
                flow(CashLegType.FUND, AccountType.CHECKING, "400.00"),
                flow(CashLegType.CASH_OUT, AccountType.SAVINGS, "100.00"));
        BalanceSummaryResponse s = summarize(
                ledger, ledger, flows, flows, valued("300.00"));

        // checking: +1000 +3000 −200 −500(transfer) −400(fund) = 2900
        assertThat(s.accountBalances().checking()).isEqualByComparingTo("2900.00");
        // savings: +500(transfer in) +100(cash-out) = 600
        assertThat(s.accountBalances().savings()).isEqualByComparingTo("600.00");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("300.00");
        assertThat(s.netWorth()).isEqualByComparingTo("3800.00");
        assertThat(s.netSpending()).isEqualByComparingTo("200.00");
        assertThat(s.spending()).isEqualByComparingTo("700.00");   // 200 expense + 500 to savings
        assertThat(s.netInvestment()).isEqualByComparingTo("300.00"); // 400 in − 100 out
    }
}
