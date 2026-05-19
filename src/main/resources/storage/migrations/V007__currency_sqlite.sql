CREATE TABLE IF NOT EXISTS slate_currencies (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    symbol TEXT NULL,
    decimals INTEGER NOT NULL DEFAULT 0,
    default_balance TEXT NOT NULL DEFAULT '0',
    min_balance TEXT NOT NULL DEFAULT '0',
    max_balance TEXT NULL,
    transferable BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS slate_currency_balances (
    account_type TEXT NOT NULL,
    account_id TEXT NOT NULL,
    currency_id TEXT NOT NULL,
    balance TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY(account_type, account_id, currency_id)
);

CREATE INDEX IF NOT EXISTS idx_currency_balances_currency
    ON slate_currency_balances(currency_id);

CREATE TABLE IF NOT EXISTS slate_currency_transactions (
    id TEXT PRIMARY KEY,
    operation_id TEXT NULL,
    transaction_key TEXT NULL,
    account_type TEXT NOT NULL,
    account_id TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    currency_id TEXT NOT NULL,
    type TEXT NOT NULL,
    amount TEXT NOT NULL,
    before_balance TEXT NULL,
    after_balance TEXT NULL,
    status TEXT NOT NULL,
    reason TEXT NOT NULL,
    metadata_json TEXT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_currency_tx_account
    ON slate_currency_transactions(account_type, account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_currency_tx_operation
    ON slate_currency_transactions(operation_id);

CREATE INDEX IF NOT EXISTS idx_currency_tx_provider_currency
    ON slate_currency_transactions(provider_id, currency_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_currency_tx_unique_op_key
    ON slate_currency_transactions(operation_id, transaction_key);
