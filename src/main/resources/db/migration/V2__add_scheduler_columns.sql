-- V2: Add 4 columns required by v1.1 scheduler features.
-- H2 and PostgreSQL ADD COLUMN defaults to NULL; no explicit NULL keyword needed.
-- conflict_policy: ConflictPolicy enum (INTERRUPT default), firedAt: ISO timestamp for ONESHOT guard,
-- webhook_url: URL for Phase 10 webhooks, zone_id: display zone for Phase 11 multi-zone.
ALTER TABLE schedules ADD COLUMN conflict_policy VARCHAR(16) DEFAULT 'INTERRUPT';
ALTER TABLE schedules ADD COLUMN fired_at        VARCHAR(32);
ALTER TABLE schedules ADD COLUMN webhook_url     VARCHAR(512);
ALTER TABLE schedules ADD COLUMN zone_id         VARCHAR(64);
