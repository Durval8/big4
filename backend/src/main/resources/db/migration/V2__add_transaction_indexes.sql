-- Supports the filtered/sorted GET /api/transactions query shape added in the
-- pagination feature: WHERE transaction_date BETWEEN ... [AND account_type = ?]
-- [AND category = ?] ORDER BY <transaction_date|amount>, id.
CREATE INDEX IF NOT EXISTS idx_transactions_transaction_date ON transactions (transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_account_type ON transactions (account_type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions (category);
CREATE INDEX IF NOT EXISTS idx_transactions_amount ON transactions (amount);
