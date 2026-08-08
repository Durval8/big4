-- Marks a transaction row as generated from an investments-service cash leg, holding that leg's
-- eventId. Null for every user-created row.
--
-- Nullable and additive: existing rows are all user-created, so NULL is the correct value for them.
-- The backfill in V4 populates it for the rows it inserts.

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS source_event_id VARCHAR(255);

-- Partial index: the only queries that touch this column filter for system rows (a small subset),
-- and BalanceService's spending filter needs to exclude them cheaply.
CREATE INDEX IF NOT EXISTS idx_transactions_source_event_id
    ON transactions (source_event_id)
    WHERE source_event_id IS NOT NULL;

-- One ledger row per cash leg. Also makes the consumer's insert safe under redelivery:
-- a duplicate eventId fails the constraint rather than silently double-writing.
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_source_event_id
    ON transactions (source_event_id)
    WHERE source_event_id IS NOT NULL;
