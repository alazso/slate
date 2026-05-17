CREATE TABLE IF NOT EXISTS virtual_keys (
    uuid UUID NOT NULL,
    crate_id VARCHAR(128) NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS pity (
    uuid UUID NOT NULL,
    crate_id VARCHAR(128) NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS cooldowns (
    uuid UUID NOT NULL,
    crate_id VARCHAR(128) NOT NULL,
    last_open_at BIGINT NOT NULL,
    PRIMARY KEY (uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS open_log (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    crate_id VARCHAR(128) NOT NULL,
    prize_id VARCHAR(128) NOT NULL,
    prize_name VARCHAR(255) NOT NULL,
    key_type VARCHAR(32) NOT NULL,
    opened_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_open_log_uuid_crate ON open_log(uuid, crate_id);

CREATE TABLE IF NOT EXISTS pending_prizes (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    crate_id VARCHAR(128) NOT NULL,
    prize_id VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL
);
