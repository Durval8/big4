# Data Model & Metrics

This is the authoritative reference for the domain model and the balance
calculations. It supersedes the original planning doc — a few rules were
tightened during implementation (noted inline as **Refinement**).

There are two entities: `Transaction` (below) and `Budget` (see
[Entity: Budget](#entity-budget)).

## Entity: `Transaction`

There is no `Account` table — "account" is a `type` field on the transaction
itself (see [Scope](#scope-decisions)).

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

Per-account running balance as of date `T`:

```
balance(account, T) = Σ INCOME.amount        (accountType = account, date ≤ T)
                     + Σ ADJUSTMENT.amount    (accountType = account, date ≤ T)
                     − Σ EXPENSE.amount       (accountType = account, date ≤ T)
                     − Σ TRANSFER.amount      (accountType = account, date ≤ T)        [outgoing]
                     + Σ TRANSFER.amount      (linkedAccountType = account, date ≤ T)  [incoming]
```

Implemented in `BalanceService.balanceAsOf()`.

## Dashboard metrics

`netWorth` is a **stock** (as-of the end of the selected period, or "now" if
no period is given); the other three are **flows** (summed over the period).

```
netWorth(T)           = balance(CHECKING, T) + balance(SAVINGS, T) + balance(INVESTING, T)

spending(period)      = Σ EXPENSE.amount                                      (in period)
                       + Σ TRANSFER.amount where linkedAccountType = SAVINGS   (in period)

netSpending(period)   = Σ EXPENSE.amount                                      (in period)

netInvestment(period) = Σ TRANSFER.amount where linkedAccountType = INVESTING  (in period)
                       − Σ TRANSFER.amount where accountType = INVESTING       (in period)  [withdrawals]
```

In words: **spending** = everything that left checking for expenses *or* for
savings; **net spending** = expenses only; **net investment** = net flow into
investing (contributions minus withdrawals).

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
| `value`       | `BigDecimal`    | required, `> 0` — the target/limit                 |
| `categories`  | `Set<Category>` | required, non-empty — stored in a `budget_categories` join table (`@ElementCollection`, **EAGER**) |
| `createdAt`/`updatedAt` | `Instant` | auto-managed (JPA auditing)                       |

**Spent computation.** A budget's `spent` for a period is:

```
spent(budget, from, to) = Σ EXPENSE.amount   where category ∈ budget.categories
                                              and from ≤ transactionDate ≤ to
remaining = value − spent      (negative when over budget)
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
