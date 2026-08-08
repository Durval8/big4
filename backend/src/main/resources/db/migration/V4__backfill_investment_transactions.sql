-- Backfills a ledger row for every investment cash leg recorded before this release.
--
-- WHY THIS IS NOT OPTIONAL, AND WHY IT SHIPS WITH THE BalanceService CHANGE:
-- Until now, BalanceService.cashBalance() debited FUND flows and credited CASH_OUT flows directly
-- from investment_cash_flow, and no transactions row existed for them. This release moves that cash
-- movement into the ledger and removes those two terms from the balance calculation. Without this
-- backfill, every historical cash leg would stop affecting balances the moment the new code
-- deploys: checking would jump up by the user's total historical investment funding, and net worth
-- would double-count (cash restored while the valuation snapshot still counts the holdings).
--
-- Acceptance is behavioural, not structural: balances computed after this migration must equal
-- balances computed before it. BalanceMigrationBackfillIT asserts exactly that.
--
-- Shape mirrors what InvestmentCashLegConsumer now writes for a live message:
--   FUND     -> TRANSFER  <source account> -> INVESTING   (debits the source account)
--   CASH_OUT -> TRANSFER  INVESTING        -> SAVINGS     (credits savings)
-- category stays NULL (TRANSFER forbids it) and source_event_id carries the leg's event_id, which
-- both locks the row against editing and keeps cash-outs out of the `spending` metric.
--
-- Idempotent via NOT EXISTS so a re-run (or a partially applied migration) cannot duplicate rows.

INSERT INTO transactions (
    description, amount, transaction_date, account_type, linked_account_type,
    category, transaction_type, source_event_id, created_at, updated_at
)
SELECT
    CASE f.type
        WHEN 'FUND'     THEN 'Investment funding'
        WHEN 'CASH_OUT' THEN 'Investment cash-out'
    END,
    f.amount,
    f.flow_date,
    CASE f.type
        WHEN 'FUND'     THEN f.account_type   -- CHECKING or SAVINGS, whichever funded the buy
        WHEN 'CASH_OUT' THEN 'INVESTING'
    END,
    CASE f.type
        WHEN 'FUND'     THEN 'INVESTING'
        WHEN 'CASH_OUT' THEN 'SAVINGS'        -- cash-outs always land in savings
    END,
    NULL,
    'TRANSFER',
    f.event_id,
    now(),
    now()
FROM investment_cash_flow f
WHERE f.type IN ('FUND', 'CASH_OUT')
  AND f.account_type IS NOT NULL
  AND f.flow_date IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM transactions t WHERE t.source_event_id = f.event_id
  );
