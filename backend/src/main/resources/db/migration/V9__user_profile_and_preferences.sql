-- Migration V9: notification preferences on app_user (used by email notifications, later).
-- Deliberately no "opt out of the activity log" setting: staff can't switch off the audit trail.

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS notify_devotees BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS notify_donations BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS notify_security BOOLEAN NOT NULL DEFAULT true;
