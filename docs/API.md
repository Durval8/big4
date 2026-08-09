# API Reference

Everything is behind the **gateway** at `http://localhost:8090` (single origin). The gateway routes
`/api/investments/**` to the investments service and `/api/**` to the backend — see
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md#request-routing). All bodies are JSON. Backend and service share
the same error shape (see [Errors](#errors)).

- **Transactions / Balances / Budgets** → backend
- **Investments (holdings, summary, news)** → investments service

## Transactions

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

### `GET /api/transactions/{id}`
Returns a single `TransactionResponse`, or 404.

### `POST /api/transactions` / `PUT /api/transactions/{id}`

Body (`TransactionRequest`):
```json
{
  "description": "Move to savings",
  "amount": 300.00,
  "transactionDate": "2026-07-10",
  "accountType": "CHECKING",
  "linkedAccountType": "SAVINGS",
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

### System-generated rows are read-only

`TransactionResponse` carries **`sourceEventId`** — null for anything a user created, and the
investments-service cash-leg `eventId` for rows the backend generated from a buy or cash-out:

```json
{ "id": 42, "description": "Bought AAPL", "amount": 500.00, "transactionDate": "2026-08-02",
  "accountType": "CHECKING", "linkedAccountType": "INVESTING", "category": null,
  "transactionType": "TRANSFER", "sourceEventId": "3f2a…", "createdAt": "…", "updatedAt": "…" }
```

These are the only rows where `INVESTING` appears — the consumer writes them through the repository,
so the validation above (which rejects `INVESTING`) still applies to every user-facing path.
`PUT` and `DELETE` on such a row return **400**: the investments service still holds the
corresponding position, so editing the ledger entry would desync cash from holdings. Change it on
the Investments API instead. See
[Data Model](DATA_MODEL.md#investing-cash-legs-in-the-ledger).

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
[{ "id": 1, "name": "Food", "value": 400.00, "periodValue": 394.22,
   "categories": ["GROCERIES", "DINING_OUT"],
   "spent": 150.00, "remaining": 244.22, "from": "2026-07-04", "to": "2026-08-02" }]
```
`value` is always the raw monthly target (also what `GET /api/budgets` and the
edit form use — never scaled). `periodValue` prorates `value` to the length of
`[from, to]`: `daysInPeriod / 30.44` (a nominal average days/month), rounded to
2dp. `spent` = Σ EXPENSE amounts in the budget's categories over `[from, to]`;
`remaining` = `periodValue − spent` (negative when over budget).

Worked example above: a 30-day window → `factor = 30 / 30.44 ≈ 0.9855` →
`periodValue = 400.00 × 0.9855 ≈ 394.22`; `remaining = 394.22 − 150.00 = 244.22`.

For `range=ALL`, the window otherwise starts at `1970-01-01` (a degenerate
~56-year span to prorate against); `periodValue` instead scales from the
system's earliest transaction date, falling back to a single day if there are
no transactions yet. See [Data Model](DATA_MODEL.md#entity-budget) for the full
formula and rationale.

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

## Analytics

### `GET /api/analytics`

Dashboard spending visualizations — see
[the design spec](superpowers/specs/2026-08-02-transaction-analytics-design.md) for full rationale.
Takes the same `range` / `from` / `to` params as `GET /api/balances`, **but resolves the window
differently**:

| Param | Type | Notes |
|---|---|---|
| `range` | `WEEK\|MONTH\|YEAR\|ALL` | `ALL` collapses to the one-year cap below, not `1970-01-01` |
| `from` | ISO date | naming an explicit `from` escapes the earliest-transaction floor (not the cap) |
| `to` | ISO date | defaults to today |

Two adjustments apply, in order, to whatever window `range`/`from`/`to` resolve to:
1. **One-year cap.** A window longer than a year is clamped to `[to − 1 year + 1 day, to]`.
2. **Earliest-transaction floor**, for **any named `range`** (not just `ALL`) — if the system's
   first transaction is more recent than the capped `from`, the window starts there instead. An
   **explicit `from`** escapes this (a caller naming specific dates gets those dates, capped, not a
   second-guessed window).

Returns an `AnalyticsResponse`:
```json
{
  "from": "2026-07-04",
  "to": "2026-08-02",
  "previousFrom": "2026-06-04",
  "previousTo": "2026-07-03",
  "bucketUnit": "DAY",
  "totalIncome": 3000.00,
  "totalExpense": 1284.55,
  "categories": [
    { "category": "GROCERIES", "amount": 412.30, "previousAmount": 380.00 }
  ],
  "incomeCategories": [
    { "category": "SALARY", "amount": 3000.00, "previousAmount": 3000.00 }
  ],
  "buckets": [
    { "start": "2026-07-04", "income": 0.00, "expense": 42.10 }
  ]
}
```
- `from`/`to` echo the **final** window, after both adjustments — never the pre-adjustment value.
- `previousFrom`/`previousTo` are the immediately preceding window of equal length, or **both null**
  when nothing precedes the window (i.e. `from` is on or before the earliest transaction date).
- `bucketUnit` is derived from the final window length, never passed by the client: `DAY` (≤31
  days), `WEEK` (≤26 weeks), `MONTH` (otherwise — the window can't exceed a year, so nothing coarser
  is needed).
- `categories` is **EXPENSE only** (`TRANSFER`/`ADJUSTMENT` carry no category and are excluded from
  every field in this response), sorted descending by `amount`. A category with no spend in either
  period is omitted; one present in the prior period but not this one still appears with
  `amount: 0` so a movers view can show the drop. `previousAmount` is null on every row when there's
  no prior period at all.
- `incomeCategories` is the **INCOME** counterpart, same shape and same sort. Kept as a *separate*
  list rather than folded into `categories` with a type discriminator: every consumer of
  `categories` means spending by it, so widening that list would make salary rows render as
  expenses in the spending breakdown and the movers view. Only the cash-flow diagram reads both,
  and it puts them on opposite sides.
- `buckets` is **gap-filled** — every bucket in the window appears, including zero-activity ones.

**Reconciles against `netSpending`, not `spending`, on `GET /api/balances`** — `spending` also
folds in transfers into savings, which carry no category and can't appear in a category breakdown.
`totalExpense` here always equals `netSpending` for the same window. See
[Data Model](DATA_MODEL.md#entity-transaction) for the full semantics.

**This diverges from `GET /api/budgets/progress`**, which takes the identical params: there,
`ALL`'s queried window is unchanged (still `1970-01-01`) and only the *proration anchor* moves.
Here the window itself is capped and re-anchored, so the two endpoints' `ALL` behavior differs.

## Investments

Served by the **investments service** (MongoDB). Holdings are **share-based** and priced from
Finnhub; buys/cash-outs reach the backend as RabbitMQ messages that fold into cash balances (see
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md), [INVESTMENT_PRICING.md](INVESTMENT_PRICING.md)). IDs are Mongo
strings.

### `GET /api/investments`
Open holdings only (symbol-ordered) — a cashed-out holding is kept as history but excluded from
this list, as `HoldingResponse[]`:
```json
[{ "id": "6a64…", "stockSymbol": "AAPL", "quantity": 0.300282, "avgCost": 333.0203,
   "latestPrice": 333.0200, "currentValue": 100.00, "netCashInvested": 100.00,
   "realizedGain": 0.00, "positionChangePct": 0.00, "priceStatus": "OK",
   "priceAsOf": "…", "status": "OPEN", "createdAt": "…", "updatedAt": "…" }]
```
`currentValue` = `quantity × latestPrice`; `positionChangePct` = `(latestPrice − avgCost)/avgCost ×
100` (null when there are no shares or no price). `priceStatus` is `OK` / `STALE` (provider failing,
last price kept) / `UNRESOLVED` (symbol not recognized — priced by hand, never auto-fetched).

### `GET /api/investments/summary`
`{ "totalNetInvested", "totalCurrentValue", "totalRealizedGain", "positionChangePct" }`.
`totalNetInvested`/`totalCurrentValue`/`positionChangePct` are OPEN-holdings-only (money still in
the market). `totalRealizedGain` is summed across **every** holding regardless of status — a fully
cashed-out holding drops out of the OPEN-only totals but must keep counting here, since realized
gain is money already made on shares already sold.

### `GET /api/investments/{id}`
Single `HoldingResponse`, or 404.

### `POST /api/investments`
Buy by money amount. Body: `{ "stockSymbol", "amount", "sourceAccount", "manualPrice"? }` —
`shares = amount ÷ price`; `amount` debits `sourceAccount` (CHECKING/SAVINGS); buying an existing
symbol merges (weighted average cost). `manualPrice` is used **only** for a symbol the provider
doesn't recognize. Returns 201. **503** if the provider is unavailable (the buy is not recorded);
400 for a bad account / blank symbol / unrecognized symbol with no `manualPrice`.

### `PUT /api/investments/{id}`
Correction only: `{ "stockSymbol", "quantity" }` — fix the symbol and/or share count (prices are the
source of truth, so there's no value edit). 400 if cashed out; 404 if missing.

### `POST /api/investments/{id}/cash-out`
Body: `{ "percentage" }` — percentage of the *current* position to sell, `(0, 100]`. Proceeds
(`percentage% × live currentValue`, computed at request time, not client-supplied) move to SAVINGS
and realized gain is recorded; `percentage=100` always closes the holding to `CASHED_OUT` (kept as
history) regardless of any price movement since the request was composed — quantity, not a money
amount, drives the full/partial decision, so there's nothing for a price refresh to race against.
400 if `percentage` is outside `(0, 100]` or the holding is already cashed out.

### `POST /api/investments/{id}/price`
Body: `{ "price" }` — set a manual price for an `UNRESOLVED` holding (the only way it gets valued).
400 if the holding is resolved (it's priced automatically) or cashed out.

### `DELETE /api/investments/{id}`
Admin-only: removes a holding and re-broadcasts the investing value. Does **not** refund cash (use
cash-out for that). Returns 204, or 404. Not exposed in the UI.

### `GET /api/investments/news`
The portfolio news feed: `{ "updatedAt", "items": [{ "symbol", "headline", "summary", "url",
"source", "publishedAt" }] }` (≤ 7 items). See [INVESTMENT_NEWS.md](INVESTMENT_NEWS.md).

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

Three transactions on `CHECKING`, plus one investment buy:
1. `ADJUSTMENT` $1,000.00 on 2026-01-01 (opening balance)
2. `INCOME` $2,000.00 / `SALARY` on 2026-07-01 (paycheck)
3. `EXPENSE` $175.50 / `GROCERIES` on 2026-07-05
4. `POST /api/investments` — buy $500.00 of a stock from `CHECKING` on 2026-07-24 (recorded by the
   investments service; the backend receives a **FUND cash-leg** of $500 and a **value snapshot** of
   $500).

`GET /api/balances?range=MONTH` (today = 2026-07-24) returns exactly the `BalanceSummaryResponse`
shown above: `netWorth` = $1000 + $2000 − $175.50 − $500 (fund) + $500 (holding value) = **$2824.50**,
split **$2324.50 checking / $500 investing**; `spending`/`netSpending` = **$175.50** (the expense
only — a buy is not spending); `netInvestment` = **$500.00** (net cash into investments over the
period). Net worth is unchanged by the buy itself — cash simply became a holding.
