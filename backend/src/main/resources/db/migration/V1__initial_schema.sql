-- Initial schema for the finance-dash backend.
-- Uses CREATE TABLE IF NOT EXISTS so this migration is safe to apply against the
-- existing production database (tables already exist from the ddl-auto=update era)
-- as well as fresh databases (test containers, new deployments).
-- Flyway records this as applied either way; subsequent migrations run normally.

CREATE TABLE IF NOT EXISTS transactions (
    id               BIGSERIAL       PRIMARY KEY,
    description      VARCHAR(255)    NOT NULL,
    amount           NUMERIC(12, 2)  NOT NULL,
    transaction_date DATE            NOT NULL,
    account_type     VARCHAR(20)     NOT NULL,
    linked_account_type VARCHAR(20),
    category         VARCHAR(30),
    transaction_type VARCHAR(20)     NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL,
    updated_at       TIMESTAMPTZ     NOT NULL
);

CREATE TABLE IF NOT EXISTS budgets (
    id         BIGSERIAL       PRIMARY KEY,
    name       VARCHAR(255)    NOT NULL,
    value      NUMERIC(12, 2)  NOT NULL,
    created_at TIMESTAMPTZ     NOT NULL,
    updated_at TIMESTAMPTZ     NOT NULL
);

-- @ElementCollection join table for Budget.categories
CREATE TABLE IF NOT EXISTS budget_categories (
    budget_id BIGINT       NOT NULL REFERENCES budgets (id) ON DELETE CASCADE,
    category  VARCHAR(30)  NOT NULL
);

-- Idempotent cash-leg commands from the investments service (PK = eventId for exactly-once delivery)
CREATE TABLE IF NOT EXISTS investment_cash_flow (
    event_id     VARCHAR(255)    PRIMARY KEY,
    type         VARCHAR(20),
    amount       NUMERIC(12, 2)  NOT NULL,
    account_type VARCHAR(20),
    flow_date    DATE
);

-- Singleton row: last-write-wins value snapshot from the investments service
CREATE TABLE IF NOT EXISTS investment_valuation (
    id        VARCHAR(255)    PRIMARY KEY,
    net_value NUMERIC(12, 2),
    as_of     TIMESTAMPTZ
);
