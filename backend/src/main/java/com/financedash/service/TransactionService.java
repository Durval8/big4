package com.financedash.service;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.TransactionRequest;
import com.financedash.exception.InvalidTransactionException;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.repository.TransactionRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<Transaction> findAll(LocalDate from, LocalDate to, AccountType accountType, Category category) {
        if (accountType != null && category != null) {
            return transactionRepository
                    .findByTransactionDateBetweenAndAccountTypeAndCategoryOrderByTransactionDateDescIdDesc(
                            from, to, accountType, category);
        }
        if (accountType != null) {
            return transactionRepository
                    .findByTransactionDateBetweenAndAccountTypeOrderByTransactionDateDescIdDesc(from, to, accountType);
        }
        if (category != null) {
            return transactionRepository
                    .findByTransactionDateBetweenAndCategoryOrderByTransactionDateDescIdDesc(from, to, category);
        }
        return transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, to);
    }

    public Transaction findById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + id + " not found"));
    }

    public Transaction create(TransactionRequest request) {
        validate(request);
        Transaction transaction = new Transaction(
                request.description(),
                request.amount(),
                request.transactionDate(),
                request.accountType(),
                request.linkedAccountType(),
                request.category(),
                request.transactionType()
        );
        return transactionRepository.save(transaction);
    }

    public Transaction update(Long id, TransactionRequest request) {
        validate(request);
        Transaction transaction = findById(id);
        transaction.setDescription(request.description());
        transaction.setAmount(request.amount());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setAccountType(request.accountType());
        transaction.setLinkedAccountType(request.linkedAccountType());
        transaction.setCategory(request.category());
        transaction.setTransactionType(request.transactionType());
        return transactionRepository.save(transaction);
    }

    public void delete(Long id) {
        if (!transactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Transaction " + id + " not found");
        }
        transactionRepository.deleteById(id);
    }

    /**
     * Enforces the invariants from the data model: a category only makes sense for
     * INCOME/EXPENSE (TRANSFER/ADJUSTMENT already carry their meaning via
     * transactionType + linkedAccountType), and a TRANSFER must name a distinct
     * destination account.
     */
    private void validate(TransactionRequest request) {
        TransactionType type = request.transactionType();
        boolean isIncomeOrExpense = type == TransactionType.INCOME || type == TransactionType.EXPENSE;

        // Investing is no longer part of the transaction ledger — it's its own entity,
        // and the INVESTING balance reflects holdings. Transactions are CHECKING/SAVINGS only.
        if (request.accountType() == AccountType.INVESTING) {
            throw new InvalidTransactionException(
                    "INVESTING is no longer a transaction account; manage investments on the Investments page");
        }
        if (request.linkedAccountType() == AccountType.INVESTING) {
            throw new InvalidTransactionException("Transfers to/from INVESTING are no longer supported");
        }

        if (isIncomeOrExpense && request.category() == null) {
            throw new InvalidTransactionException("category is required for " + type + " transactions");
        }
        if (!isIncomeOrExpense && request.category() != null) {
            throw new InvalidTransactionException("category must not be set for " + type + " transactions");
        }
        if (type == TransactionType.TRANSFER) {
            if (request.linkedAccountType() == null) {
                throw new InvalidTransactionException("linkedAccountType is required for TRANSFER transactions");
            }
            if (request.linkedAccountType() == request.accountType()) {
                throw new InvalidTransactionException("linkedAccountType must differ from accountType for a TRANSFER");
            }
        } else if (request.linkedAccountType() != null) {
            throw new InvalidTransactionException("linkedAccountType must not be set for " + type + " transactions");
        }
    }
}
