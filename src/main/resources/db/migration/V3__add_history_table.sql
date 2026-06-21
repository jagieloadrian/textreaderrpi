CREATE TABLE IF NOT EXISTS display_history (
    id           VARCHAR(36)  NOT NULL,
    text         TEXT         NOT NULL,
    effect       VARCHAR(16)  NOT NULL,
    "source"     VARCHAR(16)  NOT NULL,
    schedule_id  VARCHAR(36)  NULL,
    zone_id      VARCHAR(64)  NULL,
    displayed_at VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id)
);
