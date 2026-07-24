package com.financedash.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedash.domain.AccountType;
import com.financedash.domain.Budget;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BudgetProgressResponse;
import com.financedash.dto.BudgetRequest;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.repository.BudgetRepository;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService service;

    private static Budget budget(long id, String name, String value, Set<Category> categories) {
        Budget b = new Budget(name, new BigDecimal(value), categories);
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }

    private static Transaction tx(TransactionType type, Category category, String amount) {
        return new Transaction(
                "t", new BigDecimal(amount), DATE, AccountType.CHECKING, null, category, type);
    }

    @Nested
    class Crud {

        @Test
        void createSavesBudget() {
            BudgetRequest req = new BudgetRequest(
                    "Food", new BigDecimal("400.00"), EnumSet.of(Category.GROCERIES, Category.DINING_OUT));
            when(budgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Budget saved = service.create(req);

            assertThat(saved.getName()).isEqualTo("Food");
            assertThat(saved.getValue()).isEqualByComparingTo("400.00");
            assertThat(saved.getCategories()).containsExactlyInAnyOrder(Category.GROCERIES, Category.DINING_OUT);
        }

        @Test
        void updateMutatesExisting() {
            Budget existing = budget(1L, "Food", "400.00", EnumSet.of(Category.GROCERIES));
            when(budgetRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(budgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Budget updated = service.update(1L, new BudgetRequest(
                    "Essentials", new BigDecimal("550.00"), EnumSet.of(Category.GROCERIES, Category.UTILITIES)));

            assertThat(updated.getName()).isEqualTo("Essentials");
            assertThat(updated.getValue()).isEqualByComparingTo("550.00");
            assertThat(updated.getCategories()).containsExactlyInAnyOrder(Category.GROCERIES, Category.UTILITIES);
        }

        @Test
        void updateMissingThrows() {
            when(budgetRepository.findById(9L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.update(9L, new BudgetRequest(
                    "x", new BigDecimal("1.00"), EnumSet.of(Category.GROCERIES))))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(budgetRepository, never()).save(any());
        }

        @Test
        void deleteMissingThrows() {
            when(budgetRepository.existsById(9L)).thenReturn(false);
            assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(ResourceNotFoundException.class);
            verify(budgetRepository, never()).deleteById(any());
        }

        @Test
        void findByIdMissingThrows() {
            when(budgetRepository.findById(9L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(9L)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Progress {

        private BudgetProgressResponse progressFor(Budget b, List<Transaction> inPeriod) {
            when(budgetRepository.findAllByOrderByNameAsc()).thenReturn(List.of(b));
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(FROM, TO))
                    .thenReturn(inPeriod);
            return service.progress(FROM, TO).get(0);
        }

        @Test
        void matchingExpenseAddsToSpent() {
            Budget b = budget(1L, "Food", "400.00", EnumSet.of(Category.GROCERIES));
            BudgetProgressResponse p = progressFor(b, List.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "50.00")));
            assertThat(p.spent()).isEqualByComparingTo("50.00");
            assertThat(p.remaining()).isEqualByComparingTo("350.00");
        }

        @Test
        void matchingIncomeDoesNotAddToSpent() {
            // The "expenses only" decision: an INCOME in a budget category must be ignored.
            Budget b = budget(1L, "Food", "400.00", EnumSet.of(Category.SALARY));
            BudgetProgressResponse p = progressFor(b, List.of(tx(TransactionType.INCOME, Category.SALARY, "3000.00")));
            assertThat(p.spent()).isEqualByComparingTo("0");
            assertThat(p.remaining()).isEqualByComparingTo("400.00");
        }

        @Test
        void expenseInDifferentCategoryDoesNotAdd() {
            Budget b = budget(1L, "Food", "400.00", EnumSet.of(Category.GROCERIES));
            BudgetProgressResponse p = progressFor(b, List.of(tx(TransactionType.EXPENSE, Category.TRAVEL, "50.00")));
            assertThat(p.spent()).isEqualByComparingTo("0");
        }

        @Test
        void sumsAcrossMultipleMatchingCategoriesAndGoesOverBudget() {
            Budget b = budget(1L, "Food", "100.00", EnumSet.of(Category.GROCERIES, Category.DINING_OUT));
            BudgetProgressResponse p = progressFor(b, List.of(
                    tx(TransactionType.EXPENSE, Category.GROCERIES, "80.00"),
                    tx(TransactionType.EXPENSE, Category.DINING_OUT, "45.00"),
                    tx(TransactionType.EXPENSE, Category.TRAVEL, "500.00")));
            assertThat(p.spent()).isEqualByComparingTo("125.00");
            assertThat(p.remaining()).isEqualByComparingTo("-25.00"); // over budget → negative remaining
        }

        @Test
        void emptyWhenNoBudgets() {
            when(budgetRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
            when(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(FROM, TO))
                    .thenReturn(List.of(tx(TransactionType.EXPENSE, Category.GROCERIES, "50.00")));
            assertThat(service.progress(FROM, TO)).isEmpty();
        }
    }
}
