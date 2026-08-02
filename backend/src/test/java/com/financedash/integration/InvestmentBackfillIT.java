package com.financedash.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.financedash.domain.AccountType;
import com.financedash.domain.CashLegType;
import com.financedash.domain.InvestmentCashFlow;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.repository.InvestmentCashFlowRepository;
import com.financedash.repository.TransactionRepository;
import com.financedash.service.BalanceService;
import com.financedash.support.AbstractPostgresContainerTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceptance test for {@code V4__backfill_investment_transactions.sql}.
 *
 * <p>The migration and the {@code BalanceService} change are a matched pair: the service stopped
 * folding {@code investment_cash_flow} into cash balances, so unless every historical cash leg
 * gained a ledger row, balances would silently shift the moment this release deploys. The real
 * acceptance criterion is therefore behavioural — <b>balances must be identical before and after</b>
 * — so these tests reproduce the pre-migration arithmetic by hand and assert the post-migration
 * service agrees.
 *
 * <p>Note the migration itself already ran (Flyway, at context startup) against whatever the test
 * DB contained, so these tests exercise the same SQL by inserting flows and invoking the equivalent
 * insert, rather than by re-running Flyway.
 */
@SpringBootTest
@Transactional
// No broker in this test; keep the investment message listeners from trying to connect.
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class InvestmentBackfillIT extends AbstractPostgresContainerTest {

    private static final LocalDate D = LocalDate.of(2026, 6, 1);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private InvestmentCashFlowRepository cashFlowRepository;
    @Autowired private BalanceService balanceService;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void clean() {
        transactionRepository.deleteAll();
        cashFlowRepository.deleteAll();
    }

    /** The exact statement from V4, so the test exercises the migration's logic rather than a paraphrase. */
    private void runBackfill() {
        entityManager.createNativeQuery("""
                INSERT INTO transactions (
                    description, amount, transaction_date, account_type, linked_account_type,
                    category, transaction_type, source_event_id, created_at, updated_at
                )
                SELECT
                    CASE f.type WHEN 'FUND' THEN 'Investment funding'
                                WHEN 'CASH_OUT' THEN 'Investment cash-out' END,
                    f.amount, f.flow_date,
                    CASE f.type WHEN 'FUND' THEN f.account_type WHEN 'CASH_OUT' THEN 'INVESTING' END,
                    CASE f.type WHEN 'FUND' THEN 'INVESTING' WHEN 'CASH_OUT' THEN 'SAVINGS' END,
                    NULL, 'TRANSFER', f.event_id, now(), now()
                FROM investment_cash_flow f
                WHERE f.type IN ('FUND', 'CASH_OUT')
                  AND f.account_type IS NOT NULL
                  AND f.flow_date IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.source_event_id = f.event_id)
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private void givenLegacyState() {
        transactionRepository.saveAll(List.of(
                new Transaction("adj", new BigDecimal("1000.00"), D,
                        AccountType.CHECKING, null, null, TransactionType.ADJUSTMENT),
                new Transaction("transfer", new BigDecimal("500.00"), D,
                        AccountType.CHECKING, AccountType.SAVINGS, null, TransactionType.TRANSFER)));
        cashFlowRepository.saveAll(List.of(
                new InvestmentCashFlow("evt-fund", CashLegType.FUND,
                        new BigDecimal("400.00"), AccountType.CHECKING, D),
                new InvestmentCashFlow("evt-out", CashLegType.CASH_OUT,
                        new BigDecimal("100.00"), AccountType.SAVINGS, D)));
        entityManager.flush();
    }

    @Test
    void balancesAreUnchangedAcrossTheMigration() {
        givenLegacyState();

        // Balances as the OLD code computed them: ledger effect, then − ΣFUND(checking), + ΣCASH_OUT(savings).
        //   checking = +1000 − 500(transfer out) − 400(fund) = 100
        //   savings  = +500(transfer in) + 100(cash-out)     = 600
        BigDecimal expectedChecking = new BigDecimal("100.00");
        BigDecimal expectedSavings = new BigDecimal("600.00");

        runBackfill();

        BalanceSummaryResponse after = balanceService.summarize(FROM, TO);
        assertThat(after.accountBalances().checking()).isEqualByComparingTo(expectedChecking);
        assertThat(after.accountBalances().savings()).isEqualByComparingTo(expectedSavings);
        // netInvestment still reads the projection, so it is untouched by the backfill: 400 − 100.
        assertThat(after.netInvestment()).isEqualByComparingTo("300.00");
    }

    @Test
    void backfilledCashOutDoesNotInflateSpending() {
        givenLegacyState();
        runBackfill();

        // Only the user's own 500 transfer into savings counts; the backfilled 100 cash-out must not,
        // which is exactly what source_event_id buys us.
        assertThat(balanceService.summarize(FROM, TO).spending()).isEqualByComparingTo("500.00");
    }

    @Test
    void backfilledRowsCarryTheRightShapeAndAreMarkedSystemGenerated() {
        givenLegacyState();
        runBackfill();

        Transaction fund = transactionRepository.findAll().stream()
                .filter(t -> "evt-fund".equals(t.getSourceEventId())).findFirst().orElseThrow();
        assertThat(fund.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(fund.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(fund.getLinkedAccountType()).isEqualTo(AccountType.INVESTING);
        assertThat(fund.getCategory()).isNull();
        assertThat(fund.isSystemGenerated()).isTrue();

        Transaction out = transactionRepository.findAll().stream()
                .filter(t -> "evt-out".equals(t.getSourceEventId())).findFirst().orElseThrow();
        assertThat(out.getAccountType()).isEqualTo(AccountType.INVESTING);
        assertThat(out.getLinkedAccountType()).isEqualTo(AccountType.SAVINGS);
    }

    @Test
    void rerunningTheBackfillDoesNotDuplicateRows() {
        givenLegacyState();
        runBackfill();
        long afterFirst = transactionRepository.count();

        runBackfill();

        assertThat(transactionRepository.count()).isEqualTo(afterFirst);
    }
}
