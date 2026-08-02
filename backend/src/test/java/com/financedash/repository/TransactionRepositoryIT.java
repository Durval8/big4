package com.financedash.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.config.JpaConfig;
import com.financedash.support.AbstractPostgresContainerTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Verifies the Spring Data derived queries against a real Postgres: date-range
 * inclusivity, the optional account/category filters, and pagination/sort behavior
 * driven entirely by the passed-in Pageable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class TransactionRepositoryIT extends AbstractPostgresContainerTest {

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private Transaction persist(LocalDate date, AccountType account, Category category, TransactionType type) {
        return persist(date, account, category, type, new BigDecimal("10.00"));
    }

    private Transaction persist(
            LocalDate date, AccountType account, Category category, TransactionType type, BigDecimal amount) {
        Transaction t = new Transaction("t", amount, date, account, null, category, type);
        return entityManager.persistAndFlush(t);
    }

    private static Pageable dateDescPageable(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "transactionDate").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void betweenIsInclusiveOnBothEnds() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        persist(from, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);           // on lower bound
        persist(to, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);             // on upper bound
        persist(from.minusDays(1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE); // just before
        persist(to.plusDays(1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);    // just after

        Page<Transaction> result = repository.findByTransactionDateBetween(from, to, dateDescPageable(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allSatisfy(t ->
                assertThat(t.getTransactionDate()).isBetween(from, to));
    }

    @Test
    void ordersByDateThenIdDescendingByDefault() {
        LocalDate older = LocalDate.of(2026, 6, 1);
        LocalDate newer = LocalDate.of(2026, 6, 15);
        Transaction first = persist(newer, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        Transaction second = persist(newer, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        Transaction third = persist(older, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);

        Page<Transaction> result = repository.findByTransactionDateBetween(FROM, TO, dateDescPageable(0, 20));

        // Newest date first; within the same date, the higher id (later insert) comes first.
        assertThat(result.getContent()).extracting(Transaction::getId)
                .containsExactly(second.getId(), first.getId(), third.getId());
    }

    @Test
    void ordersByAmountAscendingWhenRequested() {
        Transaction cheap = persist(FROM, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE,
                new BigDecimal("5.00"));
        Transaction mid = persist(FROM, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE,
                new BigDecimal("50.00"));
        Transaction expensive = persist(FROM, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE,
                new BigDecimal("500.00"));

        Pageable amountAsc = PageRequest.of(0, 20,
                Sort.by(Sort.Direction.ASC, "amount").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Transaction> result = repository.findByTransactionDateBetween(FROM, TO, amountAsc);

        assertThat(result.getContent()).extracting(Transaction::getId)
                .containsExactly(cheap.getId(), mid.getId(), expensive.getId());
    }

    @Test
    void pagesResultsAndReportsTotals() {
        for (int i = 0; i < 5; i++) {
            persist(FROM.plusDays(i), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        }

        Page<Transaction> firstPage = repository.findByTransactionDateBetween(FROM, TO, dateDescPageable(0, 2));
        Page<Transaction> secondPage = repository.findByTransactionDateBetween(FROM, TO, dateDescPageable(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(secondPage.getContent()).hasSize(2);
        // Pages don't overlap.
        assertThat(firstPage.getContent()).extracting(Transaction::getId)
                .doesNotContainAnyElementsOf(secondPage.getContent().stream().map(Transaction::getId).toList());
    }

    @Test
    void filtersByAccountType() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        persist(d, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(d, AccountType.SAVINGS, Category.GROCERIES, TransactionType.EXPENSE);

        Page<Transaction> result = repository.findByTransactionDateBetweenAndAccountType(
                FROM, TO, AccountType.SAVINGS, dateDescPageable(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAccountType()).isEqualTo(AccountType.SAVINGS);
    }

    @Test
    void filtersByCategory() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        persist(d, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(d, AccountType.CHECKING, Category.DINING_OUT, TransactionType.EXPENSE);

        Page<Transaction> result = repository.findByTransactionDateBetweenAndCategory(
                FROM, TO, Category.DINING_OUT, dateDescPageable(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo(Category.DINING_OUT);
    }

    @Test
    void filtersByAccountTypeAndCategory() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        persist(d, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(d, AccountType.CHECKING, Category.DINING_OUT, TransactionType.EXPENSE);
        persist(d, AccountType.SAVINGS, Category.GROCERIES, TransactionType.EXPENSE);

        Page<Transaction> result = repository.findByTransactionDateBetweenAndAccountTypeAndCategory(
                FROM, TO, AccountType.CHECKING, Category.GROCERIES, dateDescPageable(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo(Category.GROCERIES);
    }

    @Test
    void lessThanEqualIncludesBoundary() {
        LocalDate cutoff = LocalDate.of(2026, 6, 15);
        persist(cutoff, AccountType.CHECKING, null, TransactionType.ADJUSTMENT);
        persist(cutoff.minusDays(5), AccountType.CHECKING, null, TransactionType.ADJUSTMENT);
        persist(cutoff.plusDays(1), AccountType.CHECKING, null, TransactionType.ADJUSTMENT);

        List<Transaction> result = repository.findByTransactionDateLessThanEqual(cutoff);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(t ->
                assertThat(t.getTransactionDate()).isBeforeOrEqualTo(cutoff));
    }

    /**
     * The unpaginated range query is still the one BalanceService/BudgetService use to
     * aggregate over a whole period, so it has to keep working alongside the paginated variants.
     */
    @Test
    void unpaginatedRangeQueryStillServesAggregateConsumers() {
        LocalDate older = LocalDate.of(2026, 6, 1);
        LocalDate newer = LocalDate.of(2026, 6, 15);
        persist(older, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(newer, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(TO.plusDays(1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);

        List<Transaction> result =
                repository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(FROM, TO);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Transaction::getTransactionDate).containsExactly(newer, older);
    }

    @Test
    void auditingTimestampsArePopulated() {
        Transaction saved = persist(
                LocalDate.of(2026, 6, 1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
