-- V1: Initial schedules table schema (10 columns).
-- NOTE: This file is NOT run on existing Pi installs (baselineOnMigrate skips V1).
--       It IS run on fresh installs before V2, then V2 adds the 4 new columns.
-- Source of truth: SchedulesTable.kt
CREATE TABLE IF NOT EXISTS schedules (
    id            VARCHAR(36)  NOT NULL,
    text          TEXT         NOT NULL,
    trigger_type  VARCHAR(16)  NOT NULL,
    trigger_value VARCHAR(256) NOT NULL,
    effect        VARCHAR(16)  NOT NULL DEFAULT 'SCROLL',
    priority      INT          NOT NULL DEFAULT 0,
    max_runs      INT          NULL,
    expires_at    VARCHAR(32)  NULL,
    created_at    VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id)
);
