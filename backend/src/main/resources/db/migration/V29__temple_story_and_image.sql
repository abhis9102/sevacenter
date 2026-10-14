-- MandirCenter: the temple's photo and its About / History sections. Plain text only (rendered as
-- text, never HTML), length-capped like the other temple fields.

ALTER TABLE temple_profile
    ADD COLUMN about   TEXT CHECK (length(about) <= 4000),
    ADD COLUMN history TEXT CHECK (length(history) <= 8000);

-- One photo per temple, kept off temple_profile so the page data stays small. Only images the
-- server re-encoded itself are stored (TempleImageProcessor): JPEG, bounded size, no metadata.
CREATE TABLE temple_image (
    tenant_id    BIGINT      PRIMARY KEY REFERENCES tenant(id),
    data         BYTEA       NOT NULL CHECK (octet_length(data) <= 1048576),
    content_type TEXT        NOT NULL CHECK (content_type = 'image/jpeg'),
    width        INT         NOT NULL CHECK (width BETWEEN 1 AND 1600),
    height       INT         NOT NULL CHECK (height BETWEEN 1 AND 1600),
    version      TEXT        NOT NULL CHECK (version ~ '^[0-9a-f]{16}$'),
    updated_by   BIGINT      NOT NULL REFERENCES app_user(id),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE temple_image ENABLE ROW LEVEL SECURITY;
ALTER TABLE temple_image FORCE  ROW LEVEL SECURITY;
CREATE POLICY temple_image_tenant_isolation ON temple_image
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevacenter_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON temple_image TO sevacenter_app;
    END IF;
END $$;
