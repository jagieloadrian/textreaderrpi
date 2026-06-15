-- V3: Add display_history table for Phase 9 audit log feature.
-- Stores the last 1000 displayed texts (pruned by HistoryRepository on insert).
-- displayed_at is VARCHAR(32) storing ISO-8601 Instant.now().toString() matching SchedulesTable convention.
CREATE TABLE IF NOT EXISTS display_history (
    id           VARCHAR(36)  NOT NULL,
    text         TEXT         NOT NULL,
    effect       VARCHAR(16)  NOT NULL,
    source       VARCHAR(16)  NOT NULL,
    schedule_id  VARCHAR(36)  NULL,
    zone_id      VARCHAR(64)  NULL,
    displayed_at VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id)
);
