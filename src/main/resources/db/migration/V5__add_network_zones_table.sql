CREATE TABLE IF NOT EXISTS network_zones (
    id               VARCHAR(64)  NOT NULL,
    "name"           VARCHAR(64)  NOT NULL,
    ip               VARCHAR(64)  NOT NULL,
    "type"           VARCHAR(16)  NOT NULL DEFAULT 'MAX7219',
    discovery_method VARCHAR(8)   NOT NULL,
    created_at       VARCHAR(32)  NOT NULL,
    last_seen_at     VARCHAR(32),
    PRIMARY KEY (id)
);
