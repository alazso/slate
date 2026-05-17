CREATE TABLE IF NOT EXISTS crate_anchors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    crate_id TEXT NOT NULL,
    world_key TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_crate_anchors_loc ON crate_anchors(world_key, x, y, z);
