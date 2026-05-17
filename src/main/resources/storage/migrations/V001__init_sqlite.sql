CREATE TABLE IF NOT EXISTS virtual_keys (
    uuid TEXT NOT NULL,
    crate_id TEXT NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS pity (
    uuid TEXT NOT NULL,
    crate_id TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS cooldowns (
    uuid TEXT NOT NULL,
    crate_id TEXT NOT NULL,
    last_open_at BIGINT NOT NULL,
    PRIMARY KEY (uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS open_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL,
    crate_id TEXT NOT NULL,
    prize_id TEXT NOT NULL,
    prize_name TEXT NOT NULL,
    key_type TEXT NOT NULL,
    opened_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_open_log_uuid_crate ON open_log(uuid, crate_id);

CREATE TABLE IF NOT EXISTS pending_prizes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL,
    crate_id TEXT NOT NULL,
    prize_id TEXT NOT NULL,
    created_at BIGINT NOT NULL
);
