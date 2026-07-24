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

/**
 * Verifies the Spring Data derived queries against a real Postgres: date-range
 * inclusivity, ordering, and the optional account/category filters.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class TransactionRepositoryIT extends AbstractPostgresContainerTest {

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Transaction persist(LocalDate date, AccountType account, Category category, TransactionType type) {
        Transaction t = new Transaction(
                "t", new BigDecimal("10.00"), date, account, null, category, type);
        return entityManager.persistAndFlush(t);
    }

    @Test
    void betweenIsInclusiveOnBothEnds() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        persist(from, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);           // on lower bound
        persist(to, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);             // on upper bound
        persist(from.minusDays(1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE); // just before
        persist(to.plusDays(1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);    // just after

        List<Transaction> result =
                repository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, to);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(t ->
                assertThat(t.getTransactionDate()).isBetween(from, to));
    }

    @Test
    void ordersByDateThenIdDescending() {
        LocalDate older = LocalDate.of(2026, 6, 1);
        LocalDate newer = LocalDate.of(2026, 6, 15);
        Transaction first = persist(newer, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        Transaction second = persist(newer, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        Transaction third = persist(older, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);

        List<Transaction> result = repository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        // Newest date first; within the same date, the higher id (later insert) comes first.
        assertThat(result).extracting(Transaction::getId)
                .containsExactly(second.getId(), first.getId(), third.getId());
    }

    @Test
    void filtersByAccountType() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        persist(d, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(d, AccountType.SAVINGS, Category.GROCERIES, TransactionType.EXPENSE);

        List<Transaction> result =
                repository.findByTransactionDateBetweenAndAccountTypeOrderByTransactionDateDescIdDesc(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), AccountType.SAVINGS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountType()).isEqualTo(AccountType.SAVINGS);
    }

    @Test
    void filtersByAccountTypeAndCategory() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        persist(d, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(d, AccountType.CHECKING, Category.DINING_OUT, TransactionType.EXPENSE);
        persist(d, AccountType.SAVINGS, Category.GROCERIES, TransactionType.EXPENSE);

        List<Transaction> result =
                repository.findByTransactionDateBetweenAndAccountTypeAndCategoryOrderByTransactionDateDescIdDesc(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        AccountType.CHECKING, Category.GROCERIES);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(result.get(0).getCategory()).isEqualTo(Category.GROCERIES);
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

    @Test
    void auditingTimestampsArePopulated() {
        Transaction saved = persist(
                LocalDate.of(2026, 6, 1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
