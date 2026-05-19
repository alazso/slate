CREATE INDEX IF NOT EXISTS idx_open_log_crate_opened_at ON open_log(crate_id, opened_at DESC);
CREATE INDEX IF NOT EXISTS idx_open_log_uuid_opened_at ON open_log(uuid, opened_at DESC);
