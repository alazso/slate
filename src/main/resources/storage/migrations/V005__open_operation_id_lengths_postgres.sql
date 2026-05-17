ALTER TABLE open_operations
    ALTER COLUMN operation_id TYPE VARCHAR(255),
    ALTER COLUMN request_id TYPE VARCHAR(255);

ALTER TABLE open_operation_steps
    ALTER COLUMN operation_id TYPE VARCHAR(255);
