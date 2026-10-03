-- MVP (MandirCenter operations): the trust's public temple page (ADR 0017). Everything is
-- optional and empty until the temple fills it in: no placeholder data shown to the public.

CREATE TABLE temple_profile (
    tenant_id     BIGINT      PRIMARY KEY REFERENCES tenant(id),
    deity         TEXT        CHECK (length(deity) <= 120),
    address       TEXT        CHECK (length(address) <= 400),
    helpline      TEXT        CHECK (helpline ~ '^\+[1-9][0-9]{7,14}$'),
    timings       TEXT        CHECK (length(timings) <= 500),
    announcement  TEXT        CHECK (length(announcement) <= 1000),
    updated_by    BIGINT      NOT NULL REFERENCES app_user(id),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE temple_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE temple_profile FORCE  ROW LEVEL SECURITY;
CREATE POLICY temple_profile_tenant_isolation ON temple_profile
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevacenter_app') THEN
        GRANT SELECT, INSERT, UPDATE ON temple_profile TO sevacenter_app;
    END IF;
END $$;
