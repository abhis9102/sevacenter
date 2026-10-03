-- Migration V9: User profile preferences and privacy settings

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS notify_devotees BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS notify_donations BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS notify_security BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS privacy_activity_log BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS privacy_show_in_staff_directory BOOLEAN NOT NULL DEFAULT true;
