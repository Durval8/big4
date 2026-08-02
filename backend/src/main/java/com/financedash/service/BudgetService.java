package com.financedash.service;

import com.financedash.domain.Budget;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BudgetProgressResponse;
import com.financedash.dto.BudgetRequest;
import com.financedash.dto.TimeRange;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.repository.BudgetRepository;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BudgetService {

    /** Nominal average days per month (365.25 / 12 ≈), used to prorate a monthly budget to any window length. */
    private static final BigDecimal AVG_DAYS_PER_MONTH = new BigDecimal("30.44");

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
     * never count (only EXPENSE has spend semantics), and categories not owned by the budget
     * are ignored. {@code value} is a monthly target, so it's prorated to the length of
     * [{@code from}, {@code to}] to get {@code periodValue} — see {@link #periodFactor}.
     * {@code range} is the same enum the caller resolved {@code from}/{@code to} from (or
     * {@code null} for an explicit custom range) — passed through only to detect
     * {@code TimeRange.ALL}, whose window otherwise starts at the epoch.
     */
    public List<BudgetProgressResponse> progress(TimeRange range, LocalDate from, LocalDate to) {
        LocalDate scalingFrom = range == TimeRange.ALL ? earliestTransactionDate(to) : from;
        BigDecimal factor = periodFactor(scalingFrom, to);

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
                    BigDecimal periodValue = budget.getValue().multiply(factor).setScale(2, RoundingMode.HALF_UP);
                    return new BudgetProgressResponse(
                            budget.getId(),
                            budget.getName(),
                            budget.getValue(),
                            periodValue,
                            budget.getCategories(),
                            spent,
                            periodValue.subtract(spent),
                            from,
                            to);
                })
                .toList();
    }

    /**
     * {@code TimeRange.ALL}'s window otherwise starts at 1970-01-01 — a degenerate ~56-year
     * span for prorating. Anchoring on the system's earliest transaction date instead reflects
     * how much financial history actually exists. Falls back to {@code to} (a single day) if
     * there are no transactions at all yet.
     */
    private LocalDate earliestTransactionDate(LocalDate to) {
        return transactionRepository.findFirstByOrderByTransactionDateAsc()
                .map(Transaction::getTransactionDate)
                .orElse(to);
    }

    /** Days in [from, to] (inclusive) as a fraction of a nominal {@link #AVG_DAYS_PER_MONTH}-day month. */
    private static BigDecimal periodFactor(LocalDate from, LocalDate to) {
        long daysInPeriod = Math.max(ChronoUnit.DAYS.between(from, to) + 1, 0);
        return BigDecimal.valueOf(daysInPeriod).divide(AVG_DAYS_PER_MONTH, 10, RoundingMode.HALF_UP);
    }
}
