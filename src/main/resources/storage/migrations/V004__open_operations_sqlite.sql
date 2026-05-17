CREATE TABLE IF NOT EXISTS open_operations (
    operation_id TEXT PRIMARY KEY,
    request_id TEXT NULL,
    uuid TEXT NOT NULL,
    crate_id TEXT NOT NULL,
    key_type TEXT NOT NULL,
    state TEXT NOT NULL,
    prize_id TEXT NULL,
    error_message TEXT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_open_operations_request_uuid_crate
    ON open_operations(request_id, uuid, crate_id);

CREATE INDEX IF NOT EXISTS idx_open_operations_state_updated
    ON open_operations(state, updated_at);

CREATE TABLE IF NOT EXISTS open_operation_steps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_id TEXT NOT NULL,
    state TEXT NOT NULL,
    message TEXT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_open_operation_steps_operation_created
    ON open_operation_steps(operation_id, created_at);
