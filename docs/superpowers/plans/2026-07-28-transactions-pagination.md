# Transactions Pagination, Sorting & Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `GET /api/transactions` from an unpaginated `List<TransactionResponse>` into a paged,
sortable (date or amount), still-filterable (accountType/category) endpoint, backed by real indexes,
with the frontend updated to consume and drive it.

**Architecture:** Backend: Spring Data `Page`/`Pageable`/`Sort` end-to-end (repository → service →
controller), wrapped at the controller boundary in a new `PageResponse<T>` record so every DTO
stays a plain record per this codebase's convention. New single-column Postgres indexes via a
Flyway migration support the new filtered/sorted query shape. Frontend: the existing
filters-state → hook → API-client chain grows a `page` dimension and two new sort fields; a new
generic `Pagination` component renders numbered page controls.

**Tech Stack:** Spring Boot (Spring Data JPA, Flyway, Postgres), React + TypeScript (Vite), no new
dependencies on either side.

**Source spec:** `docs/superpowers/specs/2026-07-28-transactions-pagination-design.md`

## Global Constraints

- Page size: default `20`, max `100` — exceeding the max throws `InvalidTransactionException`
  (already mapped to 400 by `GlobalExceptionHandler`), no new exception type.
- Sort params `sortBy` (`DATE`|`AMOUNT`, default `DATE`) and `sortDir` (`ASC`|`DESC`, default
  `DESC`) are uppercase, matching every other enum-bound query param in this codebase
  (`accountType`, `category`, `range`).
- Every sort **always** appends `id DESC` as a secondary key, regardless of the primary field, so
  page boundaries stay stable across ties.
- `category`/`accountType` filters stay single-value — no multi-select.
- Response shape is a new `PageResponse<T>` **record** (`content`, `page`, `size`,
  `totalElements`, `totalPages`) — never Spring's `Page`/`PageImpl` serialized directly.
- This is a breaking API change, shipped in one PR with the frontend update — no versioning, no
  transition period.
- The frontend has **no test runner configured** (`package.json` has no `test` script, no
  `*.test.ts(x)` files exist). `npm run build` (`tsc -b && vite build`) is the only
  typecheck/lint-equivalent gate — do not introduce a test framework as part of this plan.
- `BalanceService`/`BudgetService` query `TransactionRepository` directly for aggregates, not via
  `TransactionService.findAll` — nothing in this plan touches dashboard/budget math.

---

### Task 1: Database indexes for the new query shape

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__add_transaction_indexes.sql`

**Interfaces:**
- Produces: four indexes (`idx_transactions_transaction_date`, `idx_transactions_account_type`,
  `idx_transactions_category`, `idx_transactions_amount`) that later tasks' queries will run against.
  No code depends on these directly — they're a performance floor, verified by the existing
  Testcontainers suite applying all migrations at startup.

- [ ] **Step 1: Write the migration**

```sql
-- Supports the filtered/sorted GET /api/transactions query shape added in the
-- pagination feature: WHERE transaction_date BETWEEN ... [AND account_type = ?]
-- [AND category = ?] ORDER BY <transaction_date|amount>, id.
CREATE INDEX IF NOT EXISTS idx_transactions_transaction_date ON transactions (transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_account_type ON transactions (account_type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions (category);
CREATE INDEX IF NOT EXISTS idx_transactions_amount ON transactions (amount);
```

- [ ] **Step 2: Verify Flyway applies it cleanly**

Run: `cd backend && mvn verify -Dit.test=FinanceDashApplicationIT`
Expected: PASS — this IT boots the full Spring context against a Testcontainers Postgres, which
runs every migration in `db/migration` in order. A syntax error or bad table/column name in the
new file fails this test with a Flyway migration error.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__add_transaction_indexes.sql
git commit -m "feat(backend): add indexes supporting paginated transaction queries"
```

---

### Task 2: `PageResponse<T>` DTO

**Files:**
- Create: `backend/src/main/java/com/financedash/dto/PageResponse.java`
- Test: `backend/src/test/java/com/financedash/dto/PageResponseTest.java`

**Interfaces:**
- Produces: `PageResponse<T>(List<T> content, int page, int size, long totalElements, int
  totalPages)` and `static <X, T> PageResponse<T> from(Page<X> source, Function<X, T> mapper)`.
  Task 6 (controller) calls `PageResponse.from(pageOfTransactions, TransactionResponse::from)`.

- [ ] **Step 1: Write the failing test**

```java
package com.financedash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void mapsContentAndPageMetadata() {
        PageImpl<Integer> source = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(1, 3), 10);

        PageResponse<String> result = PageResponse.from(source, i -> "n" + i);

        assertThat(result.content()).containsExactly("n1", "n2", "n3");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.totalElements()).isEqualTo(10);
        assertThat(result.totalPages()).isEqualTo(4);
    }

    @Test
    void handlesEmptyPage() {
        PageImpl<Integer> source = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> result = PageResponse.from(source, i -> "n" + i);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PageResponseTest`
Expected: FAIL — `com.financedash.dto.PageResponse` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.financedash.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <X, T> PageResponse<T> from(Page<X> source, Function<X, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages()
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PageResponseTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/financedash/dto/PageResponse.java \
        backend/src/test/java/com/financedash/dto/PageResponseTest.java
git commit -m "feat(backend): add PageResponse<T> DTO for paginated endpoints"
```

---

### Task 3: `TransactionSortBy` enum

**Files:**
- Create: `backend/src/main/java/com/financedash/dto/TransactionSortBy.java`
- Test: `backend/src/test/java/com/financedash/dto/TransactionSortByTest.java`

**Interfaces:**
- Produces: `enum TransactionSortBy { DATE, AMOUNT }` with `String field()` returning the JPA
  entity property name. Task 6 (controller) calls `sortBy.field()` when building the `Sort`.
  Binds directly from the `sortBy` query param via Spring's default enum converter — same
  mechanism already used for `accountType`/`category`/`range` elsewhere in this codebase.

- [ ] **Step 1: Write the failing test**

```java
package com.financedash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionSortByTest {

    @Test
    void dateResolvesToTransactionDateField() {
        assertThat(TransactionSortBy.DATE.field()).isEqualTo("transactionDate");
    }

    @Test
    void amountResolvesToAmountField() {
        assertThat(TransactionSortBy.AMOUNT.field()).isEqualTo("amount");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=TransactionSortByTest`
Expected: FAIL — `com.financedash.dto.TransactionSortBy` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.financedash.dto;

/** Shorthand for the `sortBy` query param on GET /api/transactions, resolved to an entity field. */
public enum TransactionSortBy {
    DATE("transactionDate"),
    AMOUNT("amount");

    private final String field;

    TransactionSortBy(String field) {
        this.field = field;
    }

    public String field() {
        return field;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=TransactionSortByTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/financedash/dto/TransactionSortBy.java \
        backend/src/test/java/com/financedash/dto/TransactionSortByTest.java
git commit -m "feat(backend): add TransactionSortBy enum for the sortBy query param"
```

---

### Task 4: `TransactionRepository` — paginate the derived queries

**Files:**
- Modify: `backend/src/main/java/com/financedash/repository/TransactionRepository.java`
- Modify: `backend/src/test/java/com/financedash/repository/TransactionRepositoryIT.java`

**Interfaces:**
- Consumes: nothing new from earlier tasks.
- Produces: four repository methods, all now `Page<Transaction>` and taking a trailing
  `Pageable`: `findByTransactionDateBetween`, `findByTransactionDateBetweenAndAccountType`,
  `findByTransactionDateBetweenAndCategory`, `findByTransactionDateBetweenAndAccountTypeAndCategory`.
  Task 5 (`TransactionService`) calls these with a `Pageable` it builds. `findByTransactionDateLessThanEqual`
  is unrelated to this endpoint (used elsewhere) and is untouched.

- [ ] **Step 1: Update the failing/changed repository tests first**

Replace `backend/src/test/java/com/financedash/repository/TransactionRepositoryIT.java` in full:

```java
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Verifies the Spring Data derived queries against a real Postgres: date-range
 * inclusivity, the optional account/category filters, and pagination/sort behavior
 * driven entirely by the passed-in Pageable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class TransactionRepositoryIT extends AbstractPostgresContainerTest {

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private Transaction persist(LocalDate date, AccountType account, Category category, TransactionType type) {
        return persist(date, account, category, type, new BigDecimal("10.00"));
    }

    private Transaction persist(
            LocalDate date, AccountType account, Category category, TransactionType type, BigDecimal amount) {
        Transaction t = new Transaction("t", amount, date, account, null, category, type);
        return entityManager.persistAndFlush(t);
    }

    private static Pageable dateDescPageable(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "transactionDate").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void betweenIsInclusiveOnBothEnds() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        persist(from, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);           // on lower bound
        persist(to, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);             // on upper bound
        persist(from.minusDays(1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE); // just before
        persist(to.plusDays(1), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);    // just after

        Page<Transaction> result = repository.findByTransactionDateBetween(from, to, dateDescPageable(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allSatisfy(t ->
                assertThat(t.getTransactionDate()).isBetween(from, to));
    }

    @Test
    void ordersByDateThenIdDescendingByDefault() {
        LocalDate older = LocalDate.of(2026, 6, 1);
        LocalDate newer = LocalDate.of(2026, 6, 15);
        Transaction first = persist(newer, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        Transaction second = persist(newer, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        Transaction third = persist(older, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);

        Page<Transaction> result = repository.findByTransactionDateBetween(FROM, TO, dateDescPageable(0, 20));

        // Newest date first; within the same date, the higher id (later insert) comes first.
        assertThat(result.getContent()).extracting(Transaction::getId)
                .containsExactly(second.getId(), first.getId(), third.getId());
    }

    @Test
    void ordersByAmountAscendingWhenRequested() {
        Transaction cheap = persist(FROM, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE,
                new BigDecimal("5.00"));
        Transaction mid = persist(FROM, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE,
                new BigDecimal("50.00"));
        Transaction expensive = persist(FROM, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE,
                new BigDecimal("500.00"));

        Pageable amountAsc = PageRequest.of(0, 20,
                Sort.by(Sort.Direction.ASC, "amount").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Transaction> result = repository.findByTransactionDateBetween(FROM, TO, amountAsc);

        assertThat(result.getContent()).extracting(Transaction::getId)
                .containsExactly(cheap.getId(), mid.getId(), expensive.getId());
    }

    @Test
    void pagesResultsAndReportsTotals() {
        for (int i = 0; i < 5; i++) {
            persist(FROM.plusDays(i), AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        }

        Page<Transaction> firstPage = repository.findByTransactionDateBetween(FROM, TO, dateDescPageable(0, 2));
        Page<Transaction> secondPage = repository.findByTransactionDateBetween(FROM, TO, dateDescPageable(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(secondPage.getContent()).hasSize(2);
        // Pages don't overlap.
        assertThat(firstPage.getContent()).extracting(Transaction::getId)
                .doesNotContainAnyElementsOf(secondPage.getContent().stream().map(Transaction::getId).toList());
    }

    @Test
    void filtersByAccountType() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        persist(d, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(d, AccountType.SAVINGS, Category.GROCERIES, TransactionType.EXPENSE);

        Page<Transaction> result = repository.findByTransactionDateBetweenAndAccountType(
                FROM, TO, AccountType.SAVINGS, dateDescPageable(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAccountType()).isEqualTo(AccountType.SAVINGS);
    }

    @Test
    void filtersByAccountTypeAndCategory() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        persist(d, AccountType.CHECKING, Category.GROCERIES, TransactionType.EXPENSE);
        persist(d, AccountType.CHECKING, Category.DINING_OUT, TransactionType.EXPENSE);
        persist(d, AccountType.SAVINGS, Category.GROCERIES, TransactionType.EXPENSE);

        Page<Transaction> result = repository.findByTransactionDateBetweenAndAccountTypeAndCategory(
                FROM, TO, AccountType.CHECKING, Category.GROCERIES, dateDescPageable(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo(Category.GROCERIES);
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
```

- [ ] **Step 2: Run the IT to verify it fails**

Run: `cd backend && mvn verify -Dit.test=TransactionRepositoryIT`
Expected: FAIL to compile — the repository methods referenced (`findByTransactionDateBetween(from, to,
Pageable)` etc.) don't exist yet under these names/signatures.

- [ ] **Step 3: Update the repository interface**

Replace `backend/src/main/java/com/financedash/repository/TransactionRepository.java` in full:

```java
package com.financedash.repository;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
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
}
```

Note: the sort is no longer baked into the method name (dropped `OrderByTransactionDateDescIdDesc`)
— it now comes entirely from whatever `Sort` the caller's `Pageable` carries, since the sort field
must vary (date vs. amount) based on the client's request.

- [ ] **Step 4: Run the IT to verify it passes**

Run: `cd backend && mvn verify -Dit.test=TransactionRepositoryIT`
Expected: PASS (needs Docker running — this is a Testcontainers IT).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/financedash/repository/TransactionRepository.java \
        backend/src/test/java/com/financedash/repository/TransactionRepositoryIT.java
git commit -m "feat(backend): paginate and sort TransactionRepository queries"
```

---

### Task 5: `TransactionService.findAll` — thread the `Pageable` through

**Files:**
- Modify: `backend/src/main/java/com/financedash/service/TransactionService.java:24-39`
- Modify: `backend/src/test/java/com/financedash/service/TransactionServiceTest.java` (the
  `FindAllRouting` nested class, lines 271-312)

**Interfaces:**
- Consumes: `TransactionRepository`'s four paginated finder methods from Task 4.
- Produces: `Page<Transaction> findAll(LocalDate from, LocalDate to, AccountType accountType,
  Category category, Pageable pageable)`. Task 6 (`TransactionController`) calls this and passes
  the result straight to `PageResponse.from(...)`.

- [ ] **Step 1: Update the failing service tests first**

Replace the `FindAllRouting` nested class in `TransactionServiceTest.java` (keep every other nested
class in that file untouched):

```java
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
```

Add these imports to the top of `TransactionServiceTest.java` alongside the existing ones:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn test -Dtest=TransactionServiceTest`
Expected: FAIL to compile — `TransactionService.findAll` doesn't accept a `Pageable` yet, and
`TransactionRepository`'s mocked methods don't match these new signatures/return types.

- [ ] **Step 3: Update `TransactionService.findAll`**

Replace lines 24-39 of `backend/src/main/java/com/financedash/service/TransactionService.java`:

```java
    public Page<Transaction> findAll(
            LocalDate from, LocalDate to, AccountType accountType, Category category, Pageable pageable) {
        if (accountType != null && category != null) {
            return transactionRepository
                    .findByTransactionDateBetweenAndAccountTypeAndCategory(from, to, accountType, category, pageable);
        }
        if (accountType != null) {
            return transactionRepository
                    .findByTransactionDateBetweenAndAccountType(from, to, accountType, pageable);
        }
        if (category != null) {
            return transactionRepository
                    .findByTransactionDateBetweenAndCategory(from, to, category, pageable);
        }
        return transactionRepository.findByTransactionDateBetween(from, to, pageable);
    }
```

Add to the imports at the top of the file:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

(The `java.util.List` import stays — it's still used by nothing else in this class after this
change only if another method needs it; check and remove if now unused. As of this file's current
content, `List` was only used by the old `findAll` return type, so remove the `import java.util.List;`
line.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn test -Dtest=TransactionServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/financedash/service/TransactionService.java \
        backend/src/test/java/com/financedash/service/TransactionServiceTest.java
git commit -m "feat(backend): thread Pageable through TransactionService.findAll"
```

---

### Task 6: `TransactionController` — new query params + `PageResponse` wrapping

**Files:**
- Modify: `backend/src/main/java/com/financedash/controller/TransactionController.java:35-46`
- Modify: `backend/src/test/java/com/financedash/controller/TransactionControllerTest.java`
  (the `listReturnsArray` and `listPassesFilterParamsThrough` tests, lines 187-212)

**Interfaces:**
- Consumes: `TransactionService.findAll(..., Pageable)` from Task 5, `PageResponse.from(...)` from
  Task 2, `TransactionSortBy` from Task 3.
- Produces: `GET /api/transactions` now returns `PageResponse<TransactionResponse>` and accepts
  `page`, `size`, `sortBy`, `sortDir` in addition to the existing `from`/`to`/`accountType`/`category`.
  This is what Task 8 (frontend API client) targets.

- [ ] **Step 1: Update the failing controller tests first**

Replace `listReturnsArray` and `listPassesFilterParamsThrough` in `TransactionControllerTest.java`:

```java
    @Test
    void listReturnsPageResponse() throws Exception {
        Page<Transaction> page = new PageImpl<>(List.of(sampleIncome()), PageRequest.of(0, 20), 1);
        when(service.findAll(any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listPassesFilterParamsThrough() throws Exception {
        Page<Transaction> empty = new PageImpl<>(List.of());
        when(service.findAll(any(), any(), eq(AccountType.SAVINGS), eq(Category.GROCERIES), any()))
                .thenReturn(empty);

        mockMvc.perform(get("/api/transactions")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31")
                        .param("accountType", "SAVINGS")
                        .param("category", "GROCERIES"))
                .andExpect(status().isOk());

        verify(service).findAll(
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)),
                eq(AccountType.SAVINGS), eq(Category.GROCERIES), any());
    }

    @Test
    void listBuildsPageableFromPageSizeSortParams() throws Exception {
        Page<Transaction> empty = new PageImpl<>(List.of());
        when(service.findAll(any(), any(), any(), any(), any())).thenReturn(empty);

        mockMvc.perform(get("/api/transactions")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sortBy", "AMOUNT")
                        .param("sortDir", "ASC"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(any(), any(), any(), any(), captor.capture());
        Pageable captured = captor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(2);
        assertThat(captured.getPageSize()).isEqualTo(10);
        Sort.Order primary = captured.getSort().getOrderFor("amount");
        assertThat(primary).isNotNull();
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
        Sort.Order tiebreak = captured.getSort().getOrderFor("id");
        assertThat(tiebreak).isNotNull();
        assertThat(tiebreak.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listReturns400WhenSizeExceedsMax() throws Exception {
        mockMvc.perform(get("/api/transactions").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
```

Add these imports to the top of `TransactionControllerTest.java`:

```java
import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
```

(Remove the now-unused `import java.util.List;` only if nothing else in the file still needs it —
`sampleIncome()`/`validRequest()` don't use `List`, but check the rest of the file before deleting;
if any other test still builds a `List.of(...)`, keep the import.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn test -Dtest=TransactionControllerTest`
Expected: FAIL to compile — `TransactionController.findAll` doesn't have the new params yet, and
`service.findAll` doesn't have a 5-arg signature.

- [ ] **Step 3: Update `TransactionController`**

Replace lines 35-46 of `backend/src/main/java/com/financedash/controller/TransactionController.java`:

```java
    @GetMapping
    public PageResponse<TransactionResponse> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "DATE") TransactionSortBy sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidTransactionException("size must not exceed " + MAX_PAGE_SIZE);
        }
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Sort sort = Sort.by(sortDir, sortBy.field()).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Transaction> result =
                transactionService.findAll(effectiveFrom, effectiveTo, accountType, category, pageable);
        return PageResponse.from(result, TransactionResponse::from);
    }
```

Add a constant near the top of the class, alongside the field declarations:

```java
    private static final int MAX_PAGE_SIZE = 100;
```

Add these imports:

```java
import com.financedash.dto.PageResponse;
import com.financedash.dto.TransactionSortBy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
```

(`java.util.List` is no longer used by this controller after this change — remove that import.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn test -Dtest=TransactionControllerTest`
Expected: PASS

- [ ] **Step 5: Full backend regression check**

Run: `cd backend && mvn test`
Expected: PASS — confirms nothing else (e.g. `BalanceControllerTest`, `BudgetControllerTest`) broke.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/financedash/controller/TransactionController.java \
        backend/src/test/java/com/financedash/controller/TransactionControllerTest.java
git commit -m "feat(backend): add page/size/sortBy/sortDir to GET /api/transactions"
```

---

### Task 7: Backend docs

**Files:**
- Modify: `docs/API.md:13-24`
- Modify: `docs/SYSTEM_DESIGN.md:260-263`

**Interfaces:** None — documentation only, no code dependency.

- [ ] **Step 1: Update `docs/API.md`**

Replace lines 13-24 of `docs/API.md`:

```markdown
### `GET /api/transactions`

Query params (all optional):

| Param         | Type                 | Default                    |
|---------------|----------------------|-----------------------------|
| `from`        | ISO date             | `1970-01-01`                |
| `to`          | ISO date             | today                        |
| `accountType` | `CHECKING\|SAVINGS\|INVESTING` | (none — all accounts) |
| `category`    | one of `Category`    | (none — all categories)     |
| `page`        | int, zero-indexed    | `0`                          |
| `size`        | int, max `100`       | `20`                         |
| `sortBy`      | `DATE\|AMOUNT`       | `DATE`                       |
| `sortDir`     | `ASC\|DESC`          | `DESC`                       |

Returns a page of matching transactions as:
```json
{
  "content": [ /* TransactionResponse[] */ ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8
}
```
Every sort appends `id DESC` as a secondary key, so page boundaries stay stable when multiple rows
share a date or amount. `size > 100` returns 400.
```

- [ ] **Step 2: Update `docs/SYSTEM_DESIGN.md`**

In the "Roadmap / deferred" section, remove `pagination,` from the comma-separated list (it moves
from deferred to built):

```markdown
Auth (Spring Security), user-manageable accounts/categories, recurring transactions, multi-currency,
a price-history table for true historical net worth, realized-gain reporting UI, Flyway migrations,
and a dynamic-resolver gateway config. Shelved feature spec:
[future/STATEMENT_IMPORT.md](future/STATEMENT_IMPORT.md).
```

- [ ] **Step 3: Commit**

```bash
git add docs/API.md docs/SYSTEM_DESIGN.md
git commit -m "docs: document paginated GET /api/transactions"
```

---

### Task 8: Frontend types & API client

**Files:**
- Modify: `frontend/src/types/transaction.ts:83-88`
- Modify: `frontend/src/api/transactions.ts`

**Interfaces:**
- Produces: `TransactionFilters` gains `sortBy?: "DATE" | "AMOUNT"` and `sortDir?: "ASC" | "DESC"`;
  a new `PageResponse<T>` type. `transactionsApi.list(filters, page)` returns
  `Promise<PageResponse<Transaction>>`. Task 9 (`useTransactions` hook) consumes this signature.
- No automated test — no test runner exists in this frontend (see Global Constraints). Verified by
  `npm run build` at the end of this task and again at the end of Task 12.

- [ ] **Step 1: Update `types/transaction.ts`**

Replace lines 83-88 of `frontend/src/types/transaction.ts`:

```typescript
export interface TransactionFilters {
  from?: string;
  to?: string;
  accountType?: AccountType;
  category?: Category;
  sortBy?: "DATE" | "AMOUNT";
  sortDir?: "ASC" | "DESC";
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
```

- [ ] **Step 2: Update `api/transactions.ts`**

Replace the full file content:

```typescript
import { apiClient } from "./client";
import type { PageResponse, Transaction, TransactionFilters, TransactionInput } from "../types/transaction";

function buildQuery(filters: TransactionFilters, page: number): string {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.accountType) params.set("accountType", filters.accountType);
  if (filters.category) params.set("category", filters.category);
  if (filters.sortBy) params.set("sortBy", filters.sortBy);
  if (filters.sortDir) params.set("sortDir", filters.sortDir);
  params.set("page", String(page));
  return `?${params.toString()}`;
}

export const transactionsApi = {
  list: (filters: TransactionFilters = {}, page = 0) =>
    apiClient.get<PageResponse<Transaction>>(`/api/transactions${buildQuery(filters, page)}`),
  create: (input: TransactionInput) => apiClient.post<Transaction>("/api/transactions", input),
  update: (id: number, input: TransactionInput) =>
    apiClient.put<Transaction>(`/api/transactions/${id}`, input),
  remove: (id: number) => apiClient.del(`/api/transactions/${id}`),
};
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npm run build`
Expected: FAIL — `useTransactions.ts` (Task 9) still calls `transactionsApi.list(filters)` expecting
a `Transaction[]` back and assigns it straight into `Transaction[]` state; that's now a type error
since `list` returns `PageResponse<Transaction>`. This is expected at this point in the plan — it's
fixed in Task 9.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/transaction.ts frontend/src/api/transactions.ts
git commit -m "feat(frontend): add page/sort fields and PageResponse type"
```

---

### Task 9: `useTransactions` hook — page state

**Files:**
- Modify: `frontend/src/hooks/useTransactions.ts`

**Interfaces:**
- Consumes: `transactionsApi.list(filters, page)` from Task 8.
- Produces: hook return value gains `page: number`, `setPage: (page: number) => void`, and
  `totalPages: number`. Task 12 (`TransactionsPage`) wires these into the new `Pagination` component.

- [ ] **Step 1: Replace the hook**

Replace the full content of `frontend/src/hooks/useTransactions.ts`:

```typescript
import { useCallback, useEffect, useState } from "react";
import { transactionsApi } from "../api/transactions";
import type { Transaction, TransactionFilters, TransactionInput } from "../types/transaction";

export function useTransactions(filters: TransactionFilters) {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await transactionsApi.list(filters, page);
      setTransactions(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load transactions");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.from, filters.to, filters.accountType, filters.category, filters.sortBy, filters.sortDir, page]);

  useEffect(() => {
    reload();
  }, [reload]);

  // Any filter or sort change goes back to page 0 — a stale page number from a
  // previous, differently-filtered result set wouldn't make sense to keep.
  useEffect(() => {
    setPage(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.from, filters.to, filters.accountType, filters.category, filters.sortBy, filters.sortDir]);

  const create = useCallback(
    async (input: TransactionInput) => {
      await transactionsApi.create(input);
      await reload();
    },
    [reload],
  );

  const update = useCallback(
    async (id: number, input: TransactionInput) => {
      await transactionsApi.update(id, input);
      await reload();
    },
    [reload],
  );

  const remove = useCallback(
    async (id: number) => {
      await transactionsApi.remove(id);
      await reload();
    },
    [reload],
  );

  return { transactions, loading, error, page, setPage, totalPages, create, update, remove, reload };
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS — the hook now correctly consumes the `PageResponse<Transaction>` shape from Task 8.
`TransactionsPage.tsx` (not yet updated — that's Task 12) destructures only
`{ transactions, loading, error, create, update, remove }` from the hook's return value, which
still typechecks fine: destructuring a subset of an object's fields is always valid even though the
hook now returns `page`/`setPage`/`totalPages` too. Any failure here is a real regression — stop
and fix before moving on.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useTransactions.ts
git commit -m "feat(frontend): add page state to useTransactions"
```

---

### Task 10: `Pagination` component

**Files:**
- Create: `frontend/src/components/common/Pagination.tsx`
- Modify: `frontend/src/styles/global.css` (append after the `.filters-bar select` rule, ~line 462)

**Interfaces:**
- Produces: `<Pagination page={number} totalPages={number} onPageChange={(page: number) => void} />`
  — zero-indexed `page`, renders nothing when `totalPages <= 1`. Task 12 (`TransactionsPage`) wires
  this to the hook's `page`/`setPage`/`totalPages`.

- [ ] **Step 1: Write the component**

```typescript
interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <div className="pagination">
      <button
        type="button"
        className="pagination__nav"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        Prev
      </button>
      {Array.from({ length: totalPages }, (_, i) => i).map((n) => (
        <button
          key={n}
          type="button"
          className={`pagination__page${n === page ? " pagination__page--active" : ""}`}
          onClick={() => onPageChange(n)}
        >
          {n + 1}
        </button>
      ))}
      <button
        type="button"
        className="pagination__nav"
        disabled={page === totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </div>
  );
}
```

- [ ] **Step 2: Add CSS**

Append to `frontend/src/styles/global.css` after the `.filters-bar select { ... }` rule:

```css
.pagination {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
  margin-top: var(--space-md);
}

.pagination__nav {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 6px 10px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 14px;
  cursor: pointer;
}

.pagination__nav:disabled {
  opacity: 0.5;
  cursor: default;
}

.pagination__page {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 6px 10px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 14px;
  cursor: pointer;
}

.pagination__page--active {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS — this component isn't imported anywhere yet, so it can't break anything; this step
just confirms the new file itself is valid TypeScript/JSX.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/common/Pagination.tsx frontend/src/styles/global.css
git commit -m "feat(frontend): add Pagination component"
```

---

### Task 11: `TransactionFilters` — sort dropdown + direction toggle

**Files:**
- Modify: `frontend/src/components/transactions/TransactionFilters.tsx`

**Interfaces:**
- Produces: `TransactionFilterValues` gains `sortBy?: "DATE" | "AMOUNT"` and `sortDir?: "ASC" |
  "DESC"`. Task 12 (`TransactionsPage`) already passes its filter state straight through to this
  component and to `useTransactions`, so no other call site changes.

- [ ] **Step 1: Replace the component**

Replace the full content of `frontend/src/components/transactions/TransactionFilters.tsx`:

```typescript
import { TRANSACTION_ACCOUNT_TYPES, CATEGORIES, type AccountType, type Category } from "../../types/transaction";
import { formatEnumLabel } from "../../lib/format";

export interface TransactionFilterValues {
  accountType?: AccountType;
  category?: Category;
  sortBy?: "DATE" | "AMOUNT";
  sortDir?: "ASC" | "DESC";
}

interface TransactionFiltersProps {
  value: TransactionFilterValues;
  onChange: (value: TransactionFilterValues) => void;
}

export function TransactionFilters({ value, onChange }: TransactionFiltersProps) {
  return (
    <div className="filters-bar">
      <select
        value={value.accountType ?? ""}
        onChange={(e) =>
          onChange({ ...value, accountType: (e.target.value || undefined) as AccountType | undefined })
        }
      >
        <option value="">All accounts</option>
        {TRANSACTION_ACCOUNT_TYPES.map((type) => (
          <option key={type} value={type}>
            {formatEnumLabel(type)}
          </option>
        ))}
      </select>
      <select
        value={value.category ?? ""}
        onChange={(e) => onChange({ ...value, category: (e.target.value || undefined) as Category | undefined })}
      >
        <option value="">All categories</option>
        {CATEGORIES.map((category) => (
          <option key={category} value={category}>
            {formatEnumLabel(category)}
          </option>
        ))}
      </select>
      <select
        value={value.sortBy ?? "DATE"}
        onChange={(e) => onChange({ ...value, sortBy: e.target.value as "DATE" | "AMOUNT" })}
      >
        <option value="DATE">Sort by date</option>
        <option value="AMOUNT">Sort by amount</option>
      </select>
      <select
        value={value.sortDir ?? "DESC"}
        onChange={(e) => onChange({ ...value, sortDir: e.target.value as "ASC" | "DESC" })}
      >
        <option value="DESC">Descending</option>
        <option value="ASC">Ascending</option>
      </select>
    </div>
  );
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/transactions/TransactionFilters.tsx
git commit -m "feat(frontend): add sort controls to TransactionFilters"
```

---

### Task 12: Wire pagination into `TransactionsPage` — final integration

**Files:**
- Modify: `frontend/src/pages/TransactionsPage.tsx:10-13,32-44`

**Interfaces:**
- Consumes: `useTransactions`'s `page`/`setPage`/`totalPages` (Task 9), `Pagination` (Task 10).
- Produces: the fully wired feature — this is the last task, verified end-to-end.

- [ ] **Step 1: Update `TransactionsPage.tsx`**

Add the import:

```typescript
import { Pagination } from "../components/common/Pagination";
```

Update the destructuring at line 12:

```typescript
  const { transactions, loading, error, page, setPage, totalPages, create, update, remove } =
    useTransactions(filters);
```

Add the `Pagination` component after the `TransactionTable` in the `.card` block:

```typescript
        {error && <div className="error-banner">{error}</div>}
        {loading ? <p>Loading…</p> : (
          <>
            <TransactionTable
              transactions={transactions}
              onEdit={setEditingTransaction}
              onDelete={setDeletingTransaction}
            />
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          </>
        )}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS

- [ ] **Step 3: Manual smoke test**

Run: `cd backend && mvn spring-boot:run` (needs Postgres — `docker compose up postgres` in another
terminal) and `cd frontend && npm run dev` in a third terminal. In the browser at the Vite dev
server URL:
1. Open the Transactions page — confirm the table loads and page controls appear once there are
   more than 20 transactions (seed test data via the "Add Transaction" form if needed, or lower
   `size` temporarily via the URL to `?size=2` while testing).
2. Click a page number / Prev / Next — confirm the table updates and the active page is highlighted.
3. Change the account/category filter — confirm the page resets to 1.
4. Change "Sort by" to Amount and toggle Ascending/Descending — confirm row order changes
   accordingly.

- [ ] **Step 4: Full-repo verification**

Run: `cd backend && mvn verify` (needs Docker) and `cd frontend && npm run build`
Expected: both PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/TransactionsPage.tsx
git commit -m "feat(frontend): wire pagination into TransactionsPage"
```

---

## Post-implementation

Once all tasks are done and verified, consider invoking `superpowers:finishing-a-development-branch`
to decide how this work integrates (merge, PR, etc.) — this plan doesn't cover that decision.
