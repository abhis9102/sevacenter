-- Staff delete an event or a puja (ADR 0029). Deleting sets deleted_at: the row stays for the
-- audit trail, and past passes / bookings keep pointing at it. The app role still has no DELETE.

ALTER TABLE event
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by BIGINT REFERENCES app_user(id),
    ADD CONSTRAINT event_deleted_shape CHECK ((deleted_at IS NULL) = (deleted_by IS NULL)),
    -- Only a draft or a cancelled event can be deleted: a live one is cancelled first, so its
    -- devotees see "cancelled" rather than a pass that silently stops working.
    ADD CONSTRAINT event_deleted_not_live CHECK (deleted_at IS NULL OR status <> 'PUBLISHED');

ALTER TABLE puja
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by BIGINT REFERENCES app_user(id),
    ADD CONSTRAINT puja_deleted_shape CHECK ((deleted_at IS NULL) = (deleted_by IS NULL));
