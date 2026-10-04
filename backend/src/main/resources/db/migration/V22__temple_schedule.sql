-- MandirCenter look (ADR 0024): structured darshan hours, a same-day open/closed override, the
-- lunar calendar the temple follows, and the daily aarti timetable. All optional: nothing is shown
-- to the public until the temple enters it.

ALTER TABLE temple_profile
    ADD COLUMN morning_open    TIME,
    ADD COLUMN morning_close   TIME,
    ADD COLUMN evening_open    TIME,
    ADD COLUMN evening_close   TIME,
    -- "Closed today for grahan": applies only on override_on (IST), so it can never get stuck.
    ADD COLUMN status_override TEXT CHECK (status_override IN ('OPEN', 'CLOSED')),
    ADD COLUMN override_on     DATE,
    ADD COLUMN status_note     TEXT CHECK (length(status_note) <= 120),
    ADD COLUMN calendar        TEXT NOT NULL DEFAULT 'AMANTA' CHECK (calendar IN ('AMANTA', 'PURNIMANTA')),
    ADD CONSTRAINT temple_morning_hours CHECK ((morning_open IS NULL) = (morning_close IS NULL)
                                               AND (morning_open IS NULL OR morning_open < morning_close)),
    ADD CONSTRAINT temple_evening_hours CHECK ((evening_open IS NULL) = (evening_close IS NULL)
                                               AND (evening_open IS NULL OR evening_open < evening_close)),
    ADD CONSTRAINT temple_sessions_in_order CHECK (morning_close IS NULL OR evening_open IS NULL
                                                   OR morning_close <= evening_open),
    ADD CONSTRAINT temple_override_dated CHECK ((status_override IS NULL) = (override_on IS NULL));

CREATE TABLE temple_aarti (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenant(id),
    name        TEXT   NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
    at_time     TIME   NOT NULL,
    description TEXT   CHECK (length(description) <= 200)
);
CREATE INDEX temple_aarti_tenant ON temple_aarti (tenant_id, at_time);

ALTER TABLE temple_aarti ENABLE ROW LEVEL SECURITY;
ALTER TABLE temple_aarti FORCE  ROW LEVEL SECURITY;
CREATE POLICY temple_aarti_tenant_isolation ON temple_aarti
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevacenter_app') THEN
        -- The timetable is replaced as a whole on save: no UPDATE needed.
        GRANT SELECT, INSERT, DELETE ON temple_aarti TO sevacenter_app;
    END IF;
END $$;
