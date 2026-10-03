-- Deleting staff. A PENDING invitation is hard-deleted; anyone who could have acted is
-- tombstoned instead: login identity removed (email freed, no password, DISABLED), display name
-- kept so records they made (devotee edits, consent, donations) still say who made them.
ALTER TABLE app_user ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE app_user ADD CONSTRAINT app_user_deleted_is_disabled
    CHECK (deleted_at IS NULL OR (status = 'DISABLED' AND password_hash IS NULL));
