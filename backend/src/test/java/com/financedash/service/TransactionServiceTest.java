package com.financedash.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.TransactionRequest;
import com.financedash.exception.InvalidTransactionException;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private TransactionService service;

    private static TransactionRequest request(
            TransactionType type, AccountType account, AccountType linked, Category category) {
        return new TransactionRequest(
                "desc", new BigDecimal("100.00"), DATE, account, linked, category, type);
    }

    @Nested
    class Validation {

        @Test
        void incomeRequiresCategory() {
            TransactionRequest req = request(TransactionType.INCOME, AccountType.CHECKING, null, null);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("category is required for INCOME");
            verify(repository, never()).save(any());
        }

        @Test
        void expenseRequiresCategory() {
            TransactionRequest req = request(TransactionType.EXPENSE, AccountType.CHECKING, null, null);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("category is required for EXPENSE");
        }

        @Test
        void transferMustNotCarryCategory() {
            TransactionRequest req = request(
                    TransactionType.TRANSFER, AccountType.CHECKING, AccountType.SAVINGS, Category.GROCERIES);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("category must not be set for TRANSFER");
        }

        @Test
        void adjustmentMustNotCarryCategory() {
            TransactionRequest req = request(
                    TransactionType.ADJUSTMENT, AccountType.CHECKING, null, Category.SALARY);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("category must not be set for ADJUSTMENT");
        }

        @Test
        void transferRequiresLinkedAccount() {
            TransactionRequest req = request(TransactionType.TRANSFER, AccountType.CHECKING, null, null);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("linkedAccountType is required for TRANSFER");
        }

        @Test
        void transferLinkedAccountMustDiffer() {
            TransactionRequest req = request(
                    TransactionType.TRANSFER, AccountType.CHECKING, AccountType.CHECKING, null);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("must differ from accountType");
        }

        @Test
        void incomeMustNotCarryLinkedAccount() {
            TransactionRequest req = request(
                    TransactionType.INCOME, AccountType.CHECKING, AccountType.SAVINGS, Category.SALARY);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("linkedAccountType must not be set for INCOME");
        }

        @Test
        void adjustmentMustNotCarryLinkedAccount() {
            TransactionRequest req = request(
                    TransactionType.ADJUSTMENT, AccountType.CHECKING, AccountType.SAVINGS, null);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("linkedAccountType must not be set for ADJUSTMENT");
        }

        @Test
        void rejectsInvestingAsAccountType() {
            // Investing left the transaction ledger — it's its own entity now.
            TransactionRequest req = request(
                    TransactionType.EXPENSE, AccountType.INVESTING, null, Category.GROCERIES);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("INVESTING");
            verify(repository, never()).save(any());
        }

        @Test
        void rejectsInvestingAsLinkedAccount() {
            TransactionRequest req = request(
                    TransactionType.TRANSFER, AccountType.CHECKING, AccountType.INVESTING, null);
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("INVESTING");
            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Create {

        @Test
        void persistsValidIncome() {
            TransactionRequest req = request(
                    TransactionType.INCOME, AccountType.CHECKING, null, Category.SALARY);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Transaction saved = service.create(req);

            assertThat(saved.getDescription()).isEqualTo("desc");
            assertThat(saved.getAmount()).isEqualByComparingTo("100.00");
            assertThat(saved.getTransactionType()).isEqualTo(TransactionType.INCOME);
            assertThat(saved.getAccountType()).isEqualTo(AccountType.CHECKING);
            assertThat(saved.getCategory()).isEqualTo(Category.SALARY);
            assertThat(saved.getLinkedAccountType()).isNull();
            verify(repository).save(any());
        }

        @Test
        void persistsValidTransfer() {
            TransactionRequest req = request(
                    TransactionType.TRANSFER, AccountType.CHECKING, AccountType.SAVINGS, null);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Transaction saved = service.create(req);

            assertThat(saved.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
            assertThat(saved.getLinkedAccountType()).isEqualTo(AccountType.SAVINGS);
            assertThat(saved.getCategory()).isNull();
        }

        @Test
        void persistsValidAdjustment() {
            TransactionRequest req = request(
                    TransactionType.ADJUSTMENT, AccountType.SAVINGS, null, null);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Transaction saved = service.create(req);

            assertThat(saved.getTransactionType()).isEqualTo(TransactionType.ADJUSTMENT);
            assertThat(saved.getCategory()).isNull();
            assertThat(saved.getLinkedAccountType()).isNull();
        }
    }

    @Nested
    class Update {

        @Test
        void updatesExistingTransaction() {
            Transaction existing = new Transaction(
                    "old", new BigDecimal("10.00"), DATE,
                    AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE);
            when(repository.findById(5L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransactionRequest req = new TransactionRequest(
                    "new", new BigDecimal("42.50"), DATE,
                    AccountType.SAVINGS, null, Category.SHOPPING, TransactionType.EXPENSE);
            Transaction updated = service.update(5L, req);

            assertThat(updated.getDescription()).isEqualTo("new");
            assertThat(updated.getAmount()).isEqualByComparingTo("42.50");
            assertThat(updated.getAccountType()).isEqualTo(AccountType.SAVINGS);
            assertThat(updated.getCategory()).isEqualTo(Category.SHOPPING);
        }

        @Test
        void failsWhenTransactionMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());
            TransactionRequest req = request(
                    TransactionType.EXPENSE, AccountType.CHECKING, null, Category.GROCERIES);

            assertThatThrownBy(() -> service.update(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
            verify(repository, never()).save(any());
        }

        @Test
        void validatesBeforeLookup() {
            // Invalid request must fail validation without ever touching the repository.
            TransactionRequest invalid = request(TransactionType.TRANSFER, AccountType.CHECKING, null, null);
            assertThatThrownBy(() -> service.update(1L, invalid))
                    .isInstanceOf(InvalidTransactionException.class);
            verifyNoInteractions(repository);
        }
    }

    @Nested
    class FindAndDelete {

        @Test
        void findByIdReturnsTransaction() {
            Transaction txn = new Transaction(
                    "d", new BigDecimal("1.00"), DATE,
                    AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE);
            when(repository.findById(1L)).thenReturn(Optional.of(txn));

            assertThat(service.findById(1L)).isSameAs(txn);
        }

        @Test
        void findByIdThrowsWhenMissing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void deleteRemovesExisting() {
            Transaction txn = new Transaction(
                    "d", new BigDecimal("1.00"), DATE,
                    AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE);
            when(repository.findById(1L)).thenReturn(Optional.of(txn));
            service.delete(1L);
            verify(repository).deleteById(1L);
        }

        @Test
        void deleteThrowsWhenMissing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(repository, never()).deleteById(any());
        }
    }

    /**
     * Rows generated from an investments-service cash leg are the source of truth for that cash
     * movement while the service still holds the position — mutating one would desync the two.
     */
    @Nested
    class SystemGeneratedRowsAreReadOnly {

        private Transaction investmentRow() {
            return new Transaction("Bought AAPL", new BigDecimal("500.00"), DATE,
                    AccountType.CHECKING, AccountType.INVESTING, null, TransactionType.TRANSFER, "evt-1");
        }

        @Test
        void deleteIsRejected() {
            when(repository.findById(1L)).thenReturn(Optional.of(investmentRow()));

            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("cannot be edited or deleted");
            verify(repository, never()).deleteById(any());
        }

        @Test
        void updateIsRejected() {
            when(repository.findById(1L)).thenReturn(Optional.of(investmentRow()));

            assertThatThrownBy(() -> service.update(1L, new TransactionRequest(
                    "hacked", new BigDecimal("1.00"), DATE, AccountType.CHECKING, null,
                    Category.GROCERIES, TransactionType.EXPENSE)))
                    .isInstanceOf(InvalidTransactionException.class)
                    .hasMessageContaining("cannot be edited or deleted");
            verify(repository, never()).save(any());
        }
    }

    @Nested
    class FindAllRouting {

        private static final Pageable PAGEABLE = PageRequest.of(0, 20,
                Sort.by(Sort.Direction.DESC, "transactionDate").and(Sort.by(Sort.Direction.DESC, "id")));

        @Test
        void noFiltersUsesDateOnlyQuery() {
            service.findAll(FROM, TO, null, null, PAGEABLE);
            verify(repository).findByTransactionDateBetween(FROM, TO, PAGEABLE);
        }

        @Test
        void accountFilterUsesAccountQuery() {
            service.findAll(FROM, TO, AccountType.SAVINGS, null, PAGEABLE);
            verify(repository).findByTransactionDateBetweenAndAccountType(FROM, TO, AccountType.SAVINGS, PAGEABLE);
        }

        @Test
        void categoryFilterUsesCategoryQuery() {
            service.findAll(FROM, TO, null, Category.GROCERIES, PAGEABLE);
            verify(repository).findByTransactionDateBetweenAndCategory(FROM, TO, Category.GROCERIES, PAGEABLE);
        }

        @Test
        void bothFiltersUseCombinedQuery() {
            service.findAll(FROM, TO, AccountType.CHECKING, Category.DINING_OUT, PAGEABLE);
            verify(repository).findByTransactionDateBetweenAndAccountTypeAndCategory(
                    FROM, TO, AccountType.CHECKING, Category.DINING_OUT, PAGEABLE);
        }

        @Test
        void returnsRepositoryResults() {
            Page<Transaction> expected = new PageImpl<>(List.of(new Transaction(
                    "d", new BigDecimal("1.00"), DATE,
                    AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE)));
            when(repository.findByTransactionDateBetween(FROM, TO, PAGEABLE)).thenReturn(expected);

            assertThat(service.findAll(FROM, TO, null, null, PAGEABLE)).isEqualTo(expected);
        }
    }
}
