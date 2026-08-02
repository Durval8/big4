# Data Model & Metrics

This is the authoritative reference for the domain model and the balance
calculations. It supersedes the original planning doc — a few rules were
tightened during implementation (noted inline as **Refinement**).

Backend entities: `Transaction` (below) and `Budget` ([Entity: Budget](#entity-budget)).
**Investing data lives in the investments service** (MongoDB) now — see
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) and [INVESTMENTS_SERVICE.md](INVESTMENTS_SERVICE.md). The backend
keeps two **message-fed projections** of it, used only for the dashboard:
`investment_cash_flow` (buys/cash-outs, folded into cash balances and `netInvestment`) and
`investment_valuation` (a singleton shallow copy of the current investing value, last-write-wins).
Both are populated by RabbitMQ consumers; the backend never queries the service.

## Entity: `Transaction`

There is no `Account` table — "account" is a `type` field on the transaction
itself (see [Scope](#scope-decisions)). **Transactions are `CHECKING`/`SAVINGS`
only** — `INVESTING` is no longer a valid transaction account (rejected by
`TransactionService`); investing is the `Investment` entity, and the `INVESTING`
balance reflects holdings.

| Field               | Type          | Notes                                                            |
|---------------------|---------------|-------------------------------------------------------------------|
| `id`                | `Long`        | generated                                                        |
| `description`       | `String`      | required                                                         |
| `amount`            | `BigDecimal`  | required, `> 0` (always positive — direction comes from `transactionType`) |
| `transactionDate`   | `LocalDate`   | required                                                         |
| `accountType`       | `AccountType` | required                                                         |
| `linkedAccountType` | `AccountType` | required **iff** `transactionType = TRANSFER`, forbidden otherwise |
| `category`          | `Category`    | required **iff** `transactionType` is `INCOME`/`EXPENSE`, forbidden otherwise |
| `transactionType`   | `TransactionType` | required                                                     |
| `createdAt`/`updatedAt` | `Instant` | auto-managed (JPA auditing)                                     |

These cross-field rules are enforced in `TransactionService.validate()`
(`backend/src/main/java/com/financedash/service/TransactionService.java`), not
by Bean Validation annotations, since they depend on `transactionType`. A
violation returns **400** via `InvalidTransactionException`.

## Enums

- **`AccountType`**: `CHECKING`, `SAVINGS`, `INVESTING` — which bucket the money sits in.
- **`TransactionType`**: what happened to the money:
  - `INCOME` — enters `accountType` from outside the system (salary → CHECKING).
  - `EXPENSE` — leaves `accountType` to outside the system (groceries ← CHECKING).
  - `TRANSFER` — moves from `accountType` (source) to `linkedAccountType` (destination); both are the user's own buckets.
  - `ADJUSTMENT` — direct balance correction / **opening-balance seed**. Counts toward net worth only; excluded from every flow metric below. Without this, net worth reads as ~0 until months of transaction history accumulate — seed each account with one `ADJUSTMENT` transaction dated at your tracking start date.
- **`Category`**: `GROCERIES, TRANSPORTATION, DINING_OUT, UTILITIES, HOUSING, HEALTHCARE, ENTERTAINMENT, SHOPPING, TRAVEL, SUBSCRIPTIONS, INSURANCE, SALARY, FREELANCE_INCOME, INVESTMENT_INCOME, GIFTS, OTHER_INCOME, OTHER_EXPENSE`. Fixed list, not user-manageable (see [Scope](#scope-decisions)). Describes *what* an INCOME/EXPENSE was for — it never encodes "this was a savings/investing contribution"; that's already expressed by `TRANSFER` + `linkedAccountType`.

## Balance formula

Cash-account (`CHECKING`/`SAVINGS`) running balance as of date `T`, folding in
investing cash flows from the `investment_cash_flow` projection (`FUND` debits its source account;
`CASH_OUT` credits SAVINGS — fed by cash-leg messages from the investments service):

```
balance(account, T) = Σ INCOME.amount        (accountType = account, date ≤ T)
                     + Σ ADJUSTMENT.amount    (accountType = account, date ≤ T)
                     − Σ EXPENSE.amount       (accountType = account, date ≤ T)
                     − Σ TRANSFER.amount      (accountType = account, date ≤ T)        [outgoing]
                     + Σ TRANSFER.amount      (linkedAccountType = account, date ≤ T)  [incoming]
                     − Σ FUND cash-flow.amount     (source account = account, flowDate ≤ T)
                     + Σ CASH_OUT cash-flow.amount (account = SAVINGS, flowDate ≤ T)

balance(INVESTING) = investment_valuation.netValue   (latest snapshot; ZERO before the first)
```

Implemented in `BalanceService.cashBalance()`; the INVESTING balance is read from the
`investment_valuation` singleton (a shallow copy kept current by value-snapshot messages — always
"now," no valuation history). This is a *stale-but-coherent* number: both the cash flows and the
value ride the same message stream, so net worth is never internally contradictory.

## Dashboard metrics

`netWorth` is a **stock** (as-of the end of the selected period, or "now" if
no period is given); the other three are **flows** (summed over the period).

```
netWorth(T)           = balance(CHECKING, T) + balance(SAVINGS, T) + balance(INVESTING, T)

spending(period)      = Σ EXPENSE.amount                                      (in period)
                       + Σ TRANSFER.amount where linkedAccountType = SAVINGS   (in period)

netSpending(period)   = Σ EXPENSE.amount                                      (in period)

netInvestment(period) = Σ FUND cash-flow.amount     (flowDate in period)
                       − Σ CASH_OUT cash-flow.amount (flowDate in period)
```

In words: **spending** = everything that left checking for expenses *or* for
savings; **net spending** = expenses only; **net investment** = net cash moved
into investments over the period (buys minus cash-outs — from the
`investment_cash_flow` projection, not transactions). `balance(INVESTING)` is the
current holdings value regardless of the selected period (documented simplification
— no valuation history yet).

Implemented in `BalanceService.summarize()`
(`backend/src/main/java/com/financedash/service/BalanceService.java`) —
verified end-to-end against hand-computed examples during implementation
(see `docs/API.md` for the worked example).

## Entity: `Budget`

A named spending target tracked against a set of categories.

| Field         | Type            | Notes                                              |
|---------------|-----------------|-----------------------------------------------------|
| `id`          | `Long`          | generated                                          |
| `name`        | `String`        | required, non-blank                                |
| `value`       | `BigDecimal`    | required, `> 0` — the **monthly** target/limit     |
| `categories`  | `Set<Category>` | required, non-empty — stored in a `budget_categories` join table (`@ElementCollection`, **EAGER**) |
| `createdAt`/`updatedAt` | `Instant` | auto-managed (JPA auditing)                       |

**Spent computation.** A budget's `spent` for a period is:

```
spent(budget, from, to) = Σ EXPENSE.amount   where category ∈ budget.categories
                                              and from ≤ transactionDate ≤ to
```

- **Expenses only.** Only `EXPENSE` transactions count. `INCOME` in a budget's
  categories is ignored (a budget tracks spending), and `TRANSFER`/`ADJUSTMENT`
  have no category so never count.
- **Period-scoped.** `spent` is measured over the window the caller passes —
  the Dashboard uses its own time-range selector, so budgets and balances move
  together. It is not stored; it's computed on demand in
  `BudgetService.progress()`.
- Because `Category` is a flat enum, the create/edit UI lists income categories
  (e.g. `SALARY`) too. A budget built only from income categories will always
  read `spent = 0` — expected, given the expenses-only rule; there's no
  income/expense partition of the enum in this MVP.
- **Overlap allowed.** A category may appear in multiple budgets; each budget
  sums its own categories independently.

**Period proration.** `value` is always a monthly figure, but the window the
caller selects (`WEEK`/`MONTH`/`YEAR`/`ALL`, or an explicit `from`/`to`) can be
any length, so `GET /api/budgets/progress` also returns a `periodValue` —
`value` scaled to the window — and `remaining` is computed against
`periodValue`, not `value`:

```
daysInPeriod          = days in [from, to], inclusive
factor                = daysInPeriod / 30.44        (nominal average days/month)
periodValue(budget)   = value × factor              (rounded HALF_UP, 2dp)
remaining             = periodValue − spent          (negative when over budget)
```

- **`value` itself is never scaled.** `GET /api/budgets` / `GET /api/budgets/{id}`
  (`BudgetResponse`) and the create/edit form only ever see the raw monthly
  `value` — only `BudgetProgressResponse.periodValue` is prorated. This matters
  because the edit form is prefilled from the progress list; if it read
  `periodValue` instead, saving while e.g. "Last year" was selected would
  silently overwrite the stored monthly target with a scaled number.
- **`TimeRange.ALL` is special-cased.** Its window otherwise resolves `from` to
  `1970-01-01` — a degenerate ~56-year span that would make `factor` enormous.
  Instead, for `ALL` the scaling window starts at the date of the **earliest
  transaction in the system** (not the budget's own `createdAt`), reflecting
  how much financial history actually exists. If there are no transactions at
  all yet, it falls back to a single-day window ending at `to`.
- See `docs/API.md#get-apibudgetsprogress` for a worked example.

Categories are fetched **EAGER** deliberately: services aren't `@Transactional`
and `open-in-view` is off, so a lazy collection would fail to load once the
session closes (during response mapping and progress computation).

## Scope decisions

Decided before implementation and unchanged:

- **Single user, no auth.** No `User` entity; every transaction implicitly belongs to "the" user.
- **Fixed enums, not manageable entities.** `AccountType` and `Category` are enums baked into the code — there's no "add a new account" or "add a new category" CRUD. The original spec's "account" (checking/savings/investing) and "category" examples map directly onto these enums rather than separate tables.
- **Explicit `transactionType`, not signed amounts.** `amount` is always positive; direction and meaning come from `transactionType` + `accountType`/`linkedAccountType`.
- **Budgets are user-managed** (unlike accounts/categories): full create/edit/delete CRUD. A budget references categories by the fixed `Category` enum.

## Refinements made during implementation

- **`ADJUSTMENT` type added.** Not in the original three metrics' formulas — introduced specifically to make net worth meaningful from day one (opening balances) without polluting the flow metrics.
- **Category is enforced, not just documented.** `TransactionService` rejects (400) a category on a TRANSFER/ADJUSTMENT and rejects a missing category on INCOME/EXPENSE, so the two mechanisms (category vs. transfer) can't be muddled through the API.
- **Default date window when unspecified.** `GET /api/transactions` without `from`/`to` returns everything from `1970-01-01` through today, rather than erroring — see `docs/API.md`.
