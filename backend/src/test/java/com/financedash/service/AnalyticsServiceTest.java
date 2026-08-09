package com.financedash.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.AnalyticsResponse;
import com.financedash.dto.BucketUnit;
import com.financedash.dto.CategoryTotal;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final LocalDate TO = LocalDate.of(2026, 8, 2);

    @Mock
    private TransactionRepository transactionRepository;

    private AnalyticsService service;

    private AnalyticsService serviceWith() {
        return new AnalyticsService(transactionRepository);
    }

    private static Transaction tx(TransactionType type, Category category, String amount, LocalDate date) {
        return new Transaction("t", new BigDecimal(amount), date, AccountType.CHECKING, null, category, type);
    }

    private static Transaction transfer(String amount, LocalDate date) {
        return new Transaction(
                "t", new BigDecimal(amount), date, AccountType.CHECKING, AccountType.SAVINGS, null,
                TransactionType.TRANSFER);
    }

    private static Transaction adjustment(String amount, LocalDate date) {
        return new Transaction(
                "t", new BigDecimal(amount), date, AccountType.CHECKING, null, null, TransactionType.ADJUSTMENT);
    }

    /** Stubs the repository to answer any date-range query with an empty list, unless overridden. */
    private void stubEmptyRangesByDefault() {
        when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
    }

    @Nested
    class WindowResolution {

        @Test
        void capAppliesToAnOverlongExplicitRange() {
            service = serviceWith();
            stubEmptyRangesByDefault();
            LocalDate explicitFrom = LocalDate.of(2020, 1, 1);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc())
                    .thenReturn(Optional.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "1.00",
                            LocalDate.of(2019, 1, 1))));

            AnalyticsResponse response = service.getAnalytics(explicitFrom, explicitFrom, TO);

            // The cap wins even though the caller named an explicit `from`.
            assertThat(response.from()).isEqualTo(TO.minusYears(1).plusDays(1));
        }

        @Test
        void floorAppliesToANamedRangeButNotToExplicitDates() {
            service = serviceWith();
            stubEmptyRangesByDefault();
            LocalDate earliestTxDate = TO.minusDays(5);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc())
                    .thenReturn(Optional.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "1.00", earliestTxDate)));

            // Named range (explicitFrom = null): a MONTH-shaped window gets floored to the earliest
            // transaction, since it's more recent than the naive window start.
            LocalDate monthWindowFrom = TO.minusMonths(1).plusDays(1);
            AnalyticsResponse namedRange = service.getAnalytics(null, monthWindowFrom, TO);
            assertThat(namedRange.from()).isEqualTo(earliestTxDate);

            // Same window, but the caller named explicit dates: the floor does not apply, even
            // though the earliest transaction is more recent than the window start.
            AnalyticsResponse explicitDates = service.getAnalytics(monthWindowFrom, monthWindowFrom, TO);
            assertThat(explicitDates.from()).isEqualTo(monthWindowFrom);
        }

        @Test
        void allAndYearProduceIdenticalOutputWhenHistoryExceedsAYear() {
            service = serviceWith();
            stubEmptyRangesByDefault();
            // Over two years of history -- well before the one-year cap in either case.
            when(transactionRepository.findFirstByOrderByTransactionDateAsc())
                    .thenReturn(Optional.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "1.00",
                            LocalDate.of(2023, 1, 1))));

            // ALL resolves (via Period.resolve, simulated here) to the epoch; YEAR resolves to
            // exactly the cap boundary. Both must collapse to the same final window.
            AnalyticsResponse all = service.getAnalytics(null, LocalDate.of(1970, 1, 1), TO);
            AnalyticsResponse year = service.getAnalytics(null, TO.minusYears(1).plusDays(1), TO);

            assertThat(all.from()).isEqualTo(year.from()).isEqualTo(TO.minusYears(1).plusDays(1));
            assertThat(all.bucketUnit()).isEqualTo(year.bucketUnit());
            // With >1 year of history, a real prior period exists for both.
            assertThat(all.previousFrom()).isNotNull().isEqualTo(year.previousFrom());
        }

        @Test
        void bucketUnitDerivedFromTheFinalWindow() {
            service = serviceWith();
            stubEmptyRangesByDefault();
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());

            LocalDate from = TO.minusDays(10);
            AnalyticsResponse response = service.getAnalytics(from, from, TO);
            assertThat(response.bucketUnit()).isEqualTo(BucketUnit.DAY);
        }
    }

    @Nested
    class Aggregates {

        @Test
        void transferAndAdjustmentExcludedFromTotals() {
            service = serviceWith();
            LocalDate from = TO.minusDays(10);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, TO))
                    .thenReturn(List.of(
                            tx(TransactionType.EXPENSE, Category.GROCERIES, "50.00", from),
                            transfer("1000.00", from),
                            adjustment("5000.00", from)));

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.totalExpense()).isEqualByComparingTo("50.00");
            assertThat(response.totalIncome()).isEqualByComparingTo("0");
            assertThat(response.buckets()).allSatisfy(b -> {
                if (!b.start().equals(from)) {
                    assertThat(b.expense()).isEqualByComparingTo("0");
                }
            });
        }

        @Test
        void categoryLessExpenseCountsTowardTotalButNotAnyCategoryRow() {
            service = serviceWith();
            LocalDate from = TO.minusDays(5);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, TO))
                    .thenReturn(List.of(tx(TransactionType.EXPENSE, null, "75.00", from)));

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.totalExpense()).isEqualByComparingTo("75.00");
            assertThat(response.categories()).isEmpty();
        }

        @Test
        void categoriesSortedDescByAmountAndZeroBothPeriodsOmitted() {
            service = serviceWith();
            LocalDate from = TO.minusDays(5);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, TO))
                    .thenReturn(List.of(
                            tx(TransactionType.EXPENSE, Category.GROCERIES, "40.00", from),
                            tx(TransactionType.EXPENSE, Category.DINING_OUT, "100.00", from)));

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.categories()).extracting(CategoryTotal::category)
                    .containsExactly(Category.DINING_OUT, Category.GROCERIES);
        }

        @Test
        void incomeAndExpenseCategoriesLandInSeparateListsNeverBothInOne() {
            // The cash-flow diagram reads incomeCategories as its source side; every other consumer
            // reads categories and means EXPENSE by it. A row must never appear in both.
            service = serviceWith();
            LocalDate from = TO.minusDays(5);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, TO))
                    .thenReturn(List.of(
                            tx(TransactionType.EXPENSE, Category.GROCERIES, "40.00", from),
                            tx(TransactionType.INCOME, Category.SALARY, "3000.00", from),
                            tx(TransactionType.INCOME, Category.FREELANCE_INCOME, "500.00", from)));

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.categories()).extracting(CategoryTotal::category)
                    .containsExactly(Category.GROCERIES);
            assertThat(response.incomeCategories()).extracting(CategoryTotal::category)
                    .containsExactly(Category.SALARY, Category.FREELANCE_INCOME);
            assertThat(response.incomeCategories()).extracting(CategoryTotal::amount)
                    .satisfiesExactly(
                            a -> assertThat(a).isEqualByComparingTo("3000.00"),
                            a -> assertThat(a).isEqualByComparingTo("500.00"));
        }

        @Test
        void incomeCategoriesEmptyWhenTheWindowHasExpensesOnly() {
            service = serviceWith();
            LocalDate from = TO.minusDays(5);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, TO))
                    .thenReturn(List.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "40.00", from)));

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.incomeCategories()).isEmpty();
        }

        @Test
        void gapFilledBucketsCoverTheWholeWindowContiguously() {
            service = serviceWith();
            LocalDate from = TO.minusDays(4);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(from, TO))
                    .thenReturn(List.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "10.00", from)));

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.buckets()).hasSize(5); // 5-day window, DAY buckets
            assertThat(response.buckets()).extracting(b -> b.start()).containsExactly(
                    from, from.plusDays(1), from.plusDays(2), from.plusDays(3), from.plusDays(4));
        }
    }

    @Nested
    class PriorPeriod {

        @Test
        void windowArithmeticIsTheImmediatelyPrecedingEqualLengthWindow() {
            service = serviceWith();
            LocalDate from = LocalDate.of(2026, 7, 4); // 30-day window with TO
            when(transactionRepository.findFirstByOrderByTransactionDateAsc())
                    .thenReturn(Optional.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "1.00",
                            LocalDate.of(2020, 1, 1))));
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                    eq(from), eq(TO))).thenReturn(List.of());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                    eq(LocalDate.of(2026, 6, 4)), eq(LocalDate.of(2026, 7, 3))))
                    .thenReturn(List.of());

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.previousTo()).isEqualTo(LocalDate.of(2026, 7, 3));
            assertThat(response.previousFrom()).isEqualTo(LocalDate.of(2026, 6, 4));
        }

        @Test
        void nulledWhenFromIsOnTheEarliestTransactionDate() {
            service = serviceWith();
            LocalDate earliest = TO.minusDays(10);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc())
                    .thenReturn(Optional.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "1.00", earliest)));
            // Named range floored exactly to the earliest transaction date.
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                    eq(earliest), eq(TO))).thenReturn(List.of());

            AnalyticsResponse response = service.getAnalytics(null, TO.minusYears(1).plusDays(1), TO);

            assertThat(response.from()).isEqualTo(earliest);
            assertThat(response.previousFrom()).isNull();
            assertThat(response.previousTo()).isNull();
            assertThat(response.categories()).allSatisfy(c -> assertThat(c.previousAmount()).isNull());
        }

        @Test
        void categoryPresentOnlyInPriorPeriodStillAppearsWithZero() {
            service = serviceWith();
            LocalDate from = LocalDate.of(2026, 7, 4);
            LocalDate previousFrom = LocalDate.of(2026, 6, 4);
            LocalDate previousTo = LocalDate.of(2026, 7, 3);
            when(transactionRepository.findFirstByOrderByTransactionDateAsc())
                    .thenReturn(Optional.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "1.00",
                            LocalDate.of(2020, 1, 1))));
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(eq(from), eq(TO)))
                    .thenReturn(List.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "40.00", from)));
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                    eq(previousFrom), eq(previousTo)))
                    .thenReturn(List.of(tx(TransactionType.EXPENSE, Category.TRAVEL, "200.00", previousFrom)));

            AnalyticsResponse response = service.getAnalytics(from, from, TO);

            assertThat(response.categories()).extracting(CategoryTotal::category)
                    .containsExactlyInAnyOrder(Category.GROCERIES, Category.TRAVEL);
            CategoryTotal travel = response.categories().stream()
                    .filter(c -> c.category() == Category.TRAVEL).findFirst().orElseThrow();
            assertThat(travel.amount()).isEqualByComparingTo("0");
            assertThat(travel.previousAmount()).isEqualByComparingTo("200.00");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void zeroTransactionsReturnsEmptyRatherThanThrowing() {
            service = serviceWith();
            stubEmptyRangesByDefault();
            when(transactionRepository.findFirstByOrderByTransactionDateAsc()).thenReturn(Optional.empty());

            AnalyticsResponse response = service.getAnalytics(null, TO.minusMonths(1).plusDays(1), TO);

            assertThat(response.totalIncome()).isEqualByComparingTo("0");
            assertThat(response.totalExpense()).isEqualByComparingTo("0");
            assertThat(response.categories()).isEmpty();
            assertThat(response.previousFrom()).isNull();
        }
    }
}
