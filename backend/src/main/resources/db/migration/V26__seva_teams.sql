-- Sevak Hub (ADR 0028): the temple's seva teams (volunteer activities) with their shifts, staff
-- registering volunteers, and assigning them to a team. Deleting a team or removing a volunteer
-- hides it everywhere and keeps the row for the audit trail.

CREATE TABLE seva_team (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT      NOT NULL REFERENCES tenant(id),
    name         TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
    description  TEXT        CHECK (length(description) <= 300),
    target_count INT         CHECK (target_count BETWEEN 1 AND 1000),
    deleted_at   TIMESTAMPTZ,
    updated_by   BIGINT      NOT NULL REFERENCES app_user(id),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_seva_team_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_seva_team_name ON seva_team (tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- A team's shifts are replaced as a whole when the team is saved.
CREATE TABLE seva_shift (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    team_id   BIGINT NOT NULL,
    name      TEXT   NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
    starts_at TIME   NOT NULL,
    ends_at   TIME   NOT NULL CHECK (ends_at <> starts_at),   -- may run past midnight
    CONSTRAINT fk_seva_shift_team FOREIGN KEY (tenant_id, team_id) REFERENCES seva_team(tenant_id, id)
);
CREATE INDEX idx_seva_shift_team ON seva_shift (team_id, starts_at);

ALTER TABLE sevak_signup
    ADD COLUMN team_id       BIGINT,
    ADD COLUMN duty          TEXT CHECK (length(duty) <= 120),
    ADD COLUMN registered_by BIGINT REFERENCES app_user(id),
    ADD COLUMN removed_at    TIMESTAMPTZ,
    ADD CONSTRAINT fk_sevak_signup_team FOREIGN KEY (tenant_id, team_id) REFERENCES seva_team(tenant_id, id);
CREATE INDEX idx_sevak_signup_team ON sevak_signup (team_id) WHERE removed_at IS NULL;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['seva_team', 'seva_shift'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON %I '
            'USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::bigint) '
            'WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::bigint)',
            t || '_tenant_isolation', t);
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevacenter_app') THEN
        GRANT SELECT, INSERT, UPDATE ON seva_team TO sevacenter_app;          -- delete = deleted_at
        GRANT SELECT, INSERT, DELETE ON seva_shift TO sevacenter_app;         -- replaced on save
    END IF;
END $$;
