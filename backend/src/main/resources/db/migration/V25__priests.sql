-- The temple's priests (pujaris) and who performs each sankalp (ADR 0027). Priests are deactivated,
-- never deleted, so past bookings keep the name of the priest who performed them.

CREATE TABLE priest (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL REFERENCES tenant(id),
    name        TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
    phone       TEXT        CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    specialties TEXT        CHECK (length(specialties) <= 200),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_by  BIGINT      NOT NULL REFERENCES app_user(id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_priest_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_priest_name ON priest (tenant_id, lower(name));

-- Composite key: the database itself refuses a priest from another trust.
ALTER TABLE puja_booking ADD COLUMN priest_id BIGINT,
    ADD CONSTRAINT fk_puja_booking_priest FOREIGN KEY (tenant_id, priest_id) REFERENCES priest(tenant_id, id);

ALTER TABLE priest ENABLE ROW LEVEL SECURITY;
ALTER TABLE priest FORCE  ROW LEVEL SECURITY;
CREATE POLICY priest_tenant_isolation ON priest
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevacenter_app') THEN
        GRANT SELECT, INSERT, UPDATE ON priest TO sevacenter_app;  -- no DELETE: deactivate instead
    END IF;
END $$;
