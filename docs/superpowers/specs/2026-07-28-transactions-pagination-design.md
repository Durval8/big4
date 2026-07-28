# Transactions list pagination, sorting & filtering — design

**Status: Approved design, not yet implemented.** Brainstormed and agreed 2026-07-28. Ready to go
through `writing-plans` whenever implementation is picked up — no further design confirmation
needed unless the codebase has drifted since this date.

## Why

`GET /api/transactions` (`TransactionController.findAll`) returns the entire matching result set as
a single `List<TransactionResponse>`, with no `page`/`size` and no server-side sort control beyond
the fixed `transactionDate DESC, id DESC` baked into the repository queries. This was flagged as
deferred work in `docs/SYSTEM_DESIGN.md`'s Roadmap — fine for a low-volume single-user MVP, but it
doesn't scale as transaction history grows, and there's no way to sort by amount or otherwise view
the list beyond newest-first.

## Scope

Transactions only. Budgets, holdings, and the investments-service news feed were considered and
explicitly excluded — each is naturally bounded (a handful of budget categories, a handful of held
stocks, a curated feed size), so pagination would add machinery with no real problem behind it.
Category/accountType filtering already exists on this endpoint today (single-value); this spec
keeps that shape and adds paging + sorting alongside it.

## Decisions locked in during brainstorming

- **Pagination style**: offset/page-based (`page`, `size` query params, Spring Data
  `Pageable`/`Page<T>` internally), not cursor-based. Simple, supports jumping to an arbitrary page,
  matches a numbered-page-controls UI. The occasional skew from inserts/deletes between page loads
  is an acceptable trade-off for a personal/household app with low write concurrency.
- **Page size**: default `20`, max `100` (reject anything larger with 400). Client may request any
  size up to the max via `?size=`.
- **Default sort**: `transactionDate DESC`, tiebroken by `id DESC` — matches today's behavior when
  no sort params are given.
- **New sort control**: `sortBy` (`date` | `amount`, default `date`) + `sortDir` (`asc` | `desc`,
  default `desc`), two separate query params rather than one combined string — explicit, validates
  cleanly against a fixed enum, consistent with this endpoint's existing separate-param style.
  Whichever field is chosen, `id DESC` is *always* appended as a secondary sort key so page
  boundaries stay stable when multiple rows share a date or amount.
- **Filters unchanged**: `category` and `accountType` stay single-value optional params, exactly as
  today. Multi-select was considered and rejected — it would need a `TransactionFilters` UI redesign
  (multi-select dropdowns) that's out of scope for a pagination/sorting change.
- **Response shape**: a new `PageResponse<T>` record (see below), not Spring's `Page`/`PageImpl`
  directly — every DTO in this codebase is a `record` (see CLAUDE.md), and `PageImpl` is a class with
  serialization quirks not worth fighting.
- **Breaking change, not versioned**: the frontend is the only consumer of this endpoint and is
  updated in the same change, so there's no need for API versioning, a `v2` path, or a transition
  period — old and new shapes don't need to coexist.
- **Frontend sort UI**: a "Sort by" dropdown + direction toggle placed next to the existing
  category/account filter dropdowns in `TransactionFilters` (not clickable column headers) — keeps
  sorting visually grouped with the other query controls.
- **Frontend page UI**: numbered page controls (Prev/Next + page numbers) below the table, not
  "Load more" or infinite scroll — matches the offset/page-based backend choice directly, no extra
  scroll-position or accumulated-list state to manage.
- Changing any filter, or the sort, resets the view to page 0.

## API changes

`GET /api/transactions` query params (all optional):

| Param | Type | Default | Notes |
|---|---|---|---|
| `from`, `to` | `LocalDate` | unbounded | unchanged |
| `accountType` | `AccountType` | none | unchanged, single-value |
| `category` | `Category` | none | unchanged, single-value |
| `page` | `int` | `0` | zero-indexed |
| `size` | `int` | `20` | max `100`, else 400 |
| `sortBy` | `date` \| `amount` | `date` | invalid value → 400 |
| `sortDir` | `asc` \| `desc` | `desc` | invalid value → 400 |

Response body changes from `TransactionResponse[]` to:

```jsonc
{
  "content": [ /* TransactionResponse[] */ ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8
}
```

## Backend changes

- New `dto/PageResponse<T>` record: `content`, `page`, `size`, `totalElements`, `totalPages`, with a
  static `from(Page<X>, Function<X, T>)` mapping helper, mirroring the existing `*Response.from(...)`
  convention on other DTOs.
- `TransactionController.findAll` adds `page`, `size`, `sortBy`, `sortDir` params; validates `size`
  (≤100) and the `sortBy`/`sortDir` enums, builds a `Pageable`, and wraps the result in
  `PageResponse.from(...)`.
- `TransactionService.findAll(...)` takes a `Pageable` (already carrying the resolved `Sort`)
  alongside the existing `from`/`to`/`accountType`/`category` params, and returns `Page<Transaction>`
  instead of `List<Transaction>`.
- `sortBy` is resolved to an entity field internally (`date` → `transactionDate`, `amount` →
  `amount`) — the client never supplies a raw field name, so there's no way to sort by an unintended
  column.
- `TransactionRepository`'s existing derived-query methods (filtered by date range +
  optional `accountType`/`category`) change return type from `List<Transaction>` to
  `Page<Transaction>` — Spring Data applies the `Pageable`'s sort automatically; no method body
  changes needed.
- **No other caller is affected.** `BalanceService` and `BudgetService` query
  `TransactionRepository` directly for their sum/aggregate computations — they don't go through
  `TransactionService.findAll` — so dashboard/budget math is untouched by this change.

## Database indexes

Caught in spec review: `transactions` currently has **no index beyond the implicit one on `id`**
(`db/migration/V1__initial_schema.sql`). Every paginated request runs a `WHERE transaction_date
BETWEEN ... [AND account_type = ?] [AND category = ?] ORDER BY <date|amount>, id` query, repeatedly
— exactly the shape that needs index support once the table grows past a trivial row count, which
is the whole reason pagination is being added. Skipping this would make the new endpoint no faster
than the current one once the table's large enough to matter.

Single-column indexes, not a hand-built composite per filter/sort permutation — the latter is
over-engineering for a personal-scale table; Postgres's planner combines single-column indexes via
bitmap AND for the filtered cases:

- `idx_transactions_transaction_date` on `transaction_date` — the always-present date-range filter,
  and the default sort.
- `idx_transactions_account_type` on `account_type`
- `idx_transactions_category` on `category`
- `idx_transactions_amount` on `amount` — the new amount-sort option.

New migration `db/migration/V2__add_transaction_indexes.sql`:

```sql
CREATE INDEX IF NOT EXISTS idx_transactions_transaction_date ON transactions (transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_account_type ON transactions (account_type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions (category);
CREATE INDEX IF NOT EXISTS idx_transactions_amount ON transactions (amount);
```

## Frontend changes

- `types/transaction.ts`: `TransactionFilters` gains `sortBy`/`sortDir`; a new `PageResponse<T>`
  type mirrors the backend shape.
- `api/transactions.ts`: `transactionsApi.list` return type changes to `PageResponse<Transaction>`;
  `buildQuery` adds `page`/`size`/`sortBy`/`sortDir` to the query string when set.
- `hooks/useTransactions.ts`: adds `page`/`setPage` state (reset to `0` on any filter or sort
  change) and exposes `totalPages`/`totalElements` from the response; `reload`'s dependency array
  grows to include `page`, `sortBy`, `sortDir`.
- `components/transactions/TransactionFilters.tsx`: new "Sort by" (Date/Amount) dropdown + direction
  toggle, alongside the existing category/account filter dropdowns.
- `TransactionsPage.tsx` / `TransactionTable.tsx`: new numbered pager control (Prev/Next + page
  numbers) below the table, wired to `page`/`setPage`/`totalPages`.

## Testing

- Repository-level test: seed a multi-page fixture (spanning date and amount ties), assert
  `Page` metadata (`totalElements`, `totalPages`, `content` ordering) is correct for each
  `sortBy`/`sortDir` combination, including the `id DESC` tiebreak.
- `V2__add_transaction_indexes.sql` is exercised automatically by every Testcontainers `mvn verify`
  run (Flyway applies all migrations against the ephemeral Postgres container) — no dedicated test
  needed beyond confirming the app context still starts cleanly.
- Controller/web-slice test: `page`/`size` params wired correctly; `size > 100` → 400; invalid
  `sortBy`/`sortDir` value → 400; response shape matches `PageResponse`.
- Update existing `TransactionControllerTest`/`TransactionServiceTest` assertions for the new
  `List` → `Page` service signature and `PageResponse` controller return shape.
- Frontend: update `useTransactions` tests (if present) for the new `page` state and paged
  response shape; add a pager-interaction test (clicking next page triggers a refetch with the
  right `page` param) and a sort-dropdown test.

## Docs

- `docs/API.md`: document the five new query params and the `PageResponse` shape on
  `GET /api/transactions`.
- `docs/SYSTEM_DESIGN.md`: remove "pagination" from the Roadmap/deferred list once implemented.

## Open questions

None blocking — this is a self-contained, additive-to-existing-filters change with no external
dependencies or privacy/cost concerns (unlike the other two shelved specs in this directory).

## Revision history

- 2026-07-28: added the "Database indexes" section (V2 migration) after a spec review caught that
  the original draft never addressed index support for the new filtered/sorted query shape.
