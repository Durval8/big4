# Investments

> **Status: IMPLEMENTED** (2026-07-24). Built with the recommended answers to all
> ten questions below (net-worth investing component = always current value;
> `netInvestment` = period net cash in; show invested + current + position change;
> buy from CHECKING/SAVINGS, cash-out → SAVINGS; merge duplicate symbols; buys
> allowed with insufficient cash; `INVESTMENT_INCOME` kept; dev-volume reset for
> migration; edit = rename + mark-to-market; position-change baseline = net cash
> invested). The [open questions](#open-questions-to-confirm) are retained as the
> decision record.

## Summary

A third page (**Dashboard | Transactions | Investments**) tracks stock holdings.
Investments become their **own entity**, separate from the transaction ledger:

- Add a holding: pick a **stock** + an **amount** + a **source cash account**. The amount debits that account and becomes the holding's current position.
- Edit a holding: rename and/or **mark-to-market** the current position (no cash movement).
- Cash out (partial or full): move an amount from the holding into the **savings** account; a fully cashed-out holding is retained as history.
- Transactions are now **CHECKING / SAVINGS only** — no INVESTING transactions or transfers to/from INVESTING. The INVESTING "account" survives purely as a **reflection**: its balance = total current value of holdings.

## Locked decisions (from spec Q&A)

1. **Value model — single current amount.** Each holding has one money figure = its current value ("position"), edited to mark-to-market. No share counts, no share-level cost-basis/gain mechanics in v1. (A second figure — **net cash invested** per holding = Σ FUND − Σ CASH_OUT — is computed by the fold-in anyway and is displayed alongside; see [Frontend](#frontend).) A **position change %** vs net cash invested is derived from this — see [Position change](#position-change).
2. **Funded from a cash account.** A buy debits a chosen cash account (CHECKING or SAVINGS) by the invested amount.
3. **Recorded as investment-entity events, not transactions.** Buys/cash-outs live on the investment subsystem; balance logic folds them into cash-account balances. The Transactions page stays free of investment activity.
4. **INVESTING account kept as a reflection.** Existing INVESTING transaction data is discarded; going forward the INVESTING balance = Σ holdings' current value. Transactions can no longer use INVESTING.
5. **Cash-out supports partial.** Any amount ≤ current position moves to savings; the remainder stays invested.
6. **Cashed-out holdings kept as history** (status `CASHED_OUT`).

## Data model

### Entity: `Investment` (a holding)

| Field          | Type              | Notes                                              |
|----------------|-------------------|-----------------------------------------------------|
| `id`           | `Long`            | generated                                          |
| `stockSymbol`  | `String`          | free-text ticker/name (no live price feed in v1)   |
| `currentValue` | `BigDecimal`      | current position, ≥ 0; edited to mark-to-market    |
| `status`       | `InvestmentStatus`| `OPEN` / `CASHED_OUT`                               |
| `createdAt`/`updatedAt` | `Instant` | auto (JPA auditing)                               |
| `positionChangePct` | `BigDecimal` | **derived, not a stored column** — see [Position change](#position-change); returned on `InvestmentResponse` |

### Entity: `InvestmentEvent` (cash flow into/out of the holding)

| Field          | Type                  | Notes                                              |
|----------------|-----------------------|-----------------------------------------------------|
| `id`           | `Long`                | generated                                          |
| `investment`   | FK → `Investment`     |                                                    |
| `type`         | `InvestmentEventType` | `FUND` (buy) / `CASH_OUT`                           |
| `amount`       | `BigDecimal`          | > 0                                                |
| `accountType`  | `AccountType`         | `FUND`: the cash account debited (CHECKING/SAVINGS); `CASH_OUT`: SAVINGS (fixed) |
| `eventDate`    | `LocalDate`           | for period metrics                                 |
| `createdAt`    | `Instant`             | auto                                               |

Mark-to-market edits are **not** events (no cash moves); they change
`currentValue` directly. (A `VALUATION` event type is a possible later addition
for a value-history timeline — out of scope now.)

### Enums

- `InvestmentStatus`: `OPEN`, `CASHED_OUT`.
- `InvestmentEventType`: `FUND`, `CASH_OUT`.
- `AccountType`: **unchanged values** (`CHECKING, SAVINGS, INVESTING`), but `INVESTING` is no longer valid on a `Transaction` (enforced in `TransactionService`). It remains a balance bucket, reflecting holdings.

## Accounting & balance semantics (the crux)

Let FUND events carry `(amount, source)` and CASH_OUT events carry `amount`
(destination always SAVINGS). Balances become:

```
balance(CHECKING) = Σ CHECKING transactions            − Σ FUND where source = CHECKING
balance(SAVINGS)  = Σ SAVINGS  transactions            − Σ FUND where source = SAVINGS
                                                        + Σ CASH_OUT
balance(INVESTING) = Σ currentValue of OPEN holdings          ← the "reflection"
netWorth           = balance(CHECKING) + balance(SAVINGS) + balance(INVESTING)
```

`spending` / `netSpending` are **unchanged** (EXPENSE + transfers-to-SAVINGS;
expenses only). `netInvestment` is **redefined** as a real cash flow over the
period:

```
netInvestment(period) = Σ FUND(in period) − Σ CASH_OUT(in period)   // net new money into investments
```

**Consistency (net worth is conserved except on revaluation, which is correct):**

| Operation | Cash effect | INVESTING effect | Net worth |
|-----------|-------------|-------------------|-----------|
| Buy $1000 from CHECKING | CHECKING −1000 | +1000 | flat ✓ |
| Mark-to-market to $1200 | — | +200 | +200 (unrealized gain) ✓ |
| Partial cash-out $500 → SAVINGS | SAVINGS +500 | −500 | flat ✓ |
| Full cash-out $700 → SAVINGS | SAVINGS +700 | −700 → 0, `CASHED_OUT` | flat ✓ |

`currentValue` and the FUND/CASH_OUT amounts are independent: a cash-out reduces
`currentValue` by the cash-out amount; a mark-to-market edit moves `currentValue`
without any cash event. FUND amounts (actual cash spent) are what fold into cash
balances — they are stored for correctness/history.

### Position change

Each holding reports a **position change %** — how its current value compares to
what was put in:

```
netCashInvested   = Σ FUND.amount − Σ CASH_OUT.amount        (per holding)
positionChangePct = (currentValue − netCashInvested) / netCashInvested × 100
```

- Example: buy €100 (netCashInvested = 100), edit value to €150 → **+50%**. Edit to €80 → **−20%**.
- **Derived on read**, not stored — it's a pure function of `currentValue` and the events, so a column would only drift. Returned on `InvestmentResponse`; the frontend colors it gain/loss.
- **Guard:** when `netCashInvested ≤ 0` (e.g. fully cashed out), report `null` / "n/a" rather than dividing by zero.
- **Known v1 limitation:** with only an initial buy plus mark-to-market edits (the common case, and the example) this is exactly unrealized gain/loss. Once you **buy more** or **partially cash out**, `netCashInvested` shifts and the single % conflates realized and unrealized movement — acceptable for "for now, based on edits." A proper realized/unrealized cost-basis split is the later expansion (see [Out of scope](#out-of-scope-later-expansion)), which is also when the derivation is expected to change (hence keeping it derived, not persisted).

### Net worth time-base (must decide — see below)

Net worth is contractually **as-of the selected period's `to` date** (there's a
passing test, `netWorthUsesUpToDateLedgerWhileFlowsUseInPeriodLedger`): cash uses
`transactionDate ≤ to`, and FUND/CASH_OUT can likewise filter `eventDate ≤ to`.
But `balance(INVESTING) = Σ currentValue` is a single mark-to-market figure with
**no valuation history** — it only means "now." So selecting a past `to` would
mix as-of-then cash with **today's** holdings value.

**v1 rule (proposed):** the INVESTING contribution to net worth is **always
current value, regardless of the selected period** — a documented simplification;
cash and flow components stay as-of-`to`. Valuation history (so net worth is
truly reconstructable for a past date) is a later expansion. The alternative is
to use net cash invested (Σ FUND − Σ CASH_OUT, `eventDate ≤ to`) as a historical
proxy for the investing component. Pick one explicitly — see
[open questions](#open-questions-to-confirm).

## Operations

- **Add** (`stockSymbol`, `amount`, `sourceAccount`): create `Investment` (`currentValue = amount`, `OPEN`) + a `FUND` event. Adding an already-held symbol → **merge into the existing holding** (increment `currentValue`, add a FUND event) — see open questions.
- **Edit** (`stockSymbol`, `currentValue`): rename and/or mark-to-market. No cash movement. `currentValue ≥ 0`.
- **Cash out** (`amount ≤ currentValue`): `CASH_OUT` event (to SAVINGS) + `currentValue −= amount`; if it reaches 0 → `status = CASHED_OUT`.
- **Delete** (`DELETE /api/investments/{id}`): a hard "stop tracking this holding" — removes the holding **and its events**, which reverses their balance fold-in. It is not wired into the UI and is intended for correcting a just-added holding; **do not delete a holding that has been cashed out** (removing the CASH_OUT events would claw the realized money back out of savings).

Validation: `amount > 0`; cash-out `amount ≤ currentValue`; source account ∈ {CHECKING, SAVINGS}; `stockSymbol` non-blank. Insufficient cash on a buy is **allowed** in v1 (personal tool) — see open questions.

## Backend components

- `domain/`: `Investment`, `InvestmentEvent`, `InvestmentStatus`, `InvestmentEventType`.
- `repository/`: `InvestmentRepository`, `InvestmentEventRepository` (query events by date range for `netInvestment`, by source account for the fold-in).
- `dto/`: `InvestmentRequest` (add), `InvestmentUpdateRequest` (edit), `CashOutRequest`, `InvestmentResponse` (includes derived `netCashInvested` + `positionChangePct`), `InvestmentSummaryResponse` (total value).
- `service/`: `InvestmentService` (add/edit/cash-out + validation, emits events). `BalanceService` **changes**: fold FUND/CASH_OUT into cash balances, compute INVESTING from holdings, redefine `netInvestment`. `TransactionService` **changes**: reject INVESTING as `accountType`/`linkedAccountType`.
- `controller/`: `InvestmentController` — `GET /api/investments`, `GET /api/investments/{id}`, `POST /api/investments`, `PUT /api/investments/{id}`, `POST /api/investments/{id}/cash-out`, `DELETE /api/investments/{id}` (?), plus `GET /api/investments/summary`.

## Frontend

- Nav gains **Investments**.
- **Investments page** (not time-range scoped — holdings are as-of-now): a header with **money invested** (Σ net cash invested) and **current value** (Σ positions); a table of holdings (symbol, invested, current position, **position change %** colored gain/loss, status) with Edit / Cash out actions; and an "Add Investment" button. Showing both numbers is the user's explicit ask ("the money invested along with … their current position") and is free — the fold-in already tracks net cash invested. Note: "invested" = net cash contributed, **not** original cost basis (mark-to-market edits and partial cash-outs make them diverge).
- **Add drawer**: stock symbol, amount, source account (CHECKING/SAVINGS).
- **Edit drawer**: stock symbol, current position (mark-to-market).
- **Cash-out dialog**: amount (≤ position), confirms it moves to savings.
- **Transactions page**: remove INVESTING from the account-type and transfer-destination selectors (transactions are CHECKING/SAVINGS only).
- **Dashboard**: the "Investing" per-account balance now reflects holdings; the "Net Investment" card is redefined as net cash invested over the selected period (see open questions).

## Migration of existing INVESTING data

Existing INVESTING transaction data is **discarded** (decision 4). Purge
`transactions` where `accountType = INVESTING` or (`transactionType = TRANSFER`
and `linkedAccountType = INVESTING`). This alters historical CHECKING/SAVINGS
balances that had transfers into investing (the outflow disappears). Because the
data is dev/test and discarding is accepted, the recommended path is to **reset
the Postgres volume** (`docker compose down -v`) for a clean slate rather than a
surgical purge.

## Impact on existing code & tests

Non-trivial — the current model leans on INVESTING transfers:

- `BalanceService` tests (`transferToInvestingCountsAsNetInvestmentOnly`, `investingWithdrawalReducesNetInvestment`, `comprehensiveScenarioMatchesAllFormulas`) and the `FinanceDashApplicationIT` worked example must be rewritten to the investment-event model.
- `TransactionService` validation: transfers to/from INVESTING become invalid — new tests, and the existing transfer tests adjusted.
- Docs: `DATA_MODEL.md` (metrics + new entity), `API.md` (new endpoints, transaction constraint), `ARCHITECTURE.md` (new files), `README.md`.

## Out of scope (later expansion)

Live price feeds / auto-valuation, share quantities & cost-basis/gain, realized-gain reporting on cash-outs, dividends, a value-history timeline, multi-currency.

## Open questions to confirm

1. **Net-worth time-base for holdings.** When a past `to` date is selected, the investing component of net worth is either (a) always current value — documented simplification, recommended — or (b) net cash invested as-of `to` as a historical proxy. Cash/flows stay as-of-`to` either way. Pick one (see [Net worth time-base](#net-worth-time-base-must-decide--see-below)).
2. **`netInvestment` card.** Redefine as net cash into investments over the period (Σ FUND − Σ CASH_OUT), or drop the card and rely on the Investments page? (Recommend: redefine — it stays a meaningful flow.)
3. **"Money invested" display.** Show both net cash invested and current value per holding + totals. (Recommend: yes — the user asked for both, and the data is free.)
4. **Source/destination asymmetry.** Buys fundable from CHECKING *or* SAVINGS, cash-outs always to SAVINGS — confirm this is intended.
5. **Duplicate symbol on add.** Merge into the existing holding, or allow multiple lots of the same symbol? (Recommend: merge, given the single-amount model.)
6. **Insufficient funds on a buy.** Allow (no block) in v1, or warn/block? (Recommend: allow.)
7. **`INVESTMENT_INCOME` category.** Keep it (dividends are income on a cash account) or remove it as part of "no investment category"? (Recommend: keep.)
8. **Migration.** Full dev-volume reset (recommended) vs surgical purge of INVESTING rows.
9. **Edit scope.** Editing changes symbol + value only (mark-to-market); investing *more* cash is done via Add. Confirm that's the intended split.
10. **Position change baseline.** Use net cash invested (Σ FUND − Σ CASH_OUT) as the baseline — matches your example and stays a pure derived value (recommended) — accepting that additional buys / partial cash-outs make the single % conflate realized and unrealized until proper cost-basis is added later. Confirm, or pin the baseline to the initial buy instead.
