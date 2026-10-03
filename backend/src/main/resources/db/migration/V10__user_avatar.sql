-- Migration V10: User avatar image storage

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS avatar_data BYTEA,
    ADD COLUMN IF NOT EXISTS avatar_content_type TEXT;
