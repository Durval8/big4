package com.financedash.service;

import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.AnalyticsResponse;
import com.financedash.dto.BucketUnit;
import com.financedash.dto.CategoryTotal;
import com.financedash.dto.TimeBucket;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Backs {@code GET /api/analytics} — the Dashboard's spending-visualization section. See
 * docs/superpowers/specs/2026-08-02-transaction-analytics-design.md for the full design; this
 * class implements the window resolution (cap + floor), the income/expense aggregates, the
 * per-category totals (this window and the prior one), and the gap-filled time-bucket series.
 *
 * <p>Third aggregate consumer of {@link TransactionRepository}'s unpaginated range finder,
 * alongside {@link BalanceService} and {@link BudgetService}.
 */
@Service
public class AnalyticsService {

    private final TransactionRepository transactionRepository;

    public AnalyticsService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * @param explicitFrom the controller's raw {@code from} query param (nullable) — used only to
     *     detect whether the caller named specific dates, which escapes the earliest-transaction
     *     floor below. When neither {@code range} nor {@code from} was supplied, the controller's
     *     {@code Period.resolve} already defaulted to {@code TimeRange.MONTH} before calling this
     *     method, and that default window must still get the floor — hence this is a separate
     *     parameter from {@code windowFrom}/{@code windowTo}, not re-derived from them.
     * @param windowFrom the {@code range}/{@code from}/{@code to} params already resolved to a
     *     window via {@code Period.resolve} (by the controller, which owns "today"). This method
     *     applies the two analytics-specific adjustments on top: the one-year cap, then the
     *     earliest-transaction floor.
     */
    public AnalyticsResponse getAnalytics(LocalDate explicitFrom, LocalDate windowFrom, LocalDate windowTo) {
        LocalDate resolvedFrom = windowFrom;
        LocalDate resolvedTo = windowTo;

        // 1. One-year cap — applies regardless of how the window was specified.
        LocalDate capFrom = resolvedTo.minusYears(1).plusDays(1);
        if (resolvedFrom.isBefore(capFrom)) {
            resolvedFrom = capFrom;
        }

        // 2. Earliest-transaction floor, for any named range — an explicit `from` escapes it.
        LocalDate earliest = earliestTransactionDate(resolvedTo);
        if (explicitFrom == null && earliest.isAfter(resolvedFrom)) {
            resolvedFrom = earliest;
        }

        BucketUnit bucketUnit = BucketUnit.forWindow(resolvedFrom, resolvedTo);
        List<Transaction> current = incomeAndExpenseInRange(resolvedFrom, resolvedTo);

        BigDecimal totalIncome = sum(current, TransactionType.INCOME);
        BigDecimal totalExpense = sum(current, TransactionType.EXPENSE);

        // Prior period: omitted entirely when nothing precedes the window, i.e. `resolvedFrom` is
        // on or before the earliest transaction date.
        LocalDate previousFrom = null;
        LocalDate previousTo = null;
        List<Transaction> prior = List.of();
        if (resolvedFrom.isAfter(earliest)) {
            long windowDays = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1;
            previousTo = resolvedFrom.minusDays(1);
            previousFrom = previousTo.minusDays(windowDays - 1);
            prior = incomeAndExpenseInRange(previousFrom, previousTo);
        }

        List<CategoryTotal> categories = buildCategoryTotals(current, prior, previousFrom != null);
        List<TimeBucket> buckets = buildBuckets(resolvedFrom, resolvedTo, bucketUnit, current);

        return new AnalyticsResponse(
                resolvedFrom, resolvedTo, previousFrom, previousTo, bucketUnit,
                totalIncome, totalExpense, categories, buckets);
    }

    /** Falls back to {@code to} (a single day) when there are no transactions at all yet. */
    private LocalDate earliestTransactionDate(LocalDate to) {
        return transactionRepository.findFirstByOrderByTransactionDateAsc()
                .map(Transaction::getTransactionDate)
                .orElse(to);
    }

    /** INCOME/EXPENSE only — TRANSFER and ADJUSTMENT carry no category and aren't economic activity. */
    private List<Transaction> incomeAndExpenseInRange(LocalDate from, LocalDate to) {
        return transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, to)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.INCOME
                        || t.getTransactionType() == TransactionType.EXPENSE)
                .toList();
    }

    private static BigDecimal sum(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getTransactionType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Union of categories non-zero in either period — a category present only in the prior period
     * still appears (with {@code amount: 0}) so the movers chart can show the drop. When there's no
     * prior period at all, {@code previousAmount} is null for every row (not zero), and the omit
     * condition simplifies to "this window's amount is zero."
     */
    private static List<CategoryTotal> buildCategoryTotals(
            List<Transaction> current, List<Transaction> prior, boolean hasPriorPeriod) {
        Map<Category, BigDecimal> currentByCategory = expenseByCategory(current);
        Map<Category, BigDecimal> priorByCategory = hasPriorPeriod ? expenseByCategory(prior) : Map.of();

        Set<Category> categories = new LinkedHashSet<>();
        categories.addAll(currentByCategory.keySet());
        categories.addAll(priorByCategory.keySet());

        return categories.stream()
                .map(c -> {
                    BigDecimal amount = currentByCategory.getOrDefault(c, BigDecimal.ZERO);
                    BigDecimal previousAmount = hasPriorPeriod
                            ? priorByCategory.getOrDefault(c, BigDecimal.ZERO)
                            : null;
                    return new CategoryTotal(c, amount, previousAmount);
                })
                .filter(ct -> ct.amount().compareTo(BigDecimal.ZERO) > 0
                        || (ct.previousAmount() != null && ct.previousAmount().compareTo(BigDecimal.ZERO) > 0))
                .sorted(Comparator.comparing(CategoryTotal::amount).reversed())
                .toList();
    }

    /** EXPENSE only, grouped by category. Category is required for EXPENSE at the API layer, but
     * the DB column is nullable, so a category-less row (only reachable by bypassing the API) is
     * excluded here rather than grouped under a null key. */
    private static Map<Category, BigDecimal> expenseByCategory(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.EXPENSE && t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));
    }

    private static List<TimeBucket> buildBuckets(
            LocalDate from, LocalDate to, BucketUnit unit, List<Transaction> current) {
        List<TimeBucket> buckets = new ArrayList<>();
        for (LocalDate[] boundary : unit.boundaries(from, to)) {
            LocalDate start = boundary[0];
            LocalDate end = boundary[1];
            BigDecimal income = sumInRange(current, TransactionType.INCOME, start, end);
            BigDecimal expense = sumInRange(current, TransactionType.EXPENSE, start, end);
            buckets.add(new TimeBucket(start, income, expense));
        }
        return buckets;
    }

    private static BigDecimal sumInRange(
            List<Transaction> transactions, TransactionType type, LocalDate start, LocalDate end) {
        return transactions.stream()
                .filter(t -> t.getTransactionType() == type)
                .filter(t -> !t.getTransactionDate().isBefore(start) && !t.getTransactionDate().isAfter(end))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
