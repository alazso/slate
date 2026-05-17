CREATE TABLE IF NOT EXISTS open_operations (
    operation_id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(128) NULL,
    uuid UUID NOT NULL,
    crate_id VARCHAR(128) NOT NULL,
    key_type VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    prize_id VARCHAR(128) NULL,
    error_message VARCHAR(512) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_open_operations_request_uuid_crate
    ON open_operations(request_id, uuid, crate_id);

CREATE INDEX IF NOT EXISTS idx_open_operations_state_updated
    ON open_operations(state, updated_at);

CREATE TABLE IF NOT EXISTS open_operation_steps (
    id BIGSERIAL PRIMARY KEY,
    operation_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    message VARCHAR(512) NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_open_operation_steps_operation_created
    ON open_operation_steps(operation_id, created_at);
