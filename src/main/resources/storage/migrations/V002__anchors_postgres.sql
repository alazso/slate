CREATE TABLE IF NOT EXISTS crate_anchors (
    id BIGSERIAL PRIMARY KEY,
    crate_id VARCHAR(128) NOT NULL,
    world_key VARCHAR(128) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_crate_anchors_loc ON crate_anchors(world_key, x, y, z);
