package com.financedash.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The repository is mocked, so the balance/period lists are supplied directly — this
 * isolates the reduction logic (the metric formulas) from date filtering, which is
 * covered separately in the repository integration test.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private BalanceService service;

    private static Transaction tx(
            TransactionType type, AccountType account, AccountType linked, String amount) {
        Category category = (type == TransactionType.INCOME || type == TransactionType.EXPENSE)
                ? Category.OTHER_EXPENSE
                : null;
        return new Transaction(
                "t", new BigDecimal(amount), LocalDate.of(2026, 6, 1), account, linked, category, type);
    }

    private BalanceSummaryResponse summarize(List<Transaction> upToDate, List<Transaction> inPeriod) {
        when(repository.findByTransactionDateLessThanEqual(TO)).thenReturn(upToDate);
        when(repository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(FROM, TO))
                .thenReturn(inPeriod);
        return service.summarize(FROM, TO);
    }

    @Test
    void emptyLedgerIsAllZero() {
        BalanceSummaryResponse s = summarize(List.of(), List.of());
        assertThat(s.netWorth()).isEqualByComparingTo("0");
        assertThat(s.spending()).isEqualByComparingTo("0");
        assertThat(s.netSpending()).isEqualByComparingTo("0");
        assertThat(s.netInvestment()).isEqualByComparingTo("0");
        assertThat(s.accountBalances().checking()).isEqualByComparingTo("0");
        assertThat(s.accountBalances().savings()).isEqualByComparingTo("0");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("0");
        assertThat(s.from()).isEqualTo(FROM);
        assertThat(s.to()).isEqualTo(TO);
    }

    @Test
    void adjustmentSeedsNetWorthButNotFlows() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.ADJUSTMENT, AccountType.CHECKING, null, "1000.00"));
        BalanceSummaryResponse s = summarize(ledger, ledger);

        assertThat(s.netWorth()).isEqualByComparingTo("1000.00");
        assertThat(s.accountBalances().checking()).isEqualByComparingTo("1000.00");
        // ADJUSTMENT is excluded from every flow metric.
        assertThat(s.spending()).isEqualByComparingTo("0");
        assertThat(s.netSpending()).isEqualByComparingTo("0");
        assertThat(s.netInvestment()).isEqualByComparingTo("0");
    }

    @Test
    void incomeAndExpenseAffectCheckingAndSpending() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.INCOME, AccountType.CHECKING, null, "2000.00"),
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "150.00"));
        BalanceSummaryResponse s = summarize(ledger, ledger);

        assertThat(s.accountBalances().checking()).isEqualByComparingTo("1850.00");
        assertThat(s.netWorth()).isEqualByComparingTo("1850.00");
        // Expense counts toward both spending and net spending; income affects neither.
        assertThat(s.spending()).isEqualByComparingTo("150.00");
        assertThat(s.netSpending()).isEqualByComparingTo("150.00");
        assertThat(s.netInvestment()).isEqualByComparingTo("0");
    }

    @Test
    void transferToSavingsCountsAsSpendingButNotNetSpending() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.TRANSFER, AccountType.CHECKING, AccountType.SAVINGS, "300.00"));
        BalanceSummaryResponse s = summarize(ledger, ledger);

        // Money moved between the user's own buckets: net worth unchanged.
        assertThat(s.netWorth()).isEqualByComparingTo("0");
        assertThat(s.accountBalances().checking()).isEqualByComparingTo("-300.00");
        assertThat(s.accountBalances().savings()).isEqualByComparingTo("300.00");
        // Savings contribution is "spending" but not "net spending".
        assertThat(s.spending()).isEqualByComparingTo("300.00");
        assertThat(s.netSpending()).isEqualByComparingTo("0");
        assertThat(s.netInvestment()).isEqualByComparingTo("0");
    }

    @Test
    void transferToInvestingCountsAsNetInvestmentOnly() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.TRANSFER, AccountType.CHECKING, AccountType.INVESTING, "500.00"));
        BalanceSummaryResponse s = summarize(ledger, ledger);

        assertThat(s.netWorth()).isEqualByComparingTo("0");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("500.00");
        // Investing contribution is neither spending nor net spending.
        assertThat(s.spending()).isEqualByComparingTo("0");
        assertThat(s.netSpending()).isEqualByComparingTo("0");
        assertThat(s.netInvestment()).isEqualByComparingTo("500.00");
    }

    @Test
    void investingWithdrawalReducesNetInvestment() {
        List<Transaction> ledger = List.of(
                tx(TransactionType.TRANSFER, AccountType.CHECKING, AccountType.INVESTING, "500.00"),
                tx(TransactionType.TRANSFER, AccountType.INVESTING, AccountType.CHECKING, "200.00"));
        BalanceSummaryResponse s = summarize(ledger, ledger);

        assertThat(s.accountBalances().investing()).isEqualByComparingTo("300.00");
        assertThat(s.accountBalances().checking()).isEqualByComparingTo("-300.00");
        assertThat(s.netWorth()).isEqualByComparingTo("0");
        // Net investment = contributions (500) - withdrawals (200).
        assertThat(s.netInvestment()).isEqualByComparingTo("300.00");
    }

    @Test
    void netWorthUsesUpToDateLedgerWhileFlowsUseInPeriodLedger() {
        // Something that happened before the period (opening balance) still counts toward
        // net worth (a stock) but must NOT contribute to the flows (which sum the period).
        List<Transaction> upToDate = List.of(
                tx(TransactionType.ADJUSTMENT, AccountType.CHECKING, null, "1000.00"),
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "40.00"));
        List<Transaction> inPeriod = List.of(
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "40.00"));

        BalanceSummaryResponse s = summarize(upToDate, inPeriod);

        assertThat(s.netWorth()).isEqualByComparingTo("960.00");
        assertThat(s.netSpending()).isEqualByComparingTo("40.00");
        assertThat(s.spending()).isEqualByComparingTo("40.00");
    }

    @Test
    void comprehensiveScenarioMatchesAllFormulas() {
        // Mirrors the worked example in docs/API.md (all in-period).
        List<Transaction> ledger = List.of(
                tx(TransactionType.ADJUSTMENT, AccountType.CHECKING, null, "1000.00"),
                tx(TransactionType.INCOME, AccountType.CHECKING, null, "3000.00"),
                tx(TransactionType.EXPENSE, AccountType.CHECKING, null, "200.00"),
                tx(TransactionType.TRANSFER, AccountType.CHECKING, AccountType.SAVINGS, "500.00"),
                tx(TransactionType.TRANSFER, AccountType.CHECKING, AccountType.INVESTING, "400.00"),
                tx(TransactionType.TRANSFER, AccountType.INVESTING, AccountType.CHECKING, "100.00"));

        BalanceSummaryResponse s = summarize(ledger, ledger);

        assertThat(s.accountBalances().checking()).isEqualByComparingTo("3000.00");
        assertThat(s.accountBalances().savings()).isEqualByComparingTo("500.00");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("300.00");
        assertThat(s.netWorth()).isEqualByComparingTo("3800.00");
        assertThat(s.netSpending()).isEqualByComparingTo("200.00");
        assertThat(s.spending()).isEqualByComparingTo("700.00");
        assertThat(s.netInvestment()).isEqualByComparingTo("300.00");
    }
}
