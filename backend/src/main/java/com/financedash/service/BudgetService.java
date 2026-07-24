package com.financedash.service;

import com.financedash.domain.Budget;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BudgetProgressResponse;
import com.financedash.dto.BudgetRequest;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.repository.BudgetRepository;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository budgetRepository, TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<Budget> findAll() {
        return budgetRepository.findAllByOrderByNameAsc();
    }

    public Budget findById(Long id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget " + id + " not found"));
    }

    public Budget create(BudgetRequest request) {
        return budgetRepository.save(new Budget(request.name(), request.value(), request.categories()));
    }

    public Budget update(Long id, BudgetRequest request) {
        Budget budget = findById(id);
        budget.setName(request.name());
        budget.setValue(request.value());
        budget.setCategories(request.categories());
        return budgetRepository.save(budget);
    }

    public void delete(Long id) {
        if (!budgetRepository.existsById(id)) {
            throw new ResourceNotFoundException("Budget " + id + " not found");
        }
        budgetRepository.deleteById(id);
    }

    /**
     * For each budget, sums the EXPENSE transactions whose category falls in the budget's
     * categories over [{@code from}, {@code to}]. Income/transfer/adjustment transactions
     * never count (only EXPENSE has spend semantics), and categories not owned by the
     * budget are ignored.
     */
    public List<BudgetProgressResponse> progress(LocalDate from, LocalDate to) {
        List<Transaction> expensesInPeriod = transactionRepository
                .findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, to)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.EXPENSE)
                .toList();

        return findAll().stream()
                .map(budget -> {
                    BigDecimal spent = expensesInPeriod.stream()
                            .filter(t -> budget.getCategories().contains(t.getCategory()))
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new BudgetProgressResponse(
                            budget.getId(),
                            budget.getName(),
                            budget.getValue(),
                            budget.getCategories(),
                            spent,
                            budget.getValue().subtract(spent),
                            from,
                            to);
                })
                .toList();
    }
}
