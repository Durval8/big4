# API Reference

Base URL: `http://localhost:8080/api` (or whatever `BACKEND_PORT` is set to).
All bodies are JSON. Errors follow a consistent shape (see [Errors](#errors)).

## Transactions

### `GET /api/transactions`

Query params (all optional):

| Param         | Type                 | Default                    |
|---------------|----------------------|-----------------------------|
| `from`        | ISO date             | `1970-01-01`                |
| `to`          | ISO date             | today                        |
| `accountType` | `CHECKING\|SAVINGS\|INVESTING` | (none — all accounts) |
| `category`    | one of `Category`    | (none — all categories)     |

Returns transactions in the range, newest first, as `TransactionResponse[]`.

### `GET /api/transactions/{id}`
Returns a single `TransactionResponse`, or 404.

### `POST /api/transactions` / `PUT /api/transactions/{id}`

Body (`TransactionRequest`):
```json
{
  "description": "Brokerage contribution",
  "amount": 300.00,
  "transactionDate": "2026-07-10",
  "accountType": "CHECKING",
  "linkedAccountType": "INVESTING",
  "category": null,
  "transactionType": "TRANSFER"
}
```
Validation (400 on violation — see [Data Model](DATA_MODEL.md)):
- `linkedAccountType` required and `!= accountType` iff `transactionType = TRANSFER`; forbidden otherwise.
- `category` required iff `transactionType` is `INCOME`/`EXPENSE`; forbidden otherwise.
- `amount > 0`.
- `accountType` and `linkedAccountType` must be `CHECKING`/`SAVINGS` — **`INVESTING` is rejected** (investing is the Investments API, below).

`POST` returns 201 with the created `TransactionResponse`. `PUT` returns 200
with the updated resource, or 404 if `id` doesn't exist.

### `DELETE /api/transactions/{id}`
Returns 204, or 404 if `id` doesn't exist.

## Balances

### `GET /api/balances`

Query params — pass **either** `range` **or** `from`/`to` (`range` wins if both are given; defaults to `MONTH` if neither is given):

| Param   | Type                              |
|---------|-----------------------------------|
| `range` | `WEEK\|MONTH\|YEAR\|ALL` (resolved against today) |
| `from`  | ISO date                          |
| `to`    | ISO date (defaults to today)      |

Returns a `BalanceSummaryResponse`:
```json
{
  "from": "2026-06-25",
  "to": "2026-07-24",
  "netWorth": 2824.50,
  "spending": 175.50,
  "netSpending": 175.50,
  "netInvestment": 500.00,
  "accountBalances": { "checking": 2324.50, "savings": 0, "investing": 500.00 }
}
```
`netWorth`/`accountBalances` are as-of `to`; the other three fields are summed
over `[from, to]` — see [Data Model](DATA_MODEL.md) for the exact formulas.

## Budgets

### `GET /api/budgets`
Returns all budgets (name-ordered) as `BudgetResponse[]`:
```json
[{ "id": 1, "name": "Food", "value": 400.00, "categories": ["GROCERIES", "DINING_OUT"],
   "createdAt": "…", "updatedAt": "…" }]
```

### `GET /api/budgets/progress`
Takes the **same** `range` / `from` / `to` params as `GET /api/balances`
(defaults to the last month). Returns each budget with its spend for the
period, as `BudgetProgressResponse[]`:
```json
[{ "id": 1, "name": "Food", "value": 400.00, "categories": ["GROCERIES", "DINING_OUT"],
   "spent": 150.00, "remaining": 250.00, "from": "2026-06-25", "to": "2026-07-24" }]
```
`spent` = Σ EXPENSE amounts in the budget's categories over `[from, to]`;
`remaining` = `value − spent` (negative when over budget). See
[Data Model](DATA_MODEL.md#entity-budget).

### `GET /api/budgets/{id}`
Single `BudgetResponse`, or 404.

### `POST /api/budgets` / `PUT /api/budgets/{id}`
Body (`BudgetRequest`):
```json
{ "name": "Food", "value": 400.00, "categories": ["GROCERIES", "DINING_OUT"] }
```
Validation (400): `name` non-blank, `value > 0`, `categories` non-empty (and
each a valid `Category`). `POST` → 201; `PUT` → 200 or 404.

### `DELETE /api/budgets/{id}`
Returns 204, or 404.

## Investments

Stock holdings, separate from the transaction ledger. Buys/cash-outs are recorded
as investment events that fold into cash balances (see [docs/INVESTMENTS.md](INVESTMENTS.md)).

### `GET /api/investments`
All holdings (symbol-ordered), as `InvestmentResponse[]`:
```json
[{ "id": 1, "stockSymbol": "AAPL", "currentValue": 350.00, "netCashInvested": 150.00,
   "positionChangePct": 133.33, "status": "OPEN", "createdAt": "…", "updatedAt": "…" }]
```
`positionChangePct` = (currentValue − netCashInvested) / netCashInvested × 100, or
`null` when netCashInvested ≤ 0.

### `GET /api/investments/summary`
Totals across OPEN holdings: `{ "totalNetInvested", "totalCurrentValue", "positionChangePct" }`.

### `GET /api/investments/{id}`
Single `InvestmentResponse`, or 404.

### `POST /api/investments`
Add a holding (a buy). Body: `{ "stockSymbol", "amount", "sourceAccount" }` —
`amount` debits `sourceAccount` (CHECKING/SAVINGS); adding an existing symbol merges.
400 if `sourceAccount` is INVESTING, blank symbol, or `amount ≤ 0`. Returns 201.

### `PUT /api/investments/{id}`
Edit: `{ "stockSymbol", "currentValue" }` — rename and/or mark-to-market (no cash moves).
400 if the holding is cashed out; 404 if missing.

### `POST /api/investments/{id}/cash-out`
Body: `{ "amount" }` (≤ current position) — moves the amount to SAVINGS, reduces the
position; a fully cashed-out holding becomes `CASHED_OUT` (kept as history). 400 if the
amount exceeds the position or the holding is already cashed out.

### `DELETE /api/investments/{id}`
Removes a holding and its events. Returns 204, or 404.

## Errors

Every non-2xx response is an `ErrorResponse`:
```json
{
  "timestamp": "2026-07-24T10:42:23Z",
  "status": 400,
  "error": "Bad Request",
  "messages": ["linkedAccountType is required for TRANSFER transactions"]
}
```
`404` for a missing resource, `400` for validation failures. All three failure
sources produce this same shape:
- Bean Validation field errors (blank description, non-positive amount, missing required field)
- the service cross-field rules (`InvalidTransactionException`)
- unreadable bodies — malformed JSON or an invalid enum value such as an unknown `transactionType`/`accountType`/`category` (`HttpMessageNotReadableException`), which fail during deserialization before validation runs

## Worked example (verified during implementation)

Four transactions, all on `CHECKING` unless noted:
1. `ADJUSTMENT` $1,000.00 on 2026-01-01 (opening balance)
2. `INCOME` $2,000.00 / `SALARY` on 2026-07-01 (paycheck)
3. `EXPENSE` $175.50 / `GROCERIES` on 2026-07-05
4. `TRANSFER` $500.00 → `INVESTING` on 2026-07-24

`GET /api/balances?range=MONTH` (today = 2026-07-24) returns exactly the
`BalanceSummaryResponse` shown above: `netWorth` includes all four
transactions ($1000 + $2000 − $175.50 − $500 + $500 = $2824.50, split
$2324.50 checking / $500 investing); `spending`/`netSpending` = $175.50
(the expense only, transaction #4 isn't a savings transfer); `netInvestment`
= $500.00 (the transfer into investing).
