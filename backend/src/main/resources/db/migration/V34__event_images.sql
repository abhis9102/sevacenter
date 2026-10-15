-- A cover photo per event (utsav), shown on MandirCenter's Upcoming page. Same rules as the temple
-- photo (V29): only images the server re-encoded itself are stored. Goes with its event.

CREATE TABLE event_image (
    event_id     BIGINT      PRIMARY KEY REFERENCES event(id) ON DELETE CASCADE,
    tenant_id    BIGINT      NOT NULL REFERENCES tenant(id),
    data         BYTEA       NOT NULL CHECK (octet_length(data) <= 1048576),
    width        INT         NOT NULL CHECK (width BETWEEN 1 AND 1600),
    height       INT         NOT NULL CHECK (height BETWEEN 1 AND 1600),
    version      TEXT        NOT NULL CHECK (version ~ '^[0-9a-f]{16}$'),
    uploaded_by  BIGINT      NOT NULL REFERENCES app_user(id),
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE event_image ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_image FORCE  ROW LEVEL SECURITY;
CREATE POLICY event_image_tenant_isolation ON event_image
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevacenter_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON event_image TO sevacenter_app;
    END IF;
END $$;
