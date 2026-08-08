# Investment cash legs in the transaction ledger — design

**Status: Approved design, implementation in progress on `fix/investment-cash-leg-ledger`.**
Agreed 2026-08-02.

## Why

Buying an investment debits a cash account, but **nothing appears in the transaction ledger**. The
user sees their checking balance drop with no corresponding row explaining it.

**Important reframe: the money is not unaccounted for.** `BalanceService.cashBalance()` subtracts
`FUND` cash-flow rows directly (lines 105–111), so balances, `netWorth` and `netInvestment` are all
already correct. Net worth is conserved on a buy — cash out, holdings value in. What's missing is
purely *ledger visibility*: `investment_cash_flow` is a standalone projection with no link to
`transactions`, and the buy sequence diagram in `SYSTEM_DESIGN.md` correctly shows no `transactions`
write. This is working as designed; the design is what's wrong.

So this change is not "start accounting for the money" — it's "move the cash movement out of a
side projection and into the ledger, where the user can see it."

## The trap: naively adding a row double-counts

`cashBalance()` subtracts FUND flows *unconditionally*, independent of the ledger. Any transaction
row created for the same event stacks on top:

| Row shape | Cash balance | `spending` | `netSpending` | `netInvestment` |
|---|---|---|---|---|
| `EXPENSE` on CHECKING | double-debited | **inflated** | **inflated** | — |
| `TRANSFER` CHECKING→SAVINGS | double-debited **and** wrongly credits SAVINGS | **inflated** | — | — |
| `ADJUSTMENT` on CHECKING | net-cancels the FUND debit | — | — | — |

None of the three legal shapes works, because `INVESTING` is currently rejected as both
`accountType` and `linkedAccountType`. That rejection is the whole reason the projection exists.

## Approach

**The transaction becomes the single source of truth for the cash movement; the flow projection
keeps serving `netInvestment` only.**

- **FUND** → `TRANSFER` CHECKING (or SAVINGS) **→ INVESTING**
- **CASH_OUT** → `TRANSFER` **INVESTING** → SAVINGS
- `BalanceService.cashBalance()` **drops both cash-flow terms** — the transaction now carries them.
- A new nullable `Transaction.sourceEventId` holds the cash-leg `eventId`, marking the row as
  system-generated.

### Why this shape composes correctly with all five metrics

Verified against `BalanceService` line by line:

- **Cash balance.** `cashBalance()` is only ever called for `CHECKING` and `SAVINGS` (lines 53–54).
  A `TRANSFER` CHECKING→INVESTING therefore debits CHECKING via line 96–98 and *never* credits
  INVESTING, because the loop never runs for INVESTING. Exactly the desired effect, with no special
  casing.
- **`netWorth`.** `investing` still comes from the valuation snapshot, so net worth stays conserved.
- **`spending`.** The predicate is `TRANSFER && linkedAccountType == SAVINGS` (lines 62–64).
  FUND rows link to INVESTING, so they don't match — good. **But CASH_OUT rows link to SAVINGS and
  *would* match**, wrongly inflating `spending`. This is the sharp edge: `transfersToSavings` must
  additionally require `sourceEventId == null`.
- **`netSpending`.** `EXPENSE` only. Unaffected.
- **`netInvestment`.** Cash flows only. Unaffected — which is why the projection stays.

### `INVESTING` on a transaction — not a relaxation of the documented rule

CLAUDE.md says `INVESTING` is not a legal value on a `Transaction` and that transaction validation
must not be relaxed. **It isn't.** `TransactionService.validate()` runs only from `create()` and
`update()`; the consumer writes through `TransactionRepository.save()` directly and never touches
it. Every user-facing API path still rejects `INVESTING` exactly as before. What the rule protects —
users cannot post to INVESTING — is fully preserved.

The invariant it *does* change is the weaker "no row in `transactions` ever mentions INVESTING",
which was an implementation consequence of the projection design, not a stated rule.

## Backfill is a prerequisite, not a follow-up

**This is the part that breaks production if skipped.** The moment the flow terms leave
`cashBalance()`, every existing `investment_cash_flow` row stops debiting anything. Every user's
checking balance silently jumps up by their total historical investment funding, and net worth
double-counts (cash restored *and* still counted in the valuation).

So the backfill migration ships in the **same change** as the `BalanceService` edit. It inserts one
`TRANSFER` row per existing cash-flow row, with `source_event_id` populated (without which
historical CASH_OUT rows would retroactively inflate `spending`).

**Acceptance test for the migration is behavioural, not structural:** balances computed after the
migration must equal balances computed before it. That's a stronger check than any unit test here.

## Contract change: `stockSymbol`

`CashLegCommand` currently carries no symbol, so a generated row could only say "Investment
funding". Adding `stockSymbol` lets the ledger read `Bought AAPL` / `Cashed out AAPL`.

Both sides change (`investments-service` owns the contract; the backend keeps a mirror record),
plus `InvestmentMessageContractTest`. **Null-safe on the consumer**: messages already queued when
the new build deploys won't have the field, and must fall back to the generic description rather
than NPE. `schemaVersion` stays at 1 — this is an additive, backward-compatible field.

## System-generated rows must be locked

Once the ledger row is the source of truth for cash, editing or deleting it silently changes the
balance and desyncs from the investments-service, which still holds the position.

`TransactionService.update()` and `delete()` reject any row with a non-null `sourceEventId`, via
`InvalidTransactionException` → 400, mapped in `GlobalExceptionHandler` like every other rule. The
frontend hides the row-action buttons for those rows (the API rejection is the real guard; hiding
the buttons is so users don't hit an error they can't act on).

## Consumer becomes transactional

`InvestmentCashLegConsumer` now writes **two** rows. Without a transaction, a failure between them
strands the message permanently: the existing idempotency guard is `cashFlowRepository.existsById`,
so on redelivery it would skip, leaving the ledger row missing forever.

Two changes:
- `@Transactional` on the handler so both rows commit atomically. **This is a deliberate deviation
  from the "no service is `@Transactional`" convention** — that rule exists because services aren't
  transactional and `open-in-view` is off, so lazy collections would fail during response mapping. A
  message consumer maps no response and has a genuine two-write atomicity requirement. Documented
  here so nobody "fixes" it later.
- The idempotency guard stays keyed on `eventId`, but atomicity means either both rows exist or
  neither does, so the check is no longer ambiguous about which write to test.

## Changes

### `investments-service`
- `contract/CashLegCommand` — add `stockSymbol`; update the `of(...)` factory.
- `HoldingService` — pass the symbol at both emit sites (buy line ~127, cash-out line ~181).

### Backend
- `messaging/CashLegCommand` — mirror the new field.
- `messaging/InvestmentCashLegConsumer` — `@Transactional`; write the `Transaction` alongside the
  `InvestmentCashFlow`; null-safe description.
- `domain/Transaction` — nullable `sourceEventId`.
- `dto/TransactionResponse` — expose `sourceEventId` so the frontend can lock the row.
- `service/TransactionService` — reject `update`/`delete` on system rows.
- `service/BalanceService` — drop both flow terms from `cashBalance()`; exclude system rows from
  `transfersToSavings`.
- `db/migration/V3__add_transaction_source_event_id.sql` — nullable column + index.
- `db/migration/V4__backfill_investment_transactions.sql` — the backfill.

### Frontend
- `types/transaction.ts` — `sourceEventId: string | null`.
- Transactions table — render the `INVESTING` label (currently only CHECKING/SAVINGS are mapped),
  hide edit/delete on locked rows.

### Docs
- `DATA_MODEL.md` — the balance formula block (lines ~56–66) loses its two cash-flow terms.
- `SYSTEM_DESIGN.md` — the buy sequence diagram gains the `transactions` write.
- `API.md` — `sourceEventId` on `TransactionResponse`; the 400 on editing a system row.

## Testing

- `BalanceServiceTest.comprehensiveScenarioMatchesAllFormulas` **pins the current arithmetic** and
  must change: it currently expects checking 2900 with a FUND 400 subtracted via the flow. The same
  totals should hold with the FUND expressed as a transaction instead — which makes it a good
  equivalence check rather than just a rewrite.
- New: cash-out transactions excluded from `spending`; system rows rejected on update/delete;
  consumer writes both rows; consumer is null-safe on a symbol-less message; contract test covers
  the new field.
- `AnalyticsIT`-style end-to-end: the backfill acceptance check (balances unchanged across the
  migration) belongs in an IT against Testcontainers, since it's a migration behaviour.

## Follow-up, explicitly not now

`investment_cash_flow` becomes redundant once the ledger carries the same information —
`netInvestment` could be derived from `sourceEventId IS NOT NULL` transactions and their direction.
Removing it would touch the documented projection architecture, the message contract's purpose, and
`DATA_MODEL.md`'s two-projection framing. Not worth bundling into a fix. Both rows are written by
one handler inside one transaction, so they cannot drift in the meantime.

## Revision history

- 2026-08-02 — initial design, after tracing `BalanceService` arithmetic to establish that the cash
  is already correctly accounted for and only ledger visibility is missing. Ledger shape
  (TRANSFER to/from INVESTING) and the `stockSymbol` contract extension confirmed with the user.
  Spec review added: backfill reclassified from follow-up to prerequisite; edit/delete locking;
  the frontend's unmapped-`INVESTING` rendering gap.
