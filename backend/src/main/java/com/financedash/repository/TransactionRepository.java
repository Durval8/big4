package com.financedash.repository;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByTransactionDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<Transaction> findByTransactionDateBetweenAndAccountType(
            LocalDate from, LocalDate to, AccountType accountType, Pageable pageable);

    Page<Transaction> findByTransactionDateBetweenAndCategory(
            LocalDate from, LocalDate to, Category category, Pageable pageable);

    Page<Transaction> findByTransactionDateBetweenAndAccountTypeAndCategory(
            LocalDate from, LocalDate to, AccountType accountType, Category category, Pageable pageable);

    List<Transaction> findByTransactionDateLessThanEqual(LocalDate to);

    /**
     * Unpaginated date-range lookup retained for the aggregate consumers
     * ({@link com.financedash.service.BalanceService},
     * {@link com.financedash.service.BudgetService}), which sum over the whole range
     * rather than serving a page. The paginated variants above back
     * GET /api/transactions only.
     */
    List<Transaction> findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
            LocalDate from, LocalDate to);

    /** Earliest transaction in the system, regardless of category/account — used to anchor "All time" budget scaling. */
    Optional<Transaction> findFirstByOrderByTransactionDateAsc();
}
