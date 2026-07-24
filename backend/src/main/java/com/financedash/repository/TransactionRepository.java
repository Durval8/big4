package com.financedash.repository;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
            LocalDate from, LocalDate to);

    List<Transaction> findByTransactionDateBetweenAndAccountTypeOrderByTransactionDateDescIdDesc(
            LocalDate from, LocalDate to, AccountType accountType);

    List<Transaction> findByTransactionDateBetweenAndCategoryOrderByTransactionDateDescIdDesc(
            LocalDate from, LocalDate to, Category category);

    List<Transaction> findByTransactionDateBetweenAndAccountTypeAndCategoryOrderByTransactionDateDescIdDesc(
            LocalDate from, LocalDate to, AccountType accountType, Category category);

    List<Transaction> findByTransactionDateLessThanEqual(LocalDate to);
}
