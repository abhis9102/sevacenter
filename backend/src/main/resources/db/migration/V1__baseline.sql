-- V1 baseline (M0).
-- Proves the Flyway migration pipeline runs end to end. Real domain tables
-- (tenants, users, roles, devotees, donations, events) arrive from M1 onward,
-- each as its own versioned, forward-only migration.

CREATE TABLE app_info (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO app_info (key, value) VALUES ('schema_baseline', 'M0');
